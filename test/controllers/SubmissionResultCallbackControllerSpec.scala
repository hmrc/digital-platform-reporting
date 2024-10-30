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
import models.submission.Submission.{State, SubmissionType}
import models.submission.Submission.State.{Approved, Ready, Submitted}
import models.submission.{CadxValidationError, Submission}
import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, Mockito}
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
import repository.{CadxValidationErrorRepository, SubmissionRepository}
import services.CadxResultService
import utils.DateTimeFormats

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, Year, ZoneOffset}
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

  private val mockCadxResultService = mock[CadxResultService]

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .configure(
      "cadx.incoming-bearer-token" -> "token"
    )
    .overrides(
      bind[CadxResultService].toInstance(mockCadxResultService),
      bind[Clock].toInstance(clock)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockCadxResultService)
  }

  private val conversationId = "conversationId"

  private val approvedRequest = BREResponse_Type(
    requestCommon = RequestCommon_Type(
      receiptDate = scalaxb.Helper.toCalendar(DateTimeFormats.ISO8601Formatter.format(now.minus(1, ChronoUnit.HOURS))),
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

  "callback" - {

    "when there is a valid auth token" - {

      "when there is a submission matching the conversationId" - {

        val correlationId = "correlationId"

        "when the request indicates the submission was successful" - {

          "must update the submission to Approved and return NO_CONTENT" in {

            val requestBody = scalaxb.toXML(approvedRequest, "BREResponse", generated.defaultScope)

            val request = FakeRequest(routes.SubmissionResultCallbackController.callback())
              .withHeaders(
                "Authorization" -> "Bearer token",
                "X-Correlation-Id" -> correlationId,
                "X-Conversation-Id" -> conversationId,
                "Content-Type" -> "application/xml;charset=UTF-8"
              )
              .withBody(requestBody)

            when(mockCadxResultService.processResult(any())).thenReturn(Future.successful(Done))

            val result = route(app, request).value

            status(result) mustEqual NO_CONTENT

            val captor: ArgumentCaptor[Source[ByteString, ?]] = ArgumentCaptor.forClass(classOf[Source[ByteString, ?]])
            verify(mockCadxResultService).processResult(captor.capture())

            given Materializer = app.materializer
            val receivedBody = captor.getValue.runWith(Sink.fold(ByteString.empty)(_ ++ _)).futureValue.utf8String

            receivedBody mustEqual requestBody.toString
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

            val result = route(app, request).value

            status(result) mustEqual BAD_REQUEST

            verify(mockCadxResultService, never()).processResult(any())
          }
        }
      }
    }

    "when the conversation id is missing" - {

      "must return BAD_REQUEST" in {

        val request = FakeRequest(routes.SubmissionResultCallbackController.callback())
          .withHeaders(
            "Authorization" -> "Bearer token",
            "X-Conversation-Id" -> conversationId,
            "Content-Type" -> "application/xml"
          )
          .withBody(scalaxb.toXML(approvedRequest, "BREResponse", generated.defaultScope))

        val result = route(app, request).value

        status(result) mustEqual BAD_REQUEST

        verify(mockCadxResultService, never()).processResult(any())
      }
    }
  }

  "when there is an invalid auth token" - {

    "must return FORBIDDEN" in {

      val request = FakeRequest(routes.SubmissionResultCallbackController.callback())
        .withHeaders(
          "Authorization" -> "Bearer notValidToken",
          "X-Conversation-Id" -> "conversationId",
          "Content-Type" -> "application/xml"
        )
        .withBody(scalaxb.toXML(approvedRequest, "BREResponse", generated.defaultScope))

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN
    }
  }

  "when there is no auth token" - {

    "must return FORBIDDEN" in {

      val request = FakeRequest(routes.SubmissionResultCallbackController.callback())
        .withHeaders(
          "X-Conversation-Id" -> "conversationId",
          "Content-Type" -> "application/xml"
        )
        .withBody(scalaxb.toXML(approvedRequest, "BREResponse", generated.defaultScope))

      val result = route(app, request).value

      status(result) mustEqual FORBIDDEN
    }
  }
}
