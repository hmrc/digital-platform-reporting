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

import connectors.{EmailConnector, PlatformOperatorConnector, SubscriptionConnector}
import generated.{AEOI, Accepted, BREResponse_Type, ErrorDetail_Type, FileError_Type, Generated_BREResponse_TypeFormat, GenericStatusMessage_Type, RecordError_Type, Rejected, RequestCommon_Type, RequestDetail_Type, ValidationErrors_Type, ValidationResult_Type}
import models.audit.CadxSubmissionResponseEvent
import models.audit.CadxSubmissionResponseEvent.FileStatus.{Failed, Passed}
import models.operator.{AddressDetails, ContactDetails}
import models.operator.responses.PlatformOperator
import models.submission.Submission.{State, SubmissionType}
import models.submission.{CadxValidationError, Submission}
import models.subscription.{Individual, IndividualContact, Organisation, OrganisationContact}
import models.subscription.responses.SubscriptionInfo
import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, times, verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repository.{CadxValidationErrorRepository, SubmissionRepository}
import services.SubmissionService.NoPlatformOperatorException
import utils.DateTimeFormats
import utils.DateTimeFormats.EmailDateTimeFormatter

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, Year, ZoneOffset}
import scala.concurrent.Future
import scala.xml.Utility

class CadxResultServiceSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with ScalaFutures
    with MockitoSugar
    with BeforeAndAfterEach
    with IntegrationPatience {

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      mockSubmissionRepository,
      mockCadxValidationErrorRepository,
      mockAuditService,
      mockSubscriptionConnector,
      mockPlatformOperatorConnector,
      mockEmailService
    )
  }

  private val mockSubmissionRepository: SubmissionRepository = mock[SubmissionRepository]
  private val mockCadxValidationErrorRepository: CadxValidationErrorRepository = mock[CadxValidationErrorRepository]
  private val mockAuditService: AuditService = mock[AuditService]
  private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
  private val mockSubscriptionConnector: SubscriptionConnector = mock[SubscriptionConnector]
  private val mockPlatformOperatorConnector: PlatformOperatorConnector = mock[PlatformOperatorConnector]
  private val mockEmailService: EmailService = mock[EmailService]
  private val mockEmailConnector: EmailConnector = mock[EmailConnector]


  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(
      bind[SubmissionRepository].toInstance(mockSubmissionRepository),
      bind[CadxValidationErrorRepository].toInstance(mockCadxValidationErrorRepository),
      bind[AuditService].toInstance(mockAuditService),
      bind[Clock].toInstance(clock),
      bind[SubscriptionConnector].toInstance(mockSubscriptionConnector),
      bind[PlatformOperatorConnector].toInstance(mockPlatformOperatorConnector),
      bind[EmailService].toInstance(mockEmailService),
      bind[EmailConnector].toInstance(mockEmailConnector)
    )
    .build()

  private lazy val cadxResultService: CadxResultService = app.injector.instanceOf[CadxResultService]

  private val now = clock.instant()

  "processResult" - {

    "when there is a submission matching the conversationId" - {

      "when the submission is in a submitted state" - {

        val individualContact = IndividualContact(Individual("first", "last"), "individual email", Some("0777777"))
        val organisationContact = OrganisationContact(Organisation("org name"), "org email", Some("0787777"))

        val subscription = SubscriptionInfo(
          id = "subscriptionId",
          gbUser = true,
          tradingName = Some("tradingName"),
          primaryContact = individualContact,
          secondaryContact = Some(organisationContact)
        )
        val dprsId = "dprsId"

        val operator = PlatformOperator(
          operatorId = "operatorId",
          operatorName = "operatorName",
          tinDetails = Seq.empty,
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

        "when the response indicates that the submission was approved" - {

          val expectedState = State.Approved(
            fileName = "test.xml",
            reportingPeriod = Year.of(2024)
          )

          val submission = Submission(
            _id = "submissionId",
            submissionType = SubmissionType.Xml,
            dprsId = "dprsId",
            operatorId = "operatorId",
            operatorName = "operatorName",
            assumingOperatorName = None,
            state = State.Submitted(
              fileName = "test.xml",
              reportingPeriod = Year.of(2024),
              size = 454645L
            ),
            created = now.minus(1, ChronoUnit.DAYS),
            updated = now.minus(1, ChronoUnit.DAYS)
          )

          val expectedSubmission = submission.copy(
            state = State.Approved(
              fileName = "test.xml",
              reportingPeriod = Year.of(2024)
            ),
            updated = now
          )

          "must update the submission" - {

            val approvedResponse = BREResponse_Type(
              requestCommon = RequestCommon_Type(
                receiptDate = scalaxb.Helper.toCalendar(DateTimeFormats.ISO8601Formatter.format(now.minus(1, ChronoUnit.HOURS))),
                regime = AEOI,
                conversationID = submission._id,
                schemaVersion = "1.0.0"
              ),
              requestDetail = RequestDetail_Type(
                GenericStatusMessage = GenericStatusMessage_Type(
                  ValidationErrors = ValidationErrors_Type(
                    FileError = Seq.empty,
                    RecordError = Seq.empty
                  ),
                  ValidationResult = ValidationResult_Type(
                    Status = Accepted
                  )
                )
              )
            )

            val expectedAudit = CadxSubmissionResponseEvent(
              conversationId = "submissionId",
              dprsId = "dprsId",
              operatorId = "operatorId",
              operatorName = "operatorName",
              fileName = "test.xml",
              fileStatus = Passed
            )

            val source = Source.single {
              ByteString.fromString(Utility.trim(scalaxb.toXML(approvedResponse, "BREResponse", generated.defaultScope).head).toString)
            }

            "when submission type is ManualAssumedReport do not send emails" in {
              when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission.copy(submissionType = SubmissionType.ManualAssumedReport))))
              when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
              when(mockCadxValidationErrorRepository.saveBatch(any())).thenReturn(Future.successful(Done))
              when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
              when(mockPlatformOperatorConnector.get(any(), any())(using any())).thenReturn(Future.successful(Some(operator)))
              when(mockEmailService.sendSuccessfulBusinessRulesChecksEmails(any(), any(), any(), any())(any())).thenReturn(Future.successful(Done))

              cadxResultService.processResult(source).futureValue

              verify(mockSubmissionRepository).getById(submission._id)
              verify(mockSubmissionRepository).save(expectedSubmission.copy(submissionType = SubmissionType.ManualAssumedReport))
              verify(mockCadxValidationErrorRepository, never()).saveBatch(any())
              verify(mockAuditService).audit(eqTo(expectedAudit))(using any(), any())
              verify(mockSubscriptionConnector, never()).get(any())(using any())
              verify(mockPlatformOperatorConnector, never()).get(any(), any())(using any())
              verify(mockEmailService, never()).sendSuccessfulBusinessRulesChecksEmails(any(), any(), any(), any())(any())
            }

            "when submission type is XML send emails" - {

              "subscription and platformOperator returned successfully" in {
                when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission)))
                when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
                when(mockCadxValidationErrorRepository.saveBatch(any())).thenReturn(Future.successful(Done))
                when(mockSubscriptionConnector.get(any())(any())).thenReturn(Future.successful(subscription))
                when(mockPlatformOperatorConnector.get(any(), any())(any())).thenReturn(Future.successful(Some(operator)))
                when(mockEmailConnector.send(any())(any())).thenReturn(Future.successful(Done))
                when(mockEmailService.sendSuccessfulBusinessRulesChecksEmails(any(), any(), any(), any())(any())).thenReturn(Future.successful(Done))

                val expectedChecksCompletedDateTime = EmailDateTimeFormatter.format(clock.instant()).replace("AM", "am").replace("PM", "pm")
                cadxResultService.processResult(source).futureValue
                val dateTimeCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

                verify(mockSubmissionRepository).getById(submission._id)
                verify(mockSubmissionRepository).save(expectedSubmission)
                verify(mockCadxValidationErrorRepository, never()).saveBatch(any())
                verify(mockAuditService).audit(eqTo(expectedAudit))(using any(), any())
                verify(mockSubscriptionConnector).get(eqTo(dprsId))(any())
                verify(mockPlatformOperatorConnector).get(eqTo(dprsId), eqTo(operator.operatorId))(any())
                verify(mockEmailService).sendSuccessfulBusinessRulesChecksEmails(eqTo(expectedState), dateTimeCaptor.capture(), eqTo(operator), eqTo(subscription))(any())

                val captorResult = dateTimeCaptor.getValue
                val result = captorResult.substring(captorResult.indexOf("GMT"))
                val expected = expectedChecksCompletedDateTime.substring(expectedChecksCompletedDateTime.indexOf("GMT"))
                result mustEqual expected
              }

              "subscription returned failure" in {
                when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission)))
                when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
                when(mockCadxValidationErrorRepository.saveBatch(any())).thenReturn(Future.successful(Done))
                when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.failed(new Exception("foo")))
                when(mockPlatformOperatorConnector.get(any(), any())(using any())).thenReturn(Future.successful(Some(operator)))
                when(mockEmailService.sendSuccessfulBusinessRulesChecksEmails(any(), any(), any(), any())(any())).thenReturn(Future.successful(Done))

                cadxResultService.processResult(source).futureValue

                verify(mockSubmissionRepository).getById(submission._id)
                verify(mockSubmissionRepository).save(expectedSubmission)
                verify(mockCadxValidationErrorRepository, never()).saveBatch(any())
                verify(mockAuditService).audit(eqTo(expectedAudit))(using any(), any())
                verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
                verify(mockPlatformOperatorConnector, never()).get(any(), any())(using any())
                verify(mockEmailService, never()).sendSuccessfulBusinessRulesChecksEmails(any(), any(), any(), any())(any())
              }

              "platformOperator returned failure" in {
                when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission)))
                when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
                when(mockCadxValidationErrorRepository.saveBatch(any())).thenReturn(Future.successful(Done))
                when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
                when(mockPlatformOperatorConnector.get(any(), any())(using any())).thenReturn(Future.failed(NoPlatformOperatorException(submission.dprsId, submission.operatorId)))
                when(mockEmailService.sendSuccessfulBusinessRulesChecksEmails(any(), any(), any(), any())(any())).thenReturn(Future.successful(Done))

                cadxResultService.processResult(source).futureValue

                verify(mockSubmissionRepository).getById(submission._id)
                verify(mockSubmissionRepository).save(expectedSubmission)
                verify(mockCadxValidationErrorRepository, never()).saveBatch(any())
                verify(mockAuditService).audit(eqTo(expectedAudit))(using any(), any())
                verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
                verify(mockPlatformOperatorConnector).get(eqTo(dprsId), eqTo(operator.operatorId))(using any())
                verify(mockEmailService, never()).sendSuccessfulBusinessRulesChecksEmails(any(), any(), any(), any())(any())
              }
            }

          }

          "when there is unexpected content in the ValidationErrors element" in {

            val response = """<BREResponse xmlns:dpi="urn:oecd:ties:dpi:v1" xmlns:gsm="http://www.hmrc.gsi.gov.uk/gsm" xmlns:iso="urn:oecd:ties:isodpitypes:v1" xmlns:stf="urn:oecd:ties:dpistf:v1" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><requestCommon><receiptDate>2024-11-01T12:24:12Z</receiptDate><regime>AEOI</regime><conversationID>submissionId</conversationID><schemaVersion>1.0.0</schemaVersion></requestCommon><requestDetail><GenericStatusMessage><ValidationErrors>   </ValidationErrors><ValidationResult><Status>Accepted</Status></ValidationResult></GenericStatusMessage></requestDetail></BREResponse>""".stripMargin
            val source = Source.single(ByteString.fromString(response))

            when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission)))
            when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
            when(mockCadxValidationErrorRepository.saveBatch(any())).thenReturn(Future.successful(Done))
            when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
            when(mockPlatformOperatorConnector.get(any(), any())(using any())).thenReturn(Future.successful(Some(operator)))
            when(mockEmailService.sendSuccessfulBusinessRulesChecksEmails(any(), any(), any(), any())(any())).thenReturn(Future.successful(Done))

            val expectedChecksCompletedDateTime = EmailDateTimeFormatter.format(clock.instant()).replace("AM", "am").replace("PM", "pm")
            cadxResultService.processResult(source).futureValue
            val dateTimeCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])

            verify(mockSubmissionRepository).getById(submission._id)
            verify(mockSubmissionRepository).save(expectedSubmission)
            verify(mockCadxValidationErrorRepository, never()).saveBatch(any())
            verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
            verify(mockPlatformOperatorConnector).get(eqTo(dprsId), eqTo(operator.operatorId))(using any())
            verify(mockEmailService).sendSuccessfulBusinessRulesChecksEmails(eqTo(expectedState), dateTimeCaptor.capture(), eqTo(operator), eqTo(subscription))(any())

            val captorResult = dateTimeCaptor.getValue
            val result = captorResult.substring(captorResult.indexOf("GMT"))
            val expected = expectedChecksCompletedDateTime.substring(expectedChecksCompletedDateTime.indexOf("GMT"))
            result mustEqual expected

          }
        }

        "when the response indicates that the submission was rejected" - {

          val expectedState = State.Rejected(
            fileName = "test.xml",
            reportingPeriod = Year.of(2024)
          )

          "must update the submission" - {

            val submission = Submission(
              _id = "submissionId",
              submissionType = SubmissionType.Xml,
              dprsId = "dprsId",
              operatorId = "operatorId",
              operatorName = "operatorName",
              assumingOperatorName = None,
              state = State.Submitted(
                fileName = "test.xml",
                reportingPeriod = Year.of(2024),
                size = 435342L
              ),
              created = now.minus(1, ChronoUnit.DAYS),
              updated = now.minus(1, ChronoUnit.DAYS)
            )

            val expectedSubmission = submission.copy(state = State.Rejected(
              fileName = "test.xml",
              reportingPeriod = Year.of(2024)
            ), updated = now)

            val expectedFileError: CadxValidationError.FileError = CadxValidationError.FileError(
              submissionId = submission._id,
              dprsId = "dprsId",
              code = "001",
              detail = Some("detail"),
              created = now
            )

            val expectedRowError1: CadxValidationError.RowError = CadxValidationError.RowError(
              submissionId = submission._id,
              dprsId = "dprsId",
              code = "002",
              detail = Some("detail 2"),
              docRef = "1",
              created = now
            )

            val expectedRowError2 = expectedRowError1.copy(docRef = "2")

            val rejectedResponse = BREResponse_Type(
              requestCommon = RequestCommon_Type(
                receiptDate = scalaxb.Helper.toCalendar(DateTimeFormats.ISO8601Formatter.format(now)),
                regime = AEOI,
                conversationID = submission._id,
                schemaVersion = "1.0.0"
              ),
              requestDetail = RequestDetail_Type(
                GenericStatusMessage = GenericStatusMessage_Type(
                  ValidationErrors = ValidationErrors_Type(
                    FileError = (0 until 1000).map { _ =>
                      FileError_Type(
                        Code = "001",
                        Details = Some(ErrorDetail_Type("detail"))
                      )
                    },
                    RecordError = (0 until 200).map { _ =>
                      RecordError_Type(
                        Code = "002",
                        Details = Some(ErrorDetail_Type("detail 2")),
                        DocRefIDInError = Seq("1", "2")
                      )
                    }
                  ),
                  ValidationResult = ValidationResult_Type(
                    Status = Rejected
                  )
                )
              )
            )

            val expectedAudit = CadxSubmissionResponseEvent(
              conversationId = "submissionId",
              dprsId = "dprsId",
              operatorId = "operatorId",
              operatorName = "operatorName",
              fileName = "test.xml",
              fileStatus = Failed
            )

            val source = Source.single {
              ByteString.fromString(Utility.trim(scalaxb.toXML(rejectedResponse, "BREResponse", generated.defaultScope).head).toString)
            }

            "when submission type is ManualAssumedReport do not send emails" in {

              when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission.copy(submissionType = SubmissionType.ManualAssumedReport))))
              when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
              when(mockCadxValidationErrorRepository.saveBatch(any())).thenReturn(Future.successful(Done))
              when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
              when(mockPlatformOperatorConnector.get(any(), any())(using any())).thenReturn(Future.successful(Some(operator)))
              when(mockEmailService.sendFailedBusinessRulesChecksEmails(any(), any(), any(), any())(any())).thenReturn(Future.successful(Done))

              cadxResultService.processResult(source).futureValue

              val captor: ArgumentCaptor[Seq[CadxValidationError]] = ArgumentCaptor.forClass(classOf[Seq[CadxValidationError]])

              verify(mockSubmissionRepository).getById(submission._id)
              verify(mockSubmissionRepository).save(expectedSubmission.copy(submissionType = SubmissionType.ManualAssumedReport))
              verify(mockCadxValidationErrorRepository, times(2)).saveBatch(captor.capture())
              verify(mockAuditService).audit(eqTo(expectedAudit))(using any(), any())
              verify(mockSubscriptionConnector, never()).get(any())(using any())
              verify(mockPlatformOperatorConnector, never()).get(any(), any())(using any())
              verify(mockEmailService, never()).sendFailedBusinessRulesChecksEmails(any(), any(), any(), any())(any())

              val results = captor.getAllValues

              results.get(0) mustEqual (0 until 1000).map(_ => expectedFileError)
              results.get(1) mustEqual (0 until 200).flatMap(_ => Seq(expectedRowError1, expectedRowError2))

            }

            "when submission type is XML send emails" - {

              "subscription and platformOperator returned successfully" in {
                when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission)))
                when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
                when(mockCadxValidationErrorRepository.saveBatch(any())).thenReturn(Future.successful(Done))
                when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
                when(mockPlatformOperatorConnector.get(any(), any())(using any())).thenReturn(Future.successful(Some(operator)))
                when(mockEmailService.sendFailedBusinessRulesChecksEmails(any(), any(), any(), any())(any())).thenReturn(Future.successful(Done))

                cadxResultService.processResult(source).futureValue

                val captor: ArgumentCaptor[Seq[CadxValidationError]] = ArgumentCaptor.forClass(classOf[Seq[CadxValidationError]])

                verify(mockSubmissionRepository).getById(submission._id)
                verify(mockSubmissionRepository).save(expectedSubmission)
                verify(mockCadxValidationErrorRepository, times(2)).saveBatch(captor.capture())
                verify(mockAuditService).audit(eqTo(expectedAudit))(using any(), any())
                verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
                verify(mockPlatformOperatorConnector).get(eqTo(dprsId), eqTo(operator.operatorId))(using any())
                verify(mockEmailService).sendFailedBusinessRulesChecksEmails(eqTo(expectedState), any(), eqTo(operator), eqTo(subscription))(any())

                val results = captor.getAllValues

                results.get(0) mustEqual (0 until 1000).map(_ => expectedFileError)
                results.get(1) mustEqual (0 until 200).flatMap(_ => Seq(expectedRowError1, expectedRowError2))
              }

              "subscription returned failure" in {
                when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission)))
                when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
                when(mockCadxValidationErrorRepository.saveBatch(any())).thenReturn(Future.successful(Done))
                when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.failed(new Exception("foo")))
                when(mockPlatformOperatorConnector.get(any(), any())(using any())).thenReturn(Future.successful(Some(operator)))
                when(mockEmailService.sendFailedBusinessRulesChecksEmails(any(), any(), any(), any())(any())).thenReturn(Future.successful(Done))

                cadxResultService.processResult(source).futureValue

                val captor: ArgumentCaptor[Seq[CadxValidationError]] = ArgumentCaptor.forClass(classOf[Seq[CadxValidationError]])

                verify(mockSubmissionRepository).getById(submission._id)
                verify(mockSubmissionRepository).save(expectedSubmission)
                verify(mockCadxValidationErrorRepository, times(2)).saveBatch(captor.capture())
                verify(mockAuditService).audit(eqTo(expectedAudit))(using any(), any())
                verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
                verify(mockPlatformOperatorConnector, never()).get(any(), any())(using any())
                verify(mockEmailService, never()).sendFailedBusinessRulesChecksEmails(any(), any(), any(), any())(any())

                val results = captor.getAllValues

                results.get(0) mustEqual (0 until 1000).map(_ => expectedFileError)
                results.get(1) mustEqual (0 until 200).flatMap(_ => Seq(expectedRowError1, expectedRowError2))
              }

              "platformOperator returned failure" in {
                when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission)))
                when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
                when(mockCadxValidationErrorRepository.saveBatch(any())).thenReturn(Future.successful(Done))
                when(mockSubscriptionConnector.get(any())(using any())).thenReturn(Future.successful(subscription))
                when(mockPlatformOperatorConnector.get(any(), any())(using any())).thenReturn(Future.failed(NoPlatformOperatorException(submission.dprsId, submission.operatorId)))
                when(mockEmailService.sendFailedBusinessRulesChecksEmails(any(), any(), any(), any())(any())).thenReturn(Future.successful(Done))

                cadxResultService.processResult(source).futureValue

                val captor: ArgumentCaptor[Seq[CadxValidationError]] = ArgumentCaptor.forClass(classOf[Seq[CadxValidationError]])

                verify(mockSubmissionRepository).getById(submission._id)
                verify(mockSubmissionRepository).save(expectedSubmission)
                verify(mockCadxValidationErrorRepository, times(2)).saveBatch(captor.capture())
                verify(mockAuditService).audit(eqTo(expectedAudit))(using any(), any())
                verify(mockSubscriptionConnector).get(eqTo(dprsId))(using any())
                verify(mockPlatformOperatorConnector).get(eqTo(dprsId), eqTo(operator.operatorId))(using any())
                verify(mockEmailService, never()).sendSuccessfulBusinessRulesChecksEmails(any(), any(), any(), any())(any())

                val results = captor.getAllValues

                results.get(0) mustEqual (0 until 1000).map(_ => expectedFileError)
                results.get(1) mustEqual (0 until 200).flatMap(_ => Seq(expectedRowError1, expectedRowError2))
              }
            }

          }
        }
      }

      "when the submission is not in a submitted state" - {

        "must fail" in {

          val submission = Submission(
            _id = "submissionId",
            submissionType = SubmissionType.Xml,
            dprsId = "dprsId",
            operatorId = "operatorId",
            operatorName = "operatorName",
            assumingOperatorName = None,
            state = State.Ready,
            created = now.minus(1, ChronoUnit.DAYS),
            updated = now.minus(1, ChronoUnit.DAYS)
          )

          val approvedResponse = BREResponse_Type(
            requestCommon = RequestCommon_Type(
              receiptDate = scalaxb.Helper.toCalendar(DateTimeFormats.ISO8601Formatter.format(now.minus(1, ChronoUnit.HOURS))),
              regime = AEOI,
              conversationID = submission._id,
              schemaVersion = "1.0.0"
            ),
            requestDetail = RequestDetail_Type(
              GenericStatusMessage = GenericStatusMessage_Type(
                ValidationErrors = ValidationErrors_Type(
                  FileError = Seq.empty,
                  RecordError = Seq.empty
                ),
                ValidationResult = ValidationResult_Type(
                  Status = Accepted
                )
              )
            )
          )

          val source = Source.single {
            ByteString.fromString(Utility.trim(scalaxb.toXML(approvedResponse, "BREResponse", generated.defaultScope).head).toString)
          }

          when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission)))

          cadxResultService.processResult(source).failed.futureValue

          verify(mockSubmissionRepository).getById(submission._id)
          verify(mockSubmissionRepository, never()).save(any())
          verify(mockCadxValidationErrorRepository, never()).saveBatch(any())
        }
      }
    }

    "when there is no submission matching the conversationId" - {

      "must fail" in {

        val approvedResponse = BREResponse_Type(
          requestCommon = RequestCommon_Type(
            receiptDate = scalaxb.Helper.toCalendar(DateTimeFormats.ISO8601Formatter.format(now.minus(1, ChronoUnit.HOURS))),
            regime = AEOI,
            conversationID = "submissionId",
            schemaVersion = "1.0.0"
          ),
          requestDetail = RequestDetail_Type(
            GenericStatusMessage = GenericStatusMessage_Type(
              ValidationErrors = ValidationErrors_Type(
                FileError = Seq.empty,
                RecordError = Seq.empty
              ),
              ValidationResult = ValidationResult_Type(
                Status = Accepted
              )
            )
          )
        )

        val source = Source.single {
          ByteString.fromString(Utility.trim(scalaxb.toXML(approvedResponse, "BREResponse", generated.defaultScope).head).toString)
        }

        when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(None))
        when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
        when(mockCadxValidationErrorRepository.saveBatch(any())).thenReturn(Future.successful(Done))

        cadxResultService.processResult(source).failed.futureValue

        verify(mockSubmissionRepository, never()).save(any())
        verify(mockCadxValidationErrorRepository, never()).saveBatch(any())
      }
    }

    "when the conversationId cannot be parsed" - {

      "must fail" in {

        val source = Source.single {
          ByteString.fromString("<foo>bar</foo>")
        }

        when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(None))
        when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
        when(mockCadxValidationErrorRepository.saveBatch(any())).thenReturn(Future.successful(Done))

        cadxResultService.processResult(source).failed.futureValue

        verify(mockSubmissionRepository, never()).getById(any())
        verify(mockSubmissionRepository, never()).save(any())
        verify(mockCadxValidationErrorRepository, never()).saveBatch(any())
      }
    }
  }
}
