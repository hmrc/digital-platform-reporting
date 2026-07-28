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

package services

import play.api.Configuration
import uk.gov.hmrc.http.{HeaderCarrier, RequestId}
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import repository.{SubmissionRepository, UploadSuccessWorkItemRepository}
import models.audit.FileUploadedEvent
import models.audit.FileUploadedEvent.FileUploadOutcome
import models.submission.Submission.State.{Approved, Ready, Rejected, Submitted, UploadFailed, Uploading, Validated}
import models.submission.{Submission, UploadSuccessRequest, UploadSuccessWorkItem}
import org.apache.pekko.Done

import java.time.{Clock, Duration}
import java.time.Instant
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UploadSuccessService @Inject()(
                                      clock: Clock,
                                      workItemRepository: UploadSuccessWorkItemRepository,
                                      submissionRepository: SubmissionRepository,
                                      validationService: ValidationService,
                                      auditService: AuditService,
                                      configuration: Configuration
                                    )(using ExecutionContext) {

  private val retryTimeout: Duration =
    configuration.get[Duration]("upscan.upload-success.retry-after")

  def enqueueUploadSuccess(
                            submissionId: String,
                            request: UploadSuccessRequest
                          )(using hc:HeaderCarrier): Future[Done] = {
    val workItem = UploadSuccessWorkItem(
      dprsId = request.dprsId,
      submissionId = submissionId,
      fileName = request.fileName,
      downloadUrl = request.downloadUrl,
      checksum = request.checksum,
      size = request.size,
      receivedAt = clock.instant(),
      requestId = hc.requestId.map(_.value)
    )

    workItemRepository.pushNew(workItem, clock.instant()).map(_ => Done)
  }

  def processNextUploadSuccess(): Future[Boolean] = {
    val now = clock.instant()

    workItemRepository.pullOutstanding(now.minus(retryTimeout), now).flatMap {
      case None =>
        Future.successful(false)

      case Some(workItem) =>
        processWorkItem(workItem).flatMap { _ =>
          workItemRepository.complete(workItem.id, ProcessingStatus.Succeeded).map(_ => true)
        }.recoverWith { case e =>
          workItemRepository.markAs(workItem.id, ProcessingStatus.Failed).flatMap { _ =>
            Future.failed(e)
          }
        }
    }
  }

  def processAllUploadSuccesses(): Future[Done] =
    processNextUploadSuccess().flatMap {
      case true  => processAllUploadSuccesses()
      case false => Future.successful(Done)
    }

  def canProcessUploadSuccess(state: Submission.State): Boolean =
    state.isInstanceOf[Ready.type] || state.isInstanceOf[Uploading.type] || state.isInstanceOf[UploadFailed]

  def hasAlreadyHandledUploadSuccess(state: Submission.State, request: UploadSuccessRequest): Boolean =
    state match {
      case Validated(downloadUrl, _, fileName, checksum, size) =>
        downloadUrl == request.downloadUrl &&
          fileName == request.fileName &&
          checksum == request.checksum &&
          size == request.size
      case Submitted(fileName, _, size) =>
        fileName == request.fileName && size == request.size
      case Approved(fileName, _) =>
        fileName == request.fileName
      case Rejected(fileName, _) =>
        fileName == request.fileName
      case _ =>
        false
    }

  private def processWorkItem(workItem: WorkItem[UploadSuccessWorkItem]): Future[Unit] = {
    val item = workItem.item
    val callback = UploadSuccessRequest(
      dprsId = item.dprsId,
      downloadUrl = item.downloadUrl,
      fileName = item.fileName,
      checksum = item.checksum,
      size = item.size
    )
    
    given HeaderCarrier = HeaderCarrier(
      requestId = item.requestId.map(RequestId.apply)
    )

    submissionRepository.get(item.dprsId, item.submissionId).flatMap {
      case None =>
        Future.failed(new RuntimeException(s"Submission not found for dprsId=${item.dprsId}, submissionId=${item.submissionId}"))

      case Some(submission) if hasAlreadyHandledUploadSuccess(submission.state, callback) =>
        Future.successful(())

      case Some(submission) if !canProcessUploadSuccess(submission.state) =>
        Future.failed(new RuntimeException(
          s"Submission ${item.submissionId} is in unexpected state ${submission.state}"
      ))

      case Some(submission) =>
        validationService
          .validateXml(item.fileName, item.dprsId, item.downloadUrl, submission.operatorId)
          .flatMap { maybeReportingPeriod =>

            val auditEvent = FileUploadedEvent(
              conversationId = submission._id,
              dprsId = submission.dprsId,
              operatorId = submission.operatorId,
              operatorName = submission.operatorName,
              fileName = Some(item.fileName),
              outcome = maybeReportingPeriod
                .map(_ => FileUploadOutcome.Accepted)
                .left.map(e => FileUploadOutcome.Rejected(e))
                .merge
            )

            val updatedSubmission =
              maybeReportingPeriod.left.map { failureReason =>
                submission.copy(
                  state = UploadFailed(failureReason, Some(item.fileName)),
                  updated = clock.instant()
                )
              }.map { reportingPeriod =>
                submission.copy(
                  state = Validated(
                    downloadUrl = item.downloadUrl,
                    reportingPeriod = reportingPeriod,
                    fileName = item.fileName,
                    checksum = item.checksum,
                    size = item.size
                  ),
                  updated = clock.instant()
                )
              }.merge

            submissionRepository.save(updatedSubmission).map { _ =>
              auditService.audit(auditEvent)
            }
          }
    }
  }
}
