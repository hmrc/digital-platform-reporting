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

import generated.{AEOI, Accepted, BREResponse_Type, ErrorDetail_Type, FileError_Type, Generated_BREResponse_TypeFormat, GenericStatusMessage_Type, RecordError_Type, Rejected, RequestCommon_Type, RequestDetail_Type, ValidationErrors_Type, ValidationResult_Type}
import models.submission.Submission.State.Submitted
import models.submission.Submission.{State, SubmissionType}
import models.submission.{CadxValidationError, Submission}
import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito.{atLeastOnce, never, times, verify, when}
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
import utils.DateTimeFormats

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
      mockCadxValidationErrorRepository
    )
  }

  private val mockSubmissionRepository: SubmissionRepository = mock[SubmissionRepository]
  private val mockCadxValidationErrorRepository: CadxValidationErrorRepository = mock[CadxValidationErrorRepository]
  private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(
      bind[SubmissionRepository].toInstance(mockSubmissionRepository),
      bind[CadxValidationErrorRepository].toInstance(mockCadxValidationErrorRepository),
      bind[Clock].toInstance(clock)
    )
    .build()

  private lazy val cadxResultService: CadxResultService = app.injector.instanceOf[CadxResultService]

  private val now = clock.instant()

  "processResult" - {

    "when there is a submission matching the conversationId" - {

      "when the submission is in a submitted state" - {

        "when the response indicates that the submission was approved" - {

          "must update the submission" in {

            val submission = Submission(
              _id = "submissionId",
              submissionType = SubmissionType.Xml,
              dprsId = "dprsId",
              operatorId = "operatorId",
              operatorName = "operatorName",
              assumingOperatorName = None,
              state = State.Submitted(
                fileName = "test.xml",
                reportingPeriod = Year.of(2024)
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
            when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
            when(mockCadxValidationErrorRepository.saveBatch(any())).thenReturn(Future.successful(Done))

            cadxResultService.processResult(source).futureValue

            verify(mockSubmissionRepository).getById(submission._id)
            verify(mockSubmissionRepository).save(expectedSubmission)
            verify(mockCadxValidationErrorRepository, never()).saveBatch(any())
          }
        }

        "when the response indicates that the submission was rejected" - {

          "must update the submission" in {

            val submission = Submission(
              _id = "submissionId",
              submissionType = SubmissionType.Xml,
              dprsId = "dprsId",
              operatorId = "operatorId",
              operatorName = "operatorName",
              assumingOperatorName = None,
              state = State.Submitted(
                fileName = "test.xml",
                reportingPeriod = Year.of(2024)
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

            val source = Source.single {
              ByteString.fromString(Utility.trim(scalaxb.toXML(rejectedResponse, "BREResponse", generated.defaultScope).head).toString)
            }

            when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission)))
            when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
            when(mockCadxValidationErrorRepository.saveBatch(any())).thenReturn(Future.successful(Done))

            cadxResultService.processResult(source).futureValue

            val captor: ArgumentCaptor[Seq[CadxValidationError]] = ArgumentCaptor.forClass(classOf[Seq[CadxValidationError]])

            verify(mockSubmissionRepository).getById(submission._id)
            verify(mockSubmissionRepository).save(expectedSubmission)
            verify(mockCadxValidationErrorRepository, times(2)).saveBatch(captor.capture())

            val results = captor.getAllValues

            results.get(0) mustEqual (0 until 1000).map(_ => expectedFileError)
            results.get(1) mustEqual (0 until 200).flatMap(_ => Seq(expectedRowError1, expectedRowError2))
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
