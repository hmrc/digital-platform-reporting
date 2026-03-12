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

import models.submission.Submission.State.{Ready, Submitted, UploadFailed, Uploading}
import models.submission.Submission.{SubmissionType, UploadFailureReason}
import models.submission.{Submission, UploadSuccessRequest}
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repository.SubmissionRepository
import services.{UploadSuccessService, ValidationService}
import uk.gov.hmrc.http.StringContextOps
import controllers.SubmissionController
import services.UuidService
import services.SubmissionService
import services.ViewSubmissionsService
import controllers.actions.AuthAction
import services.AuditService
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.Future
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{Materializer, SystemMaterializer}
import org.scalatest.BeforeAndAfterAll

class UploadControllerSpec
  extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with GuiceOneAppPerSuite
    with BeforeAndAfterAll {

    private val instant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
    private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

    implicit val actorSystem: ActorSystem = ActorSystem("SubmissionControllerSpec")
    implicit val materializer: Materializer =
      SystemMaterializer(actorSystem).materializer

    override def afterAll(): Unit = {
      actorSystem.terminate()
      super.afterAll()
    }


    private val submissionRepository = mock[SubmissionRepository]
    private val uploadSuccessService = mock[UploadSuccessService]
    private val validationService = mock[ValidationService]
    private val uuidService = mock[UuidService]
    private val submissionService = mock[SubmissionService]
    private val viewSubmissionsService = mock[ViewSubmissionsService]
    private val auditService = mock[AuditService]
    private val auth = mock[AuthAction]
    private val clock = java.time.Clock.systemUTC()

    private val dprsId = "dprsId"
    private val operatorId = "operatorId"
    private val operatorName = "operatorName"
    private val uuid = UUID.randomUUID().toString

    private val created = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    private val updated = created.plus(1, ChronoUnit.DAYS)

    private val requestBody = UploadSuccessRequest(
      dprsId = dprsId,
      fileName = "file.xml",
      downloadUrl = url"https://download-url",
      checksum = "checksum-123",
      size = 123L
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

    private val controller = new SubmissionController(
      cc = stubControllerComponents(),
      uuidService = uuidService,
      clock = clock,
      submissionRepository = submissionRepository,
      auth = auth,
      submissionService = submissionService,
      viewSubmissionsService = viewSubmissionsService,
      uploadSuccessService = uploadSuccessService,
      auditService = auditService
    )

    "uploadSuccess" should {

      "return 404 when submission is not found" in {
        when(submissionRepository.get(dprsId, "submission-1"))
          .thenReturn(Future.successful(None))

        val request = FakeRequest(POST, "/")
          .withBody(Json.toJson(requestBody))
          .withHeaders(CONTENT_TYPE -> "application/json")

        val result = call(controller.uploadSuccess("submission-1"), request)

        status(result) mustBe NOT_FOUND

        verify(uploadSuccessService, never()).enqueueUploadSuccess(any[String], any[UploadSuccessRequest])
      }

      "return 200 and enqueue work item when state is Ready" in {
        when(submissionRepository.get("DPRS123", "submission-1"))
          .thenReturn(Future.successful(Some(submission.copy(state = Ready))))
        when(uploadSuccessService.enqueueUploadSuccess("submission-1", requestBody))
          .thenReturn(Future.successful(Done))

        val result = controller.uploadSuccess("submission-1")(
          FakeRequest(POST, "/")
            .withBody(Json.toJson(requestBody))
            .withHeaders(CONTENT_TYPE -> "application/json")
        )

        status(result) mustBe OK

        verify(uploadSuccessService).enqueueUploadSuccess("submission-1", requestBody)
        verify(validationService, never()).validateXml(any[String], any[String], any[URL], any[String])
      }

      "return 200 and enqueue work item when state is Uploading" in {
        when(submissionRepository.get("DPRS123", "submission-1"))
          .thenReturn(Future.successful(Some(submission.copy(state = Uploading))))
        when(uploadSuccessService.enqueueUploadSuccess("submission-1", requestBody))
          .thenReturn(Future.successful(Done))

        val result = controller.uploadSuccess("submission-1")(
          FakeRequest(POST, "/")
            .withBody(Json.toJson(requestBody))
            .withHeaders(CONTENT_TYPE -> "application/json")
        )

        status(result) mustBe OK

        verify(uploadSuccessService).enqueueUploadSuccess("submission-1", requestBody)
        verify(validationService, never()).validateXml(any[String], any[String], any[URL], any[String])
      }

      "return 200 and enqueue work item when state is UploadFailed" in {
        val failedSubmission =
          submission.copy(state = UploadFailed(UploadFailureReason.FileNameTooLong, Some("old-file.xml")))

        when(submissionRepository.get("DPRS123", "submission-1"))
          .thenReturn(Future.successful(Some(failedSubmission)))
        when(uploadSuccessService.enqueueUploadSuccess("submission-1", requestBody))
          .thenReturn(Future.successful(Done))

        val result = controller.uploadSuccess("submission-1")(
          FakeRequest(POST, "/")
            .withBody(Json.toJson(requestBody))
            .withHeaders(CONTENT_TYPE -> "application/json")
        )

        status(result) mustBe OK

        verify(uploadSuccessService).enqueueUploadSuccess("submission-1", requestBody)
        verify(validationService, never()).validateXml(any[String], any[String], any[URL], any[String])
      }

      "propagate failure if enqueue fails" in {
        when(submissionRepository.get("DPRS123", "submission-1"))
          .thenReturn(Future.successful(Some(submission)))
        when(uploadSuccessService.enqueueUploadSuccess("submission-1", requestBody))
          .thenReturn(Future.failed(new RuntimeException("mongo down")))

        val result = controller.uploadSuccess("submission-1")(
          FakeRequest(POST, "/")
            .withBody(Json.toJson(requestBody))
            .withHeaders(CONTENT_TYPE -> "application/json")
        )

        status(result) mustBe INTERNAL_SERVER_ERROR

        verify(uploadSuccessService).enqueueUploadSuccess("submission-1", requestBody)
        verify(validationService, never()).validateXml(any[String], any[String], any[URL], any[String])
      }

      "not call submission save from controller" in {
        when(submissionRepository.get("DPRS123", "submission-1"))
          .thenReturn(Future.successful(Some(submission.copy(state = Ready))))
        when(uploadSuccessService.enqueueUploadSuccess("submission-1", requestBody))
          .thenReturn(Future.successful(Done))

        val result = controller.uploadSuccess("submission-1")(
          FakeRequest(POST, "/")
            .withBody(Json.toJson(requestBody))
            .withHeaders(CONTENT_TYPE -> "application/json")
        )

        status(result) mustBe OK

        verify(submissionRepository, never()).save(any[Submission])
      }
    }
}
