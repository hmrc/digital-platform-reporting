/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package services

import connectors.*
import models.assumed.AssumingPlatformOperator
import models.audit.AddSubmissionEvent
import models.audit.AddSubmissionEvent.DeliveryRoute.{Dct52A, Dprs0502}
import models.operator.TinType.{Utr, Vrn}
import models.operator.responses.PlatformOperator
import models.operator.{AddressDetails, ContactDetails, TinDetails}
import models.submission.Submission
import models.submission.Submission.State.{Ready, Submitted, Validated}
import models.submission.Submission.SubmissionType
import models.subscription.responses.SubscriptionInfo
import models.subscription.{Individual, IndividualContact, Organisation, OrganisationContact}
import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.xml.sax.ErrorHandler
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repository.SubmissionRepository
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import utils.DateTimeFormats

import java.nio.file.Paths
import java.time.{Clock, Instant, Year, ZoneOffset}
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import scala.concurrent.Future
import scala.xml.{Document, SAXParseException, XML}

class SubmissionServiceSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfterEach
    with ScalaFutures
    with OptionValues
    with IntegrationPatience {

  private lazy val submissionService: SubmissionService = app.injector.instanceOf[SubmissionService]
  private val now = Instant.now()
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val mockSubmissionConnector: SubmissionConnector = mock[SubmissionConnector]
  private val mockSubscriptionConnector: SubscriptionConnector = mock[SubscriptionConnector]
  private val mockDownloadConnector: DownloadConnector = mock[DownloadConnector]
  private val mockSdesService: SdesService = mock[SdesService]
  private val mockAssumedReportingService: AssumedReportingService = mock[AssumedReportingService]
  private val mockPlatformOperatorConnector: PlatformOperatorConnector = mock[PlatformOperatorConnector]
  private val mockSubmissionRepository: SubmissionRepository = mock[SubmissionRepository]
  private val mockUuidService: UuidService = mock[UuidService]
  private val mockAuditService: AuditService = mock[AuditService]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "sdes.size-threshold" -> 3_000_000L
      )
      .overrides(
        bind[SubmissionConnector].toInstance(mockSubmissionConnector),
        bind[SubscriptionConnector].toInstance(mockSubscriptionConnector),
        bind[DownloadConnector].toInstance(mockDownloadConnector),
        bind[SdesService].toInstance(mockSdesService),
        bind[Clock].toInstance(clock),
        bind[AssumedReportingService].toInstance(mockAssumedReportingService),
        bind[PlatformOperatorConnector].toInstance(mockPlatformOperatorConnector),
        bind[SubmissionRepository].toInstance(mockSubmissionRepository),
        bind[UuidService].toInstance(mockUuidService),
        bind[AuditService].toInstance(mockAuditService)
      )
      .build()

  given Materializer = app.injector.instanceOf[Materializer]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      mockSubmissionConnector,
      mockSubscriptionConnector,
      mockDownloadConnector,
      mockSdesService,
      mockAssumedReportingService,
      mockPlatformOperatorConnector,
      mockSubmissionRepository,
      mockUuidService,
      mockAuditService
    )
  }

  "submit" - {

    val hc = HeaderCarrier()
    val dprsId = "dprsId"
    val fileName = "test.xml"
    val submissionId = "submissionId"
    val subscriptionId = "subscriptionId"

    "when the submission is in a validated state" - {

      "when the submission is less than the SDES submission threshold" - {

        "must add the relevant XML envelope and submit" - {

          "when the XML has no XML declaration" - {

            "when all optional fields are included" in {

              val submission = Submission(
                _id = submissionId,
                submissionType = SubmissionType.Xml,
                dprsId = dprsId,
                operatorId = "operatorId",
                operatorName = "operatorName",
                assumingOperatorName = None,
                state = Validated(
                  downloadUrl = url"http://example.com",
                  reportingPeriod = Year.of(2024),
                  fileName = fileName,
                  checksum = "checksum",
                  size = 3_000_000L
                ),
                created = now,
                updated = now
              )

              val individualContact = IndividualContact(Individual("first", "last"), "individual email", Some("0777777"))
              val organisationContact = OrganisationContact(Organisation("org name"), "org email", Some("0787777"))
              val subscription = SubscriptionInfo(
                id = subscriptionId,
                gbUser = true,
                tradingName = Some("tradingName"),
                primaryContact = individualContact,
                secondaryContact = Some(organisationContact)
              )

              val innerContent = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/SubmissionSampleNoXmlDeclaration.xml")).mkString
              val fileSource = Source.single(ByteString.fromString(innerContent))

              val expectedAudit = AddSubmissionEvent(
                conversationId = submissionId,
                dprsId = dprsId,
                operatorId = "operatorId",
                operatorName = "operatorName",
                reportingPeriod = Year.of(2024),
                fileName = fileName,
                fileSize = 3_000_000L,
                deliveryRoute = Dprs0502,
                processedAt = now,
                isSent = true
              )

              when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
              when(mockDownloadConnector.download(any())).thenReturn(Future.successful(fileSource))
              when(mockSubmissionConnector.submit(any(), any())(using any())).thenReturn(Future.successful(Done))

              submissionService.submit(submission)(using hc).futureValue

              val requestBodyCaptor: ArgumentCaptor[Source[ByteString, ?]] =
                ArgumentCaptor.forClass(classOf[Source[ByteString, ?]])

              verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
              verify(mockSubmissionConnector).submit(eqTo(submissionId), requestBodyCaptor.capture())(using any())
              verify(mockSdesService, never()).enqueueSubmission(any(), any(), any(), any())
              verify(mockAuditService).audit(eqTo(expectedAudit))(using any(), any())

              val result = requestBodyCaptor.getValue.runWith(Sink.fold(ByteString.empty)(_ ++ _)).futureValue
              val document = validate(result)

              document.docElem.label mustEqual "DPISubmissionRequest"

              (document \ "requestCommon" \ "receiptDate").text mustEqual DateTimeFormats.ISO8601Formatter.format(now)
              (document \ "requestCommon" \ "conversationID").text mustEqual submissionId
              (document \ "requestCommon" \ "schemaVersion").text mustEqual "1.0.0"

              (document \ "requestAdditionalDetail" \ "fileName").text mustEqual "test"
              (document \ "requestAdditionalDetail" \ "subscriptionID").text mustEqual subscription.id
              (document \ "requestAdditionalDetail" \ "tradingName").text mustEqual subscription.tradingName.value
              (document \ "requestAdditionalDetail" \ "isManual").text mustEqual "false"
              (document \ "requestAdditionalDetail" \ "isGBUser").text mustEqual subscription.gbUser.toString

              (document \ "requestAdditionalDetail" \ "primaryContact" \ "phoneNumber").text mustEqual individualContact.phone.value
              (document \ "requestAdditionalDetail" \ "primaryContact" \ "emailAddress").text mustEqual individualContact.email
              (document \ "requestAdditionalDetail" \ "primaryContact" \ "individualDetails" \ "firstName").text mustEqual individualContact.individual.firstName
              (document \ "requestAdditionalDetail" \ "primaryContact" \ "individualDetails" \ "lastName").text mustEqual individualContact.individual.lastName

              (document \ "requestAdditionalDetail" \ "secondaryContact" \ "phoneNumber").text mustEqual organisationContact.phone.value
              (document \ "requestAdditionalDetail" \ "secondaryContact" \ "emailAddress").text mustEqual organisationContact.email
              (document \ "requestAdditionalDetail" \ "secondaryContact" \ "organisationDetails" \ "organisationName").text mustEqual organisationContact.organisation.name

              val inner = scala.xml.XML.loadString(innerContent)
              (document \ "requestDetail" \ "_").last mustEqual inner

              result.utf8String must include("<dpi:PlatformBusinessName>a&apos;&apos;</dpi:PlatformBusinessName>")
            }

            "when minimal data is included" in {

              val submission = Submission(
                _id = submissionId,
                submissionType = SubmissionType.Xml,
                dprsId = dprsId,
                operatorId = "operatorId",
                operatorName = "operatorName",
                assumingOperatorName = None,
                state = Validated(
                  downloadUrl = url"http://example.com",
                  reportingPeriod = Year.of(2024),
                  fileName = fileName,
                  checksum = "checksum",
                  size = 1337
                ),
                created = now,
                updated = now
              )

              val organisationContact = OrganisationContact(Organisation("org name"), "org email", None)
              val subscription = SubscriptionInfo(
                id = subscriptionId,
                gbUser = false,
                tradingName = None,
                primaryContact = organisationContact,
                secondaryContact = None
              )

              val innerContent = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/SubmissionSampleNoXmlDeclaration.xml")).mkString
              val fileSource = Source.single(ByteString.fromString(innerContent))

              val expectedAudit = AddSubmissionEvent(
                conversationId = submissionId,
                dprsId = dprsId,
                operatorId = "operatorId",
                operatorName = "operatorName",
                reportingPeriod = Year.of(2024),
                fileName = fileName,
                fileSize = 1337L,
                deliveryRoute = Dprs0502,
                processedAt = now,
                isSent = true
              )

              when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
              when(mockDownloadConnector.download(any())).thenReturn(Future.successful(fileSource))
              when(mockSubmissionConnector.submit(any(), any())(using any())).thenReturn(Future.successful(Done))

              submissionService.submit(submission)(using hc).futureValue

              val requestBodyCaptor: ArgumentCaptor[Source[ByteString, ?]] =
                ArgumentCaptor.forClass(classOf[Source[ByteString, ?]])

              verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
              verify(mockSubmissionConnector).submit(eqTo(submissionId), requestBodyCaptor.capture())(using any())
              verify(mockSdesService, never()).enqueueSubmission(any(), any(), any(), any())
              verify(mockAuditService).audit(eqTo(expectedAudit))(using any(), any())

              val result = requestBodyCaptor.getValue.runWith(Sink.fold(ByteString.empty)(_ ++ _)).futureValue
              val document = validate(result)

              document.docElem.label mustEqual "DPISubmissionRequest"

              (document \ "requestCommon" \ "receiptDate").text mustEqual DateTimeFormats.ISO8601Formatter.format(now)
              (document \ "requestCommon" \ "conversationID").text mustEqual submissionId
              (document \ "requestCommon" \ "schemaVersion").text mustEqual "1.0.0"

              (document \ "requestAdditionalDetail" \ "fileName").text mustEqual "test"
              (document \ "requestAdditionalDetail" \ "subscriptionID").text mustEqual subscription.id
              (document \ "requestAdditionalDetail" \ "tradingName") mustBe empty
              (document \ "requestAdditionalDetail" \ "isManual").text mustEqual "false"
              (document \ "requestAdditionalDetail" \ "isGBUser").text mustEqual subscription.gbUser.toString

              (document \ "requestAdditionalDetail" \ "primaryContact" \ "phoneNumber") mustBe empty
              (document \ "requestAdditionalDetail" \ "primaryContact" \ "emailAddress").text mustEqual organisationContact.email
              (document \ "requestAdditionalDetail" \ "primaryContact" \ "organisationDetails" \ "organisationName").text mustEqual organisationContact.organisation.name

              (document \ "requestAdditionalDetail" \ "secondaryContact") mustBe empty

              val inner = scala.xml.XML.loadString(innerContent)
              (document \ "requestDetail" \ "_").last mustEqual inner
            }

            "when minimal data is included and dprs0502 submission fails" in {

              val submission = Submission(
                _id = submissionId,
                submissionType = SubmissionType.Xml,
                dprsId = dprsId,
                operatorId = "operatorId",
                operatorName = "operatorName",
                assumingOperatorName = None,
                state = Validated(
                  downloadUrl = url"http://example.com",
                  reportingPeriod = Year.of(2024),
                  fileName = fileName,
                  checksum = "checksum",
                  size = 1337
                ),
                created = now,
                updated = now
              )

              val organisationContact = OrganisationContact(Organisation("org name"), "org email", None)
              val subscription = SubscriptionInfo(
                id = subscriptionId,
                gbUser = false,
                tradingName = None,
                primaryContact = organisationContact,
                secondaryContact = None
              )

              val innerContent = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/SubmissionSampleNoXmlDeclaration.xml")).mkString
              val fileSource = Source.single(ByteString.fromString(innerContent))

              val expectedAudit = AddSubmissionEvent(
                conversationId = submissionId,
                dprsId = dprsId,
                operatorId = "operatorId",
                operatorName = "operatorName",
                reportingPeriod = Year.of(2024),
                fileName = fileName,
                fileSize = 1337L,
                deliveryRoute = Dprs0502,
                processedAt = now,
                isSent = false
              )

              when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
              when(mockDownloadConnector.download(any())).thenReturn(Future.successful(fileSource))
              when(mockSubmissionConnector.submit(any(), any())(using any())).thenReturn(Future.failed(new RuntimeException()))

              submissionService.submit(submission)(using hc).failed.futureValue

              val requestBodyCaptor: ArgumentCaptor[Source[ByteString, ?]] =
                ArgumentCaptor.forClass(classOf[Source[ByteString, ?]])

              verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
              verify(mockSubmissionConnector).submit(eqTo(submissionId), requestBodyCaptor.capture())(using any())
              verify(mockSdesService, never()).enqueueSubmission(any(), any(), any(), any())
              verify(mockAuditService).audit(eqTo(expectedAudit))(using any(), any())
            }

          }

          "when the XML has an XML declaration" in {

            val submission = Submission(
              _id = submissionId,
              submissionType = SubmissionType.Xml,
              dprsId = dprsId,
              operatorId = "operatorId",
              operatorName = "operatorName",
              assumingOperatorName = None,
              state = Validated(
                downloadUrl = url"http://example.com",
                reportingPeriod = Year.of(2024),
                fileName = fileName,
                checksum = "checksum",
                size = 1337
              ),
              created = now,
              updated = now
            )

            val individualContact = IndividualContact(Individual("first", "last"), "individual email", Some("0777777"))
            val organisationContact = OrganisationContact(Organisation("org name"), "org email", Some("0787777"))
            val subscription = SubscriptionInfo(
              id = subscriptionId,
              gbUser = true,
              tradingName = Some("tradingName"),
              primaryContact = individualContact,
              secondaryContact = Some(organisationContact)
            )

            val innerContent = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/SubmissionSampleAssumed.xml")).mkString
            val fileSource = Source.single(ByteString.fromString(innerContent))

            val expectedAudit = AddSubmissionEvent(
              conversationId = submissionId,
              dprsId = dprsId,
              operatorId = "operatorId",
              operatorName = "operatorName",
              reportingPeriod = Year.of(2024),
              fileName = fileName,
              fileSize = 1337L,
              deliveryRoute = Dprs0502,
              processedAt = now,
              isSent = true
            )

            when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
            when(mockDownloadConnector.download(any())).thenReturn(Future.successful(fileSource))
            when(mockSubmissionConnector.submit(any(), any())(using any())).thenReturn(Future.successful(Done))

            submissionService.submit(submission)(using hc).futureValue

            val requestBodyCaptor: ArgumentCaptor[Source[ByteString, ?]] =
              ArgumentCaptor.forClass(classOf[Source[ByteString, ?]])

            verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
            verify(mockSubmissionConnector).submit(eqTo(submissionId), requestBodyCaptor.capture())(using any())
            verify(mockSdesService, never()).enqueueSubmission(any(), any(), any(), any())
            verify(mockAuditService).audit(eqTo(expectedAudit))(using any(), any())

            val result = requestBodyCaptor.getValue.runWith(Sink.fold(ByteString.empty)(_ ++ _)).futureValue
            val document = validate(result)

            document.docElem.label mustEqual "DPISubmissionRequest"

            (document \ "requestCommon" \ "receiptDate").text mustEqual DateTimeFormats.ISO8601Formatter.format(now)
            (document \ "requestCommon" \ "conversationID").text mustEqual submissionId
            (document \ "requestCommon" \ "schemaVersion").text mustEqual "1.0.0"

            (document \ "requestAdditionalDetail" \ "fileName").text mustEqual "test"
            (document \ "requestAdditionalDetail" \ "subscriptionID").text mustEqual subscription.id
            (document \ "requestAdditionalDetail" \ "tradingName").text mustEqual subscription.tradingName.value
            (document \ "requestAdditionalDetail" \ "isManual").text mustEqual "false"
            (document \ "requestAdditionalDetail" \ "isGBUser").text mustEqual subscription.gbUser.toString

            (document \ "requestAdditionalDetail" \ "primaryContact" \ "phoneNumber").text mustEqual individualContact.phone.value
            (document \ "requestAdditionalDetail" \ "primaryContact" \ "emailAddress").text mustEqual individualContact.email
            (document \ "requestAdditionalDetail" \ "primaryContact" \ "individualDetails" \ "firstName").text mustEqual individualContact.individual.firstName
            (document \ "requestAdditionalDetail" \ "primaryContact" \ "individualDetails" \ "lastName").text mustEqual individualContact.individual.lastName

            (document \ "requestAdditionalDetail" \ "secondaryContact" \ "phoneNumber").text mustEqual organisationContact.phone.value
            (document \ "requestAdditionalDetail" \ "secondaryContact" \ "emailAddress").text mustEqual organisationContact.email
            (document \ "requestAdditionalDetail" \ "secondaryContact" \ "organisationDetails" \ "organisationName").text mustEqual organisationContact.organisation.name

            val inner = scala.xml.XML.loadString(innerContent)
            (document \ "requestDetail" \ "_").last mustEqual inner
          }
        }
      }

      "when the submission is larger than the SDES submission threshold" - {

        "must enqueue the submission to be sent to SDES" in {

          val submission = Submission(
            _id = submissionId,
            submissionType = SubmissionType.Xml,
            dprsId = dprsId,
            operatorId = "operatorId",
            operatorName = "operatorName",
            assumingOperatorName = None,
            state = Validated(
              downloadUrl = url"http://example.com",
              reportingPeriod = Year.of(2024),
              fileName = fileName,
              checksum = "checksum",
              size = 3_000_001L
            ),
            created = now,
            updated = now
          )

          val individualContact = IndividualContact(Individual("first", "last"), "individual email", Some("0777777"))
          val organisationContact = OrganisationContact(Organisation("org name"), "org email", Some("0787777"))
          val subscription = SubscriptionInfo(
            id = subscriptionId,
            gbUser = true,
            tradingName = Some("tradingName"),
            primaryContact = individualContact,
            secondaryContact = Some(organisationContact)
          )

          val expectedAudit = AddSubmissionEvent(
            conversationId = submissionId,
            dprsId = dprsId,
            operatorId = "operatorId",
            operatorName = "operatorName",
            reportingPeriod = Year.of(2024),
            fileName = fileName,
            fileSize = 3_000_001L,
            deliveryRoute = Dct52A,
            processedAt = now,
            isSent = true
          )

          when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
          when(mockSdesService.enqueueSubmission(any(), any(), any(), any())).thenReturn(Future.successful(Done))
          when(mockDownloadConnector.download(any())).thenReturn(Future.failed(new RuntimeException()))
          when(mockSubmissionConnector.submit(any(), any())(using any())).thenReturn(Future.failed(new RuntimeException()))

          submissionService.submit(submission)(using hc).futureValue

          verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
          verify(mockSdesService).enqueueSubmission(submissionId, submission.state.asInstanceOf[Validated], subscription, expectedAudit)
          verify(mockDownloadConnector, never()).download(any())
          verify(mockSubmissionConnector, never()).submit(any(), any())(using any())
          verify(mockAuditService).audit(eqTo(expectedAudit))(using any(), any())
        }

        "when enqueue the submission fails" in {

          val submission = Submission(
            _id = submissionId,
            submissionType = SubmissionType.Xml,
            dprsId = dprsId,
            operatorId = "operatorId",
            operatorName = "operatorName",
            assumingOperatorName = None,
            state = Validated(
              downloadUrl = url"http://example.com",
              reportingPeriod = Year.of(2024),
              fileName = fileName,
              checksum = "checksum",
              size = 3_000_001L
            ),
            created = now,
            updated = now
          )

          val individualContact = IndividualContact(Individual("first", "last"), "individual email", Some("0777777"))
          val organisationContact = OrganisationContact(Organisation("org name"), "org email", Some("0787777"))
          val subscription = SubscriptionInfo(
            id = subscriptionId,
            gbUser = true,
            tradingName = Some("tradingName"),
            primaryContact = individualContact,
            secondaryContact = Some(organisationContact)
          )

          val expectedAudit = AddSubmissionEvent(
            conversationId = submissionId,
            dprsId = dprsId,
            operatorId = "operatorId",
            operatorName = "operatorName",
            reportingPeriod = Year.of(2024),
            fileName = fileName,
            fileSize = 3_000_001L,
            deliveryRoute = Dct52A,
            processedAt = now,
            isSent = true
          )

          when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
          when(mockSdesService.enqueueSubmission(any(), any(), any(), any())).thenReturn(Future.failed(new RuntimeException()))

          submissionService.submit(submission)(using hc).failed.futureValue

          verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
          verify(mockSdesService).enqueueSubmission(submissionId, submission.state.asInstanceOf[Validated], subscription, expectedAudit)
          verify(mockDownloadConnector, never()).download(any())
          verify(mockSubmissionConnector, never()).submit(any(), any())(using any())
          verify(mockAuditService).audit(eqTo(expectedAudit.copy(isSent = false)))(using any(), any())
        }

      }
    }

    "when the submission is not in a validated state" - {

      "must fail" in {

        val submission = Submission(
          _id = "id",
          submissionType = SubmissionType.Xml,
          dprsId = "dprsId",
          operatorId = "operatorId",
          operatorName = "operatorName",
          assumingOperatorName = None,
          state = Ready,
          created = now,
          updated = now
        )

        submissionService.submit(submission)(using hc).failed.futureValue
      }
    }
  }

  "submitAssumedReporting" - {

    "must create and submit an assumed reporting submission for the given operator" in {

      val dprsId = "dprsId"
      val submissionId = "submissionId"
      val subscriptionId = "subscriptionId"

      val operator = PlatformOperator(
        operatorId = "operatorId",
        operatorName = "operatorName",
        tinDetails = Seq(
          TinDetails(
            tin = "tin1",
            tinType = Utr,
            issuedBy = "GB"
          ),
          TinDetails(
            tin = "tin2",
            tinType = Vrn,
            issuedBy = "GB"
          )
        ),
        businessName = Some("businessName"),
        tradingName = Some("tradingName"),
        primaryContactDetails = ContactDetails(Some("phoneNumber"), "primaryContactName", "primaryEmail"),
        secondaryContactDetails = None,
        addressDetails = AddressDetails(
          line1 = "line1",
          line2 = Some("line2"),
          line3 = Some("line3"),
          line4 = Some("line4"),
          postCode = Some("postcode"),
          countryCode = Some("GB")
        ),
        notifications = Seq.empty
      )

      val assumingOperator = AssumingPlatformOperator(
        name = "assumingOperator",
        residentCountry = "US",
        tinDetails = Seq(
          TinDetails(
            tin = "tin3",
            tinType = Utr,
            issuedBy = "GB"
          ),
          TinDetails(
            tin = "tin4",
            tinType = Vrn,
            issuedBy = "GB"
          )
        ),
        registeredCountry = "US",
        address = "assumed line 1\nassumed line 2\nassumed line 3"
      )

      val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/create/test.xml").toURI)
      val expectedPayload = XML.loadString(expectedPayloadSource.mkString)
      expectedPayloadSource.close()

      val messageRef = "GB2024GB-operatorId-86233cb7-4922-4e54-a5ff-75f5e62eec0d"
      val assumedReportingPayload = AssumedReportingPayload(messageRef, expectedPayload)

      val expectedFileName = s"$messageRef.xml"
      val expectedSubmission = Submission(
        _id = submissionId,
        submissionType = SubmissionType.ManualAssumedReport,
        dprsId = dprsId,
        operatorId = operator.operatorId,
        operatorName = operator.operatorName,
        assumingOperatorName = Some(assumingOperator.name),
        state = Submitted(
          fileName = expectedFileName,
          reportingPeriod = Year.of(2024),
          size = 4105L
        ),
        created = now,
        updated = now
      )

      val individualContact = IndividualContact(Individual("first", "last"), "individual@email", Some("0777777"))
      val organisationContact = OrganisationContact(Organisation("org name"), "org@email", Some("0787777"))
      val subscription = SubscriptionInfo(
        id = subscriptionId,
        gbUser = true,
        tradingName = Some("tradingName"),
        primaryContact = individualContact,
        secondaryContact = Some(organisationContact)
      )

      when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
      when(mockPlatformOperatorConnector.get(any(), any())(using any())).thenReturn(Future.successful(Some(operator)))
      when(mockAssumedReportingService.createSubmission(any(), any(), any(), any())(using any())).thenReturn(Future.successful(assumedReportingPayload))
      when(mockUuidService.generate()).thenReturn(submissionId)
      when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
      when(mockSubmissionConnector.submit(any(), any())(using any())).thenReturn(Future.successful(Done))

      val submission = submissionService.submitAssumedReporting(dprsId, operator.operatorId, assumingOperator, Year.of(2024))(using HeaderCarrier()).futureValue
      submission mustEqual expectedSubmission

      verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
      verify(mockPlatformOperatorConnector).get(eqTo(dprsId), eqTo(operator.operatorId))(using any())
      verify(mockSubmissionRepository).save(expectedSubmission)
      verify(mockAssumedReportingService).createSubmission(eqTo(dprsId), eqTo(operator), eqTo(assumingOperator), eqTo(Year.of(2024)))(using any())

      val captor: ArgumentCaptor[Source[ByteString, ?]] = ArgumentCaptor.forClass(classOf[Source[ByteString, ?]])
      verify(mockSubmissionConnector).submit(eqTo(submissionId), captor.capture())(using any())

      val result = captor.getValue.runWith(Sink.fold(ByteString.empty)(_ ++ _)).futureValue
      val document = validate(result)

      document.docElem.label mustEqual "DPISubmissionRequest"

      (document \ "requestCommon" \ "receiptDate").text mustEqual DateTimeFormats.ISO8601Formatter.format(now)
      (document \ "requestCommon" \ "conversationID").text mustEqual submissionId
      (document \ "requestCommon" \ "schemaVersion").text mustEqual "1.0.0"

      (document \ "requestAdditionalDetail" \ "fileName").text mustEqual messageRef
      (document \ "requestAdditionalDetail" \ "subscriptionID").text mustEqual subscription.id
      (document \ "requestAdditionalDetail" \ "tradingName").text mustEqual subscription.tradingName.value
      (document \ "requestAdditionalDetail" \ "isManual").text mustEqual "true"
      (document \ "requestAdditionalDetail" \ "isGBUser").text mustEqual subscription.gbUser.toString

      (document \ "requestAdditionalDetail" \ "primaryContact" \ "phoneNumber").text mustEqual individualContact.phone.value
      (document \ "requestAdditionalDetail" \ "primaryContact" \ "emailAddress").text mustEqual "individual@email"
      (document \ "requestAdditionalDetail" \ "primaryContact" \ "individualDetails" \ "firstName").text mustEqual individualContact.individual.firstName
      (document \ "requestAdditionalDetail" \ "primaryContact" \ "individualDetails" \ "lastName").text mustEqual individualContact.individual.lastName

      (document \ "requestAdditionalDetail" \ "secondaryContact" \ "phoneNumber").text mustEqual organisationContact.phone.value
      (document \ "requestAdditionalDetail" \ "secondaryContact" \ "emailAddress").text mustEqual "org@email"
      (document \ "requestAdditionalDetail" \ "secondaryContact" \ "organisationDetails" \ "organisationName").text mustEqual organisationContact.organisation.name

      (document \ "requestDetail" \ "_").last mustEqual expectedPayload
    }
  }

  "submitAssumedReportingDeletion" - {

    "must create and submit an assumed reporting deletion for the given operator" in {

      val dprsId = "dprsId"
      val submissionId = "submissionId"
      val subscriptionId = "subscriptionId"

      val operator = PlatformOperator(
        operatorId = "operatorId",
        operatorName = "operatorName",
        tinDetails = Seq(
          TinDetails(
            tin = "tin1",
            tinType = Utr,
            issuedBy = "GB"
          ),
          TinDetails(
            tin = "tin2",
            tinType = Vrn,
            issuedBy = "GB"
          )
        ),
        businessName = Some("businessName"),
        tradingName = Some("tradingName"),
        primaryContactDetails = ContactDetails(Some("phoneNumber"), "primaryContactName", "primaryEmail"),
        secondaryContactDetails = None,
        addressDetails = AddressDetails(
          line1 = "line1",
          line2 = Some("line2"),
          line3 = Some("line3"),
          line4 = Some("line4"),
          postCode = Some("postcode"),
          countryCode = Some("GB")
        ),
        notifications = Seq.empty
      )

      val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/delete/test.xml").toURI)
      val expectedPayload = XML.loadString(expectedPayloadSource.mkString)
      expectedPayloadSource.close()

      val messageRef = "GB2024GB-operatorId-86233cb7-4922-4e54-a5ff-75f5e62eec0d"
      val assumedReportingPayload = AssumedReportingPayload(messageRef, expectedPayload)

      val expectedFileName = s"$messageRef.xml"
      val expectedSubmission = Submission(
        _id = submissionId,
        submissionType = SubmissionType.ManualAssumedReport,
        dprsId = dprsId,
        operatorId = operator.operatorId,
        operatorName = operator.operatorName,
        assumingOperatorName = None,
        state = Submitted(
          fileName = expectedFileName,
          reportingPeriod = Year.of(2024),
          size = 3973L
        ),
        created = now,
        updated = now
      )

      val individualContact = IndividualContact(Individual("first", "last"), "individual@email", Some("0777777"))
      val organisationContact = OrganisationContact(Organisation("org name"), "org@email", Some("0787777"))
      val subscription = SubscriptionInfo(
        id = subscriptionId,
        gbUser = true,
        tradingName = Some("tradingName"),
        primaryContact = individualContact,
        secondaryContact = Some(organisationContact)
      )

      when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
      when(mockPlatformOperatorConnector.get(any(), any())(using any())).thenReturn(Future.successful(Some(operator)))
      when(mockAssumedReportingService.createDeleteSubmission(any(), any(), any())(using any())).thenReturn(Future.successful(assumedReportingPayload))
      when(mockUuidService.generate()).thenReturn(submissionId)
      when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
      when(mockSubmissionConnector.submit(any(), any())(using any())).thenReturn(Future.successful(Done))

      val submission = submissionService.submitAssumedReportingDeletion(dprsId, operator.operatorId, Year.of(2024))(using HeaderCarrier()).futureValue
      submission mustEqual expectedSubmission

      verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
      verify(mockPlatformOperatorConnector).get(eqTo(dprsId), eqTo(operator.operatorId))(using any())
      verify(mockSubmissionRepository).save(expectedSubmission)
      verify(mockAssumedReportingService).createDeleteSubmission(eqTo(dprsId), eqTo(operator.operatorId), eqTo(Year.of(2024)))(using any())

      val captor: ArgumentCaptor[Source[ByteString, ?]] = ArgumentCaptor.forClass(classOf[Source[ByteString, ?]])
      verify(mockSubmissionConnector).submit(eqTo(submissionId), captor.capture())(using any())

      val result = captor.getValue.runWith(Sink.fold(ByteString.empty)(_ ++ _)).futureValue
      val document = validate(result)

      document.docElem.label mustEqual "DPISubmissionRequest"

      (document \ "requestCommon" \ "receiptDate").text mustEqual DateTimeFormats.ISO8601Formatter.format(now)
      (document \ "requestCommon" \ "conversationID").text mustEqual submissionId
      (document \ "requestCommon" \ "schemaVersion").text mustEqual "1.0.0"

      (document \ "requestAdditionalDetail" \ "fileName").text mustEqual messageRef
      (document \ "requestAdditionalDetail" \ "subscriptionID").text mustEqual subscription.id
      (document \ "requestAdditionalDetail" \ "tradingName").text mustEqual subscription.tradingName.value
      (document \ "requestAdditionalDetail" \ "isManual").text mustEqual "true"
      (document \ "requestAdditionalDetail" \ "isGBUser").text mustEqual subscription.gbUser.toString

      (document \ "requestAdditionalDetail" \ "primaryContact" \ "phoneNumber").text mustEqual individualContact.phone.value
      (document \ "requestAdditionalDetail" \ "primaryContact" \ "emailAddress").text mustEqual "individual@email"
      (document \ "requestAdditionalDetail" \ "primaryContact" \ "individualDetails" \ "firstName").text mustEqual individualContact.individual.firstName
      (document \ "requestAdditionalDetail" \ "primaryContact" \ "individualDetails" \ "lastName").text mustEqual individualContact.individual.lastName

      (document \ "requestAdditionalDetail" \ "secondaryContact" \ "phoneNumber").text mustEqual organisationContact.phone.value
      (document \ "requestAdditionalDetail" \ "secondaryContact" \ "emailAddress").text mustEqual "org@email"
      (document \ "requestAdditionalDetail" \ "secondaryContact" \ "organisationDetails" \ "organisationName").text mustEqual organisationContact.organisation.name

      (document \ "requestDetail" \ "_").last mustEqual expectedPayload
    }
  }

  private def validate(content: ByteString): Document = {

    val resource = Paths.get(getClass.getResource("/schemas/DPISubmissionRequest_v1.0.xsd").toURI).toFile
    val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    val schemaFile = new StreamSource(resource)
    val schema = schemaFactory.newSchema(schemaFile)

    val parserFactory = SAXParserFactory.newInstance()
    parserFactory.setNamespaceAware(true)
    parserFactory.setSchema(schema)

    val reader = parserFactory.newSAXParser().getXMLReader
    reader.setErrorHandler(new ErrorHandler {
      override def warning(exception: SAXParseException): Unit = throw exception

      override def error(exception: SAXParseException): Unit = throw exception

      override def fatalError(exception: SAXParseException): Unit = throw exception
    })

    val xmlLoader = scala.xml.XML.withXMLReader(reader)

    try {
      // If we load it with a schema it strips whitespace so we load it twice
      xmlLoader.loadStringDocument(content.utf8String)
      scala.xml.XML.loadStringDocument(content.utf8String)
    } catch {
      case e: SAXParseException =>
        fail(s"Failed schema validation with: ${e.getMessage}")
    }
  }
}
