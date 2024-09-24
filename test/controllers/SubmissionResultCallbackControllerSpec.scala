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

package controllers

import generated.{AEOI, Accepted, BREResponse_Type, ErrorDetail_Type, FileError_Type, Generated_BREResponse_TypeFormat, GenericStatusMessage_Type, RecordError_Type, Rejected, RequestCommon_Type, RequestDetail_Type, ValidationErrors_Type, ValidationResult_Type}
import models.submission.Submission
import models.submission.Submission.State
import models.submission.Submission.State.{Approved, Ready, Submitted}
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repository.SubmissionRepository
import utils.DateTimeFormats

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.Future

class SubmissionResultCallbackControllerSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach {

  private val now = Instant.now()
  private val clock = Clock.fixed(now, ZoneOffset.UTC)

  private val mockSubmissionRepository = mock[SubmissionRepository]

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .configure(
      "cadx.incoming-bearer-token" -> "token"
    )
    .overrides(
      bind[SubmissionRepository].toInstance(mockSubmissionRepository),
      bind[Clock].toInstance(clock)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockSubmissionRepository)
  }

  "callback" - {

    val conversationId = "conversationId"
    val dprsId = "dprsId"

    val approvedRequest = BREResponse_Type(
      requestCommon = RequestCommon_Type(
        receiptDate = scalaxb.Helper.toCalendar(DateTimeFormats.ISO8601Formatter.format(now)),
        regime = AEOI,
        conversationID = conversationId,
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

    "when there is a submission matching the correlationId" - {

      val correlationId = "correlationId"

      "when there is a submission in a submitted state" - {

        val submission = Submission(
          _id = conversationId,
          dprsId = dprsId,
          state = Submitted,
          created = now.minus(1, ChronoUnit.DAYS),
          updated = now.minus(1, ChronoUnit.DAYS)
        )

        "when the request indicates the submission was successful" - {

          "must update the submission to Approved and return NO_CONTENT" in {

            val request = FakeRequest(routes.SubmissionResultCallbackController.callback())
              .withHeaders(
                "Authorization" -> "Bearer token",
                "X-Correlation-Id" -> correlationId,
                "X-Conversation-Id" -> conversationId,
                "Content-Type" -> "application/xml;charset=UTF-8"
              )
              .withBody(scalaxb.toXML(approvedRequest, "BREResponse", generated.defaultScope))

            val expectedSubmission = submission.copy(state = Approved, updated = now)

            when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission)))
            when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

            val result = route(app, request).value

            status(result) mustEqual NO_CONTENT

            verify(mockSubmissionRepository).getById(conversationId)
            verify(mockSubmissionRepository).save(expectedSubmission)
          }
        }

        "when the request indicates the submission was rejected" - {

          val requestBody = BREResponse_Type(
            requestCommon = RequestCommon_Type(
              receiptDate = scalaxb.Helper.toCalendar(DateTimeFormats.ISO8601Formatter.format(now)),
              regime = AEOI,
              conversationID = conversationId,
              schemaVersion = "1.0.0"
            ),
            requestDetail = RequestDetail_Type(
              GenericStatusMessage = GenericStatusMessage_Type(
                ValidationErrors = ValidationErrors_Type(
                  FileError = Seq(FileError_Type(
                    Code = "001",
                    Details = Some(ErrorDetail_Type("detail"))
                  )),
                  RecordError = Seq(RecordError_Type(
                    Code = "002",
                    Details = Some(ErrorDetail_Type("detail 2")),
                    DocRefIDInError = Seq("1", "2")
                  ))
                ),
                ValidationResult = ValidationResult_Type(
                  Status = Rejected
                )
              )
            )
          )

          "must update the submission to Rejected, add the failures, and return NO_CONTENT" in {

            val request = FakeRequest(routes.SubmissionResultCallbackController.callback())
              .withHeaders(
                "Authorization" -> "Bearer token",
                "X-Correlation-Id" -> correlationId,
                "X-Conversation-Id" -> conversationId,
                "Content-Type" -> "application/xml;charset=UTF-8"
              )
              .withBody(scalaxb.toXML(requestBody, "BREResponse", generated.defaultScope))

            val expectedSubmission = submission.copy(state = State.Rejected("reason"), updated = now)

            when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission)))
            when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

            // TODO add failures to repository

            val result = route(app, request).value

            status(result) mustEqual NO_CONTENT

            verify(mockSubmissionRepository).getById(conversationId)
            verify(mockSubmissionRepository).save(expectedSubmission)
          }
        }
      }

      "when the submission is not in a submitted state" - {

        val submission = Submission(
          _id = conversationId,
          dprsId = dprsId,
          state = Ready,
          created = now.minus(1, ChronoUnit.DAYS),
          updated = now.minus(1, ChronoUnit.DAYS)
        )

        "must return NOT_FOUND" in {

          val request = FakeRequest(routes.SubmissionResultCallbackController.callback())
            .withHeaders(
              "Authorization" -> "Bearer token",
              "X-Correlation-Id" -> correlationId,
              "X-Conversation-Id" -> conversationId,
              "Content-Type" -> "application/xml;charset=UTF-8"
            )
            .withBody(scalaxb.toXML(approvedRequest, "BREResponse", generated.defaultScope))

          val expectedSubmission = submission.copy(state = State.Rejected("reason"), updated = now)

          when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission)))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.failed(new RuntimeException()))

          val result = route(app, request).value

          status(result) mustEqual NOT_FOUND

          verify(mockSubmissionRepository).getById(conversationId)
          verify(mockSubmissionRepository, never()).save(expectedSubmission)
        }
      }

      "when the conversation id is missing" - {

        "must return BAD_REQUEST" in {

          val request = FakeRequest(routes.SubmissionResultCallbackController.callback())
            .withHeaders(
              "Authorization" -> "Bearer token",
              "X-Correlation-Id" -> correlationId,
              "Content-Type" -> "application/xml;charset=UTF-8"
            )
            .withBody(scalaxb.toXML(approvedRequest, "BREResponse", generated.defaultScope))

          when(mockSubmissionRepository.getById(any())).thenReturn(Future.failed(new RuntimeException()))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.failed(new RuntimeException()))

          val result = route(app, request).value

          status(result) mustEqual BAD_REQUEST

          verify(mockSubmissionRepository, never()).getById(any())
          verify(mockSubmissionRepository, never()).save(any())
        }
      }

      "when the body is not XML" - {

        "must return UNSUPPORTED_MEDIA_TYPE" in {

          val request = FakeRequest(routes.SubmissionResultCallbackController.callback())
            .withHeaders(
              "Authorization" -> "Bearer token",
              "X-Correlation-Id" -> correlationId,
              "X-Conversation-Id" -> conversationId
            )
            .withBody(Json.obj())

          when(mockSubmissionRepository.getById(any())).thenReturn(Future.failed(new RuntimeException()))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.failed(new RuntimeException()))

          val result = route(app, request).value

          status(result) mustEqual UNSUPPORTED_MEDIA_TYPE

          verify(mockSubmissionRepository, never()).getById(any())
          verify(mockSubmissionRepository, never()).save(any())
        }
      }

      "when the body is invalid XML" - {

        "must return BAD_REQUEST" in {

          val request = FakeRequest(routes.SubmissionResultCallbackController.callback())
            .withHeaders(
              "Authorization" -> "Bearer token",
              "X-Correlation-Id" -> correlationId,
              "X-Conversation-Id" -> conversationId,
              "Content-Type" -> "application/xml"
            )
            .withBody(Json.obj())

          when(mockSubmissionRepository.getById(any())).thenReturn(Future.failed(new RuntimeException()))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.failed(new RuntimeException()))

          val result = route(app, request).value

          status(result) mustEqual BAD_REQUEST

          verify(mockSubmissionRepository, never()).getById(any())
          verify(mockSubmissionRepository, never()).save(any())
        }
      }

      "when the body is not valid" - {

        "must return BAD_REQUEST" in {

          val request = FakeRequest(routes.SubmissionResultCallbackController.callback())
            .withHeaders(
              "Authorization" -> "Bearer token",
              "X-Correlation-Id" -> correlationId,
              "X-Conversation-Id" -> conversationId,
              "Content-Type" -> "application/xml"
            )
            .withXmlBody(<foo></foo>)

          when(mockSubmissionRepository.getById(any())).thenReturn(Future.failed(new RuntimeException()))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.failed(new RuntimeException()))

          val result = route(app, request).value

          status(result) mustEqual BAD_REQUEST

          verify(mockSubmissionRepository, never()).getById(any())
          verify(mockSubmissionRepository, never()).save(any())
        }
      }
    }

    "when the correlation id is missing" - {

      "must return BAD_REQUEST" in {

        val request = FakeRequest(routes.SubmissionResultCallbackController.callback())
          .withHeaders(
            "Authorization" -> "Bearer token",
            "X-Conversation-Id" -> conversationId,
            "Content-Type" -> "application/xml"
          )
          .withBody(scalaxb.toXML(approvedRequest, "BREResponse", generated.defaultScope))

        when(mockSubmissionRepository.getById(any())).thenReturn(Future.failed(new RuntimeException()))
        when(mockSubmissionRepository.save(any())).thenReturn(Future.failed(new RuntimeException()))

        val result = route(app, request).value

        status(result) mustEqual BAD_REQUEST

        verify(mockSubmissionRepository, never()).getById(any())
        verify(mockSubmissionRepository, never()).save(any())
      }
    }
  }
}