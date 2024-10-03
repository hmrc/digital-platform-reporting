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

import connectors.{DownloadConnector, SubmissionConnector, SubscriptionConnector}
import models.submission.Submission
import models.submission.Submission.State.{Ready, Validated}
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
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import utils.DateTimeFormats

import java.nio.file.Paths
import java.time.{Clock, Instant, Year, ZoneOffset}
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import scala.concurrent.Future
import scala.xml.{Document, SAXParseException}

class SubmissionServiceSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfterEach
    with ScalaFutures
    with OptionValues
    with IntegrationPatience {

  private val now = Instant.now()
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val mockSubmissionConnector: SubmissionConnector = mock[SubmissionConnector]
  private val mockSubscriptionConnector: SubscriptionConnector = mock[SubscriptionConnector]
  private val mockDownloadConnector: DownloadConnector = mock[DownloadConnector]
  private val mockSdesService: SdesService = mock[SdesService]

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
        bind[Clock].toInstance(clock)
      )
      .build()

  given Materializer = app.injector.instanceOf[Materializer]

  private lazy val submissionService: SubmissionService = app.injector.instanceOf[SubmissionService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      mockSubmissionConnector,
      mockSubscriptionConnector,
      mockDownloadConnector,
      mockSdesService
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
                dprsId = dprsId,
                operatorId = "operatorId",
                operatorName = "operatorName",
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

              when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
              when(mockSdesService.enqueueSubmission(any(), any(), any())).thenReturn(Future.failed(new RuntimeException()))
              when(mockDownloadConnector.download(any())).thenReturn(Future.successful(fileSource))
              when(mockSubmissionConnector.submit(any(), any())(using any())).thenReturn(Future.successful(Done))

              submissionService.submit(submission)(using hc).futureValue

              val requestBodyCaptor: ArgumentCaptor[Source[ByteString, _]] =
                ArgumentCaptor.forClass(classOf[Source[ByteString, _]])

              verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
              verify(mockSubmissionConnector).submit(eqTo(submissionId), requestBodyCaptor.capture())(using any())
              verify(mockSdesService, never()).enqueueSubmission(any(), any(), any())

              val result = requestBodyCaptor.getValue.runWith(Sink.fold(ByteString.empty)(_ ++ _)).futureValue
              val document = validate(result)

              document.docElem.label mustEqual "DPISubmissionRequest"

              (document \ "requestCommon" \ "receiptDate").text mustEqual DateTimeFormats.ISO8601Formatter.format(now)
              (document \ "requestCommon" \ "conversationID").text mustEqual submissionId
              (document \ "requestCommon" \ "schemaVersion").text mustEqual "1.0.0"

              (document \ "requestAdditionalDetail" \ "fileName").text mustEqual fileName
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
              (document \ "requestDetail" \ "_").last mustEqual scala.xml.Utility.trim(inner)
            }

            "when minimal data is included" in {

              val submission = Submission(
                _id = submissionId,
                dprsId = dprsId,
                operatorId = "operatorId",
                operatorName = "operatorName",
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

              when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
              when(mockSdesService.enqueueSubmission(any(), any(), any())).thenReturn(Future.failed(new RuntimeException()))
              when(mockDownloadConnector.download(any())).thenReturn(Future.successful(fileSource))
              when(mockSubmissionConnector.submit(any(), any())(using any())).thenReturn(Future.successful(Done))

              submissionService.submit(submission)(using hc).futureValue

              val requestBodyCaptor: ArgumentCaptor[Source[ByteString, _]] =
                ArgumentCaptor.forClass(classOf[Source[ByteString, _]])

              verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
              verify(mockSubmissionConnector).submit(eqTo(submissionId), requestBodyCaptor.capture())(using any())
              verify(mockSdesService, never()).enqueueSubmission(any(), any(), any())

              val result = requestBodyCaptor.getValue.runWith(Sink.fold(ByteString.empty)(_ ++ _)).futureValue
              val document = validate(result)

              document.docElem.label mustEqual "DPISubmissionRequest"

              (document \ "requestCommon" \ "receiptDate").text mustEqual DateTimeFormats.ISO8601Formatter.format(now)
              (document \ "requestCommon" \ "conversationID").text mustEqual submissionId
              (document \ "requestCommon" \ "schemaVersion").text mustEqual "1.0.0"

              (document \ "requestAdditionalDetail" \ "fileName").text mustEqual fileName
              (document \ "requestAdditionalDetail" \ "subscriptionID").text mustEqual subscription.id
              (document \ "requestAdditionalDetail" \ "tradingName") mustBe empty
              (document \ "requestAdditionalDetail" \ "isManual").text mustEqual "false"
              (document \ "requestAdditionalDetail" \ "isGBUser").text mustEqual subscription.gbUser.toString

              (document \ "requestAdditionalDetail" \ "primaryContact" \ "phoneNumber") mustBe empty
              (document \ "requestAdditionalDetail" \ "primaryContact" \ "emailAddress").text mustEqual organisationContact.email
              (document \ "requestAdditionalDetail" \ "primaryContact" \ "organisationDetails" \ "organisationName").text mustEqual organisationContact.organisation.name

              (document \ "requestAdditionalDetail" \ "secondaryContact") mustBe empty

              val inner = scala.xml.XML.loadString(innerContent)
              (document \ "requestDetail" \ "_").last mustEqual scala.xml.Utility.trim(inner)
            }
          }

          "when the XML has an XML declaration" in {

            val submission = Submission(
              _id = submissionId,
              dprsId = dprsId,
              operatorId = "operatorId",
              operatorName = "operatorName",
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

            val innerContent = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/SubmissionSample.xml")).mkString
            val fileSource = Source.single(ByteString.fromString(innerContent))

            when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
            when(mockSdesService.enqueueSubmission(any(), any(), any())).thenReturn(Future.failed(new RuntimeException()))
            when(mockDownloadConnector.download(any())).thenReturn(Future.successful(fileSource))
            when(mockSubmissionConnector.submit(any(), any())(using any())).thenReturn(Future.successful(Done))

            submissionService.submit(submission)(using hc).futureValue

            val requestBodyCaptor: ArgumentCaptor[Source[ByteString, _]] =
              ArgumentCaptor.forClass(classOf[Source[ByteString, _]])

            verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
            verify(mockSubmissionConnector).submit(eqTo(submissionId), requestBodyCaptor.capture())(using any())
            verify(mockSdesService, never()).enqueueSubmission(any(), any(), any())

            val result = requestBodyCaptor.getValue.runWith(Sink.fold(ByteString.empty)(_ ++ _)).futureValue
            val document = validate(result)

            document.docElem.label mustEqual "DPISubmissionRequest"

            (document \ "requestCommon" \ "receiptDate").text mustEqual DateTimeFormats.ISO8601Formatter.format(now)
            (document \ "requestCommon" \ "conversationID").text mustEqual submissionId
            (document \ "requestCommon" \ "schemaVersion").text mustEqual "1.0.0"

            (document \ "requestAdditionalDetail" \ "fileName").text mustEqual fileName
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
            (document \ "requestDetail" \ "_").last mustEqual scala.xml.Utility.trim(inner)
          }
        }
      }

      "when the submission is larger than the SDES submission threshold" - {

        "must enqueue the submission to be sent to SDES" in {

          val submission = Submission(
            _id = submissionId,
            dprsId = dprsId,
            operatorId = "operatorId",
            operatorName = "operatorName",
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

          when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
          when(mockSdesService.enqueueSubmission(any(), any(), any())).thenReturn(Future.successful(Done))
          when(mockDownloadConnector.download(any())).thenReturn(Future.failed(new RuntimeException()))
          when(mockSubmissionConnector.submit(any(), any())(using any())).thenReturn(Future.failed(new RuntimeException()))

          submissionService.submit(submission)(using hc).futureValue

          verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
          verify(mockSdesService).enqueueSubmission(submissionId, submission.state.asInstanceOf[Validated], subscription)
          verify(mockDownloadConnector, never()).download(any())
          verify(mockSubmissionConnector, never()).submit(any(), any())(using any())
        }
      }
    }

    "when the submission is not in a validated state" - {

      "must fail" in {

        val submission = Submission(
          _id = "id",
          dprsId = "dprsId",
          operatorId = "operatorId",
          operatorName = "operatorName",
          state = Ready,
          created = now,
          updated = now
        )

        submissionService.submit(submission)(using hc).failed.futureValue
      }
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
      xmlLoader.loadStringDocument(content.utf8String)
    } catch { case e: SAXParseException =>
      fail(s"Failed schema validation with: ${e.getMessage}")
    }
  }
}
