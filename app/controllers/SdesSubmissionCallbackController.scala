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

import cats.data.{EitherT, OptionT}
import connectors.{SdesConnector, SdesDownloadConnector}
import logging.Logging
import models.sdes.list.SdesFile
import models.sdes.{NotificationCallback, NotificationType}
import models.submission.CadxValidationError
import models.submission.Submission.State.{Rejected, Submitted}
import play.api.Configuration
import play.api.mvc.{Action, ControllerComponents, Result}
import repository.{CadxValidationErrorRepository, SubmissionRepository}
import services.CadxResultService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.net.URL
import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SdesSubmissionCallbackController @Inject()(
                                                  cc: ControllerComponents,
                                                  submissionRepository: SubmissionRepository,
                                                  cadxValidationErrorRepository: CadxValidationErrorRepository,
                                                  sdesConnector: SdesConnector,
                                                  cadxResultService: CadxResultService,
                                                  downloadConnector: SdesDownloadConnector,
                                                  clock: Clock,
                                                  configuration: Configuration
                                                )(using ExecutionContext) extends BackendController(cc) with Logging {

  private val cadxResultInformationType: String = configuration.get[String]("sdes.cadx-result.information-type")

  def callback(): Action[NotificationCallback] = Action.async(parse.json[NotificationCallback]) { implicit request =>
    request.body.notification match {
      case NotificationType.FileProcessingFailure =>
        handleFileProcessingFailure(request.body)
      case NotificationType.FileReady =>
        handleFileReady(request.body)
      case _ =>
        logger.info(s"SDES callback received for submission: ${request.body.correlationID}, with status: ${request.body.notification}")
        Future.successful(Ok)
    }
  }

  private def handleFileProcessingFailure(callback: NotificationCallback): Future[Result] =
    OptionT(submissionRepository.getById(callback.correlationID))
      .flatMap { submission =>
        submission.state match {
          case state: Submitted =>
            val timestamp = clock.instant()

            val error = CadxValidationError.FileError(
              submissionId = submission._id,
              dprsId = submission.dprsId,
              code = "MDTP1",
              detail = None,
              created = timestamp
            )

            OptionT.liftF {
              for {
                _ <- submissionRepository.save(submission.copy(state = Rejected(state.fileName, state.reportingPeriod), updated = timestamp))
                _ <- cadxValidationErrorRepository.save(error)
              } yield Ok
            }
          case _ =>
            OptionT.none
        }
      }.getOrElse(Ok)

  private def handleFileReady(callback: NotificationCallback)(using HeaderCarrier): Future[Result] = {
    for {
      files   <- EitherT.liftF(sdesConnector.listFiles(cadxResultInformationType))
      fileUrl <- EitherT.fromEither(findUrl(files, callback.filename))
      source  <- EitherT.liftF(downloadConnector.download(fileUrl))
      _       <- EitherT.liftF(cadxResultService.processResult(source))
    } yield Ok
  }.merge

  private def findUrl(files: Seq[SdesFile], fileName: String): Either[Result, URL] =
    files.find(_.fileName == fileName).map { file =>
      file.downloadUrl
    }.toRight(Conflict)
}
