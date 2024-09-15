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
import models.submission.Submission.State.{Rejected, Submitted}
import play.api.mvc.{Action, ControllerComponents}
import repository.SubmissionRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SdesSubmissionCallbackController @Inject()(
                                                  cc: ControllerComponents,
                                                  submissionRepository: SubmissionRepository
                                                )(using ExecutionContext) extends BackendController(cc) with Logging {

  def callback(): Action[NotificationCallback] = Action.async(parse.json[NotificationCallback]) { implicit request =>
    if (request.body.notification == NotificationType.FileProcessingFailure) {
      OptionT(submissionRepository.getById(request.body.correlationID))
        .filter(_.state.isInstanceOf[Submitted.type])
        .semiflatMap { submission =>
          val updatedSubmission = submission.copy(
            state = Rejected(request.body.failureReason.getOrElse("Unknown")) // TODO do something better than this when we know what shape the rejected errors will be in
          )
          submissionRepository.save(updatedSubmission).map { _ =>
            Ok
          }
        }.getOrElse(Ok)
    } else {
      logger.info(s"SDES callback received for submission: ${request.body.correlationID}, with status: ${request.body.notification}")
      Future.successful(Ok)
    }
  }
}
