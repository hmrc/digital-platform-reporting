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
import models.subscription.{Contact, IndividualContact, OrganisationContact}
import models.subscription.responses.SubscriptionInfo
import org.apache.pekko.Done
import play.api.Configuration
import repository.SdesSubmissionWorkItemRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.ProcessingStatus
import utils.DateTimeFormats
import utils.FileUtils.stripExtension

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

  private val retryTimeout: Duration = configuration.get[Duration]("sdes.submission.retry-after")
  private val informationType: String = configuration.get[String]("sdes.submission.information-type")
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
            checksum = FileChecksum("SHA-256", workItem.item.checksum),
            size = workItem.item.size,
            properties = requestCommon(workItem.item) ++ additionalDetail(workItem.item)
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

  private def requestCommon(workItem: SdesSubmissionWorkItem): List[FileProperty] =
    List(
      FileProperty("requestCommon/conversationID", workItem.submissionId),
      FileProperty("requestCommon/receiptDate", DateTimeFormats.ISO8601Formatter.format(clock.instant())),
      FileProperty("requestCommon/regime", "DPI"),
      FileProperty("requestCommon/schemaVersion", "1.0.0")
    )

  private def additionalDetail(workItem: SdesSubmissionWorkItem): List[FileProperty] = {

    val primaryContact = contactDetail(workItem.subscriptionInfo.primaryContact).map { property =>
      property.copy(name = s"requestAdditionalDetail/primaryContact/${property.name}")
    }

    val secondaryContact = workItem.subscriptionInfo.secondaryContact.flatMap(contactDetail).map { property =>
      property.copy(name = s"requestAdditionalDetail/secondaryContact/${property.name}")
    }

    List(
      Some(FileProperty("requestAdditionalDetail/fileName", stripExtension(workItem.fileName))),
      Some(FileProperty("requestAdditionalDetail/subscriptionID", workItem.subscriptionInfo.id)),
      workItem.subscriptionInfo.tradingName.map(FileProperty("requestAdditionalDetail/tradingName", _)),
      Some(FileProperty("requestAdditionalDetail/isGBUser", workItem.subscriptionInfo.gbUser.toString)),
    ).flatten ++ primaryContact ++ secondaryContact
  }

  private def contactDetail(contact: Contact): List[FileProperty] =
    List(
      Some(FileProperty("emailAddress", contact.email)),
      contact.phone.map(FileProperty("phoneNumber", _))
    ).flatten ++ (contact match {
      case individual: IndividualContact =>
        List(
          FileProperty("individualDetails/firstName", individual.individual.firstName),
          FileProperty("individualDetails/lastName", individual.individual.lastName)
        )
      case organisation: OrganisationContact =>
        List(
          FileProperty("organisationDetails/organisationName", organisation.organisation.name),
        )
    })

  def processAllSubmissions(): Future[Done] =
    processNextSubmission().flatMap {
      case true =>
        processAllSubmissions()
      case false =>
        Future.successful(Done)
    }
}
