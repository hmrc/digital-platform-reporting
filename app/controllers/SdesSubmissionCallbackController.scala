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

import cats.data.OptionT
import logging.Logging
import models.sdes.{NotificationCallback, NotificationType}
import models.submission.CadxValidationError
import models.submission.Submission.State.{Rejected, Submitted}
import play.api.mvc.{Action, ControllerComponents}
import repository.{CadxValidationErrorRepository, SubmissionRepository}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SdesSubmissionCallbackController @Inject()(
                                                  cc: ControllerComponents,
                                                  submissionRepository: SubmissionRepository,
                                                  cadxValidationErrorRepository: CadxValidationErrorRepository,
                                                  clock: Clock
                                                )(using ExecutionContext) extends BackendController(cc) with Logging {

  def callback(): Action[NotificationCallback] = Action.async(parse.json[NotificationCallback]) { implicit request =>
    if (request.body.notification == NotificationType.FileProcessingFailure) {
      OptionT(submissionRepository.getById(request.body.correlationID))
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
    } else {
      logger.info(s"SDES callback received for submission: ${request.body.correlationID}, with status: ${request.body.notification}")
      Future.successful(Ok)
    }
  }
}
