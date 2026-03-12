/*
 * Copyright 2026 HM Revenue & Customs
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
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.test.FakeRequest
import controllers.actions.{AuthAction, FakeAuthAction}
import services.UuidService
import repository.SubmissionRepository
import services.{AuditService, SubmissionService, UploadSuccessService, ViewSubmissionsService}
import models.submission.{Submission, UploadSuccessRequest}
import models.submission.Submission.State.{Ready, Submitted, UploadFailed, Uploading, *}
import models.submission.Submission.UploadFailureReason.SchemaValidationError
import models.submission.Submission.{SubmissionType, UploadFailureReason}

import java.time.temporal.ChronoUnit
import uk.gov.hmrc.http.StringContextOps
import org.apache.pekko.Done
import org.mockito.Mockito
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach

import java.time.{Clock, Instant, Year, ZoneOffset}
import scala.concurrent.Future

class UploadControllerSpec
  extends PlaySpec with BeforeAndAfterEach
    with MockitoSugar {

  private val submissionRepository = mock[SubmissionRepository]
  private val uploadSuccessService = mock[UploadSuccessService]
  private val uuidService = mock[UuidService]
  private val submissionService = mock[SubmissionService]
  private val viewSubmissionsService = mock[ViewSubmissionsService]
  private val auditService = mock[AuditService]

  private val clock = Clock.fixed(Instant.parse("2026-03-12T10:00:00Z"), ZoneOffset.UTC)

  private val dprsId = "dprsId"
  private val fileName = "test.xml"
  private val created = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  private val updated = created.plus(1, ChronoUnit.DAYS)
  private val validatedGen: Gen[Validated] = Gen.const(Validated(url"http://example.com", Year.of(2024), "test.xml", "checksum", 1337L))
  private val submittedGen: Gen[Submitted] = Gen.const(Submitted("test.xml", Year.of(2024), 8576L))
  private val approvedGen: Gen[Approved] = Gen.const(Approved("test.xml", Year.of(2024)))
  private val rejectedGen: Gen[Rejected] = Gen.const(Rejected("test.xml", Year.of(2024)))

  private val requestBody = UploadSuccessRequest(
    dprsId = dprsId,
    downloadUrl = url"https://download-url",
    fileName = "file.xml",
    checksum = "checksum-123",
    size = 123
  )

  private val payload = Json.obj(
    "dprsId" -> "dprsId",
    "downloadUrl" ->  "https://download-url",
    "fileName" -> "file.xml",
    "checksum" -> "checksum-123",
    "size" -> 123
  )

  private val submission = Submission(
    _id = "id",
    submissionType = SubmissionType.Xml,
    dprsId = "dprsId",
    operatorId = "operatorId",
    operatorName = "operatorName",
    assumingOperatorName = None,
    state = Ready,
    created = created,
    updated = updated
  )

  private def application: Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[SubmissionRepository].toInstance(submissionRepository),
        bind[UploadSuccessService].toInstance(uploadSuccessService),
        bind[UuidService].toInstance(uuidService),
        bind[SubmissionService].toInstance(submissionService),
        bind[ViewSubmissionsService].toInstance(viewSubmissionsService),
        bind[AuditService].toInstance(auditService),
        bind[Clock].toInstance(clock),
        bind[AuthAction].toInstance(new FakeAuthAction)
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      submissionRepository,
      uploadSuccessService,
      uuidService,
      submissionService,
      viewSubmissionsService,
      auditService
    )
  }


  "SubmissionController.uploadSuccess" must {

    "return NOT_FOUND when submission is not found" in {
      when(submissionRepository.get(dprsId, "submission-1"))
        .thenReturn(Future.successful(None))

      running(application) {
        val request = FakeRequest(routes.SubmissionController.uploadSuccess("submission-1"))
          .withJsonBody(payload)

        val result = route(application, request).value

        status(result) mustBe NOT_FOUND

        verify(uploadSuccessService, never()).enqueueUploadSuccess(any[String], any[UploadSuccessRequest])
      }
    }

    "return CONFLICT when submission state is not allowed" in {
      val state = Gen.oneOf(validatedGen, submittedGen, approvedGen, rejectedGen).sample.value

      val invalidStateSubmission = submission.copy(
        state = state
      )

      when(submissionRepository.get(dprsId, "submission-1"))
        .thenReturn(Future.successful(Some(invalidStateSubmission)))

      running(application) {
        val request = FakeRequest(routes.SubmissionController.uploadSuccess("submission-1"))
          .withJsonBody(payload)

        val result = route(application, request).value

        status(result) mustBe CONFLICT

        verify(uploadSuccessService, never()).enqueueUploadSuccess(any[String], any[UploadSuccessRequest])
      }
    }

    "return OK and enqueue work item when submission state is Ready" in {
      when(submissionRepository.get(dprsId, "submission-1"))
        .thenReturn(Future.successful(Some(submission.copy(state = Ready))))
      when(uploadSuccessService.enqueueUploadSuccess(eqTo("submission-1"), eqTo(requestBody)))
        .thenReturn(Future.successful(Done))

      running(application) {
        val request = FakeRequest(routes.SubmissionController.uploadSuccess("submission-1"))
          .withJsonBody(payload)

        val result = route(application, request).value

        status(result) mustBe OK

        verify(uploadSuccessService, times(1)).enqueueUploadSuccess(eqTo("submission-1"), eqTo(requestBody))
      }
    }

    "return OK and enqueue work item when submission state is Uploading" in {
      when(submissionRepository.get(dprsId, "submission-1"))
        .thenReturn(Future.successful(Some(submission.copy(state = Uploading))))
      when(uploadSuccessService.enqueueUploadSuccess(eqTo("submission-1"), eqTo(requestBody)))
        .thenReturn(Future.successful(Done))

      running(application) {
        val request = FakeRequest(routes.SubmissionController.uploadSuccess("submission-1"))
          .withJsonBody(payload)

        val result = route(application, request).value

        status(result) mustBe OK

        verify(uploadSuccessService, times(1)).enqueueUploadSuccess(eqTo("submission-1"), eqTo(requestBody))
      }
    }

    "return OK and enqueue work item when submission state is UploadFailed" in {

      val failedSubmission = submission.copy(
        state = UploadFailed(SchemaValidationError(Seq.empty, false), Some(fileName))
      )

      when(submissionRepository.get(dprsId, "submission-1"))
        .thenReturn(Future.successful(Some(failedSubmission)))
      when(uploadSuccessService.enqueueUploadSuccess(eqTo("submission-1"), eqTo(requestBody)))
        .thenReturn(Future.successful(Done))

      running(application) {
        val request = FakeRequest(routes.SubmissionController.uploadSuccess("submission-1"))
          .withJsonBody(payload)

        val result = route(application, request).value

        status(result) mustBe OK

        verify(uploadSuccessService, times(1)).enqueueUploadSuccess(eqTo("submission-1"), eqTo(requestBody))
      }
    }
  }
}