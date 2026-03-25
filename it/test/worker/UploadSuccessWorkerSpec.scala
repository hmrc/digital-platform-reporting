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

package worker

import models.submission.Submission.State.{UploadFailed, Uploading, Validated}
import models.submission.Submission.SubmissionType
import models.submission.Submission.UploadFailureReason.SchemaValidationError
import models.submission.{Submission, UploadSuccessWorkItem}
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repository.{SubmissionRepository, UploadSuccessWorkItemRepository}
import services.{AuditService, ValidationService}
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.mongo.MongoComponent

import java.time.{Clock, Instant}
import java.time.temporal.ChronoUnit
import java.time.Year
import scala.concurrent.Future
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import workers.UploadSuccessWorker

class UploadSuccessWorkerSpec
  extends AnyFreeSpec
    with Matchers
    with BeforeAndAfterEach
    with GuiceOneServerPerSuite
    with CleanMongoCollectionSupport
    with IntegrationPatience
    with OptionValues
    with MockitoSugar
    with Eventually {

  private val mockValidationService: ValidationService = mock[ValidationService]
  private val mockAuditService: AuditService = mock[AuditService]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[MongoComponent].toInstance(mongoComponent),
        bind[ValidationService].toInstance(mockValidationService),
        bind[AuditService].toInstance(mockAuditService),
        bind[Clock].toInstance(Clock.systemUTC())
      )
      .configure(
        "mongodb.upload-success.ttl" -> "5minutes",
        "upscan.upload-success.retry-after" -> "1s",
        "workers.upload-success.initial-delay" -> "1s",
        "workers.upload-success.interval" -> "1s"
      )
      .build()

  private val uploadSuccessWorkItemRepository =
    app.injector.instanceOf[UploadSuccessWorkItemRepository]

  private val submissionRepository =
    app.injector.instanceOf[SubmissionRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockValidationService, mockAuditService)
    uploadSuccessWorkItemRepository.initialised.futureValue
    app.injector.instanceOf[UploadSuccessWorker]
  }

  "must process waiting upload success work items and update submission to Validated" in {
    val now = Instant.now()
    val submissionId = "submission-1"
    val dprsId = "DPRSId"
    val operatorId = "operator-1"
    val operatorName = "Operator name"
    val fileName = "file.xml"

    val existingSubmission = Submission(
      _id = submissionId,
      submissionType = SubmissionType.Xml,
      dprsId = dprsId,
      operatorId = operatorId,
      operatorName = operatorName,
      assumingOperatorName = None,
      state = Uploading,
      created = now.minus(1, ChronoUnit.DAYS),
      updated = now.minus(1, ChronoUnit.DAYS)
    )

    submissionRepository.save(existingSubmission).futureValue

    val workItem = UploadSuccessWorkItem(
      dprsId = dprsId,
      submissionId = submissionId,
      fileName = fileName,
      downloadUrl = url"https://download-url",
      checksum = "checksum-123",
      size = 123L,
      receivedAt = now,
      requestId = Some("request-id-123")
    )

    when(
      mockValidationService.validateXml(
        eqTo(fileName),
        eqTo(dprsId),
        eqTo(url"https://download-url"),
        eqTo(operatorId)
      )
    ).thenReturn(Future.successful(Right(Year.of(2025))))

    uploadSuccessWorkItemRepository.pushNew(workItem).futureValue

    eventually {
      verify(mockValidationService).validateXml(
        eqTo(fileName),
        eqTo(dprsId),
        eqTo(url"https://download-url"),
        eqTo(operatorId)
      )

      val updatedSubmission = submissionRepository.get(dprsId, submissionId).futureValue.value

      updatedSubmission.state mustBe Validated(
        downloadUrl = url"https://download-url",
        reportingPeriod = Year.of(2025),
        fileName = fileName,
        checksum = "checksum-123",
        size = 123L
      )
    }
  }

  "must process waiting upload success work items and update submission to UploadFailed when XML validation fails" in {
    val now = Instant.now()
    val submissionId = "submission-2"
    val dprsId = "DPRS456"
    val operatorId = "operator-2"
    val operatorName = "Operator two"
    val fileName = "error-file.xml"

    val existingSubmission = Submission(
      _id = submissionId,
      submissionType = SubmissionType.Xml,
      dprsId = dprsId,
      operatorId = operatorId,
      operatorName = operatorName,
      assumingOperatorName = None,
      state = Uploading,
      created = now.minus(1, ChronoUnit.DAYS),
      updated = now.minus(1, ChronoUnit.DAYS)
    )

    submissionRepository.save(existingSubmission).futureValue

    val validationFailure = SchemaValidationError(Seq.empty, false)

    val workItem = UploadSuccessWorkItem(
      dprsId = dprsId,
      submissionId = submissionId,
      fileName = fileName,
      downloadUrl = url"https://download-url/error",
      checksum = "checksum-error",
      size = 999L,
      receivedAt = now,
      requestId = Some("request-id-123")
    )

    when(
      mockValidationService.validateXml(
        eqTo(fileName),
        eqTo(dprsId),
        eqTo(url"https://download-url/error"),
        eqTo(operatorId)
      )
    ).thenReturn(Future.successful(Left(validationFailure)))

    uploadSuccessWorkItemRepository.pushNew(workItem).futureValue

    eventually {
      verify(mockValidationService).validateXml(
        eqTo(fileName),
        eqTo(dprsId),
        eqTo(url"https://download-url/error"),
        eqTo(operatorId)
      )

      val updatedSubmission = submissionRepository.get(dprsId, submissionId).futureValue.value

      updatedSubmission.state mustBe UploadFailed(validationFailure, Some(fileName))
    }
  }
}