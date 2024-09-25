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

import models.sdes.{NotificationCallback, NotificationType}
import models.submission.{CadxValidationError, Submission}
import models.submission.Submission.State.{Approved, Ready, Rejected, Submitted, UploadFailed, Uploading, Validated}
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{never, verify, when}
import org.scalacheck.Gen
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
import services.SdesService
import uk.gov.hmrc.http.StringContextOps

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, Year, ZoneOffset}
import scala.concurrent.Future

class SdesSubmissionCallbackControllerSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach {

  private val now = Instant.now()
  private val clock = Clock.fixed(now, ZoneOffset.UTC)

  private val mockSdesService = mock[SdesService]
  private val mockSubmissionRepository = mock[SubmissionRepository]
  private val mockCadxValidationErrorRepository = mock[CadxValidationErrorRepository]

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(
      bind[SdesService].toInstance(mockSdesService),
      bind[SubmissionRepository].toInstance(mockSubmissionRepository),
      bind[CadxValidationErrorRepository].toInstance(mockCadxValidationErrorRepository),
      bind[Clock].toInstance(clock)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockSdesService, mockSubmissionRepository)
  }

  private val readyGen: Gen[Ready.type] = Gen.const(Ready)
  private val uploadingGen: Gen[Uploading.type] = Gen.const(Uploading)
  private val uploadFailedGen: Gen[UploadFailed] = Gen.asciiPrintableStr.map(UploadFailed.apply)
  private val validatedGen: Gen[Validated] = Gen.const(Validated(url"http://example.com", "poid", Year.of(2024), "test.xml", "checksum", 1337L))
  private val approvedGen: Gen[Approved.type] = Gen.const(Approved)
  private val rejectedGen: Gen[Rejected.type] = Gen.const(Rejected)

  "callback" - {

    val submissionId = "submissionId"

    "when the notification is a failure" - {

      val notificationCallback = NotificationCallback(
        notification = NotificationType.FileProcessingFailure,
        filename = "test.xml",
        correlationID = submissionId,
        failureReason = Some("reason")
      )

      "when the submission is in a submitted state" - {

        val submission = Submission(
          _id = submissionId,
          dprsId = "dprsId",
          state = Submitted,
          created = now.minus(1, ChronoUnit.DAYS),
          updated = now.minus(1, ChronoUnit.DAYS)
        )

        "must update the submission state to rejected, save the rejected reason and return OK" in {

          val request = FakeRequest(routes.SdesSubmissionCallbackController.callback())
            .withBody(Json.toJson(notificationCallback))

          val expectedSubmission = submission.copy(
            state = Rejected,
            updated = now
          )

          val expectedValidationError = CadxValidationError.FileError(
            submissionId = submissionId, code = "MDTP1", detail = None, created = now
          )

          when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission)))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))
          when(mockCadxValidationErrorRepository.save(any())).thenReturn(Future.successful(Done))

          val result = route(app, request).value

          status(result) mustBe OK

          verify(mockSubmissionRepository).getById(submissionId)
          verify(mockSubmissionRepository).save(expectedSubmission)
          verify(mockCadxValidationErrorRepository).save(expectedValidationError)
        }
      }

      "when there is no submission" - {

        "must return OK" in {

          val request = FakeRequest(routes.SdesSubmissionCallbackController.callback())
            .withBody(Json.toJson(notificationCallback))

          when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(None))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

          val result = route(app, request).value

          status(result) mustBe OK

          verify(mockSubmissionRepository).getById(submissionId)
          verify(mockSubmissionRepository, never()).save(any())
        }
      }

      "when the submission is in any other state" - {

        val state = Gen.oneOf(readyGen, uploadingGen, uploadFailedGen, validatedGen, approvedGen, rejectedGen).sample.value
        val submission = Submission(
          _id = submissionId,
          dprsId = "dprsId",
          state = state,
          created = now,
          updated = now
        )

        "must return OK" in {

          val request = FakeRequest(routes.SdesSubmissionCallbackController.callback())
            .withBody(Json.toJson(notificationCallback))

          when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(Some(submission)))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

          val result = route(app, request).value

          status(result) mustBe OK

          verify(mockSubmissionRepository).getById(submissionId)
          verify(mockSubmissionRepository, never()).save(any())
        }
      }
    }

    "when the notification is any other status" - {

      val notification = Gen.oneOf(NotificationType.FileReady, NotificationType.FileReceived, NotificationType.FileProcessed).sample.value
      val notificationCallback = NotificationCallback(
        notification = notification,
        filename = "test.xml",
        correlationID = submissionId,
        failureReason = Some("reason")
      )

      "must not update the submission state and return OK" in {

        val request = FakeRequest(routes.SdesSubmissionCallbackController.callback())
          .withBody(Json.toJson(notificationCallback))

        when(mockSubmissionRepository.getById(any())).thenReturn(Future.successful(None))
        when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

        val result = route(app, request).value

        status(result) mustBe OK

        verify(mockSubmissionRepository, never()).getById(any())
        verify(mockSubmissionRepository, never()).save(any())
      }
    }
  }
}
