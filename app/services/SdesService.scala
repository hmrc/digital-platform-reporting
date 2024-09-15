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

import connectors.SdesConnector
import models.sdes.*
import models.submission.Submission.State.Validated
import models.subscription.responses.SubscriptionInfo
import org.apache.pekko.Done
import play.api.Configuration
import repository.SdesSubmissionWorkItemRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.ProcessingStatus

import java.time.{Clock, Duration}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SdesService @Inject()(
                             clock: Clock,
                             workItemRepository: SdesSubmissionWorkItemRepository,
                             sdesConnector: SdesConnector,
                             configuration: Configuration
                           )(using ExecutionContext) {

  private val retryTimeout: Duration = configuration.get[Duration]("sdes.work-item.retry-timeout")
  private val informationType: String = configuration.get[String]("sdes.information-type")
  private val recipientOrSender: String = configuration.get[String]("sdes.recipient-or-sender")

  def enqueueSubmission(submissionId: String, state: Validated, subscription: SubscriptionInfo): Future[Done] = {

    val workItem = SdesSubmissionWorkItem(
      submissionId = submissionId,
      downloadUrl = state.downloadUrl,
      fileName = state.fileName,
      checksum = state.checksum,
      size = state.size,
      subscriptionInfo = subscription
    )

    workItemRepository.pushNew(workItem, clock.instant()).map(_ => Done)
  }

  def processNextSubmission(): Future[Boolean] = {
    val now = clock.instant()
    workItemRepository.pullOutstanding(now.minus(retryTimeout), now).flatMap {
      _.map { workItem =>

        val notificationRequest = FileNotifyRequest(
          informationType = informationType,
          file = FileMetadata(
            recipientOrSender = recipientOrSender,
            name = workItem.item.fileName,
            location = workItem.item.downloadUrl,
            checksum = FileChecksum("SHA256", workItem.item.checksum),
            size = workItem.item.size,
            properties = List.empty // TODO need to add metadata here when we have the schema for that
          ),
          audit = FileAudit(workItem.item.submissionId)
        )

        for {
          _ <- sdesConnector.notify(notificationRequest)(using HeaderCarrier()).recoverWith { case e =>
            workItemRepository.markAs(workItem.id, ProcessingStatus.Failed).flatMap { _ =>
              Future.failed(e)
            }
          }
          _ <- workItemRepository.complete(workItem.id, ProcessingStatus.Succeeded)
        } yield true
      }.getOrElse(Future.successful(false))
    }
  }

  def processAllSubmissions(): Future[Done] =
    processNextSubmission().flatMap {
      case true =>
        processAllSubmissions()
      case false =>
        Future.successful(Done)
    }
}
