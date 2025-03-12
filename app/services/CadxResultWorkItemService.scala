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

import connectors.{SdesConnector, SdesDownloadConnector}
import logging.Logging
import models.sdes.CadxResultWorkItem
import models.sdes.list.SdesFile
import org.apache.pekko.Done
import play.api.Configuration
import repository.CadxResultWorkItemRepository
import services.CadxResultService.SubmissionNotFoundException
import services.CadxResultWorkItemService.ResultFileNotFound
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}

import java.net.URL
import java.time.{Clock, Duration}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.given
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CadxResultWorkItemService @Inject()(
                                           workItemRepository: CadxResultWorkItemRepository,
                                           cadxResultService: CadxResultService,
                                           sdesConnector: SdesConnector,
                                           downloadConnector: SdesDownloadConnector,
                                           configuration: Configuration,
                                           mongoLockRepository: MongoLockRepository,
                                           clock: Clock
                                         )(using ExecutionContext) extends Logging {

  private val retryTimeout: Duration = configuration.get[Duration]("sdes.cadx-result.retry-after")
  private val cadxResultInformationType: String = configuration.get[String]("sdes.cadx-result.information-type")

  private val instanceId: String = UUID.randomUUID().toString
  private val lockService: LockService = LockService(mongoLockRepository, lockId = instanceId, ttl = 1.hour)

  private given HeaderCarrier = HeaderCarrier()

  def enqueueResult(fileName: String): Future[Done] =
    workItemRepository.pushNew(CadxResultWorkItem(fileName))
      .map(_ => Done)

  def processNextResult(): Future[Boolean] = {
    val now = clock.instant()
    workItemRepository.pullOutstanding(now.minus(retryTimeout), now).flatMap {
      _.map { workItem => {
        for {
          _ <- process(workItem)
          _ <- workItemRepository.complete(workItem.id, ProcessingStatus.Succeeded)
        } yield true
      }.recoverWith {
        case _: SubmissionNotFoundException => workItemRepository.markAs(workItem.id, ProcessingStatus.PermanentlyFailed).map(_ => true)
        case _: ResultFileNotFound => Future.successful(true)
      }
      }.getOrElse(Future.successful(false))
    }
  }

  private def process(workItem: WorkItem[CadxResultWorkItem]): Future[Done] = {
    for {
      files <- sdesConnector.listFiles(cadxResultInformationType)
      fileUrl <- findUrl(files, workItem.item.fileName)
      source <- downloadConnector.download(fileUrl)
      _ <- cadxResultService.processResult(source)
    } yield Done
  }.recoverWith {
    case e: ResultFileNotFound =>
      workItemRepository.markAs(workItem.id, ProcessingStatus.PermanentlyFailed).flatMap(_ => Future.failed(e))
    case e if !e.isInstanceOf[SubmissionNotFoundException] =>
      workItemRepository.markAs(workItem.id, ProcessingStatus.Failed).flatMap(_ => Future.failed(e))
  }

  private def findUrl(files: Seq[SdesFile], fileName: String): Future[URL] =
    files.find(_.fileName == fileName).map { file =>
      Future.successful(file.downloadUrl)
    }.getOrElse(Future.failed(ResultFileNotFound(fileName)))

  def processAllResults(): Future[Done] =
    lockService.withLock {
      processNextResult().flatMap {
        case true =>
          processAllResults()
        case false =>
          Future.successful(Done)
      }
    }.map {
      _.getOrElse {
        logger.info(s"Could not acquire lock on CadxWorkItemService for $instanceId")
        Done
      }
    }
}

object CadxResultWorkItemService {

  final case class ResultFileNotFound(fileName: String) extends Throwable {
    override def getMessage: String = s"File with name $fileName not found in result file list"
  }
}