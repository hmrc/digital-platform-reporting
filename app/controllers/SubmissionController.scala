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

import models.submission.Submission.State.{Ready, Uploading, Validated}
import models.submission.{StartSubmissionRequest, Submission}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repository.SubmissionRepository
import services.UuidService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionController @Inject() (
                                       cc: ControllerComponents,
                                       uuidService: UuidService,
                                       clock: Clock,
                                       submissionRepository: SubmissionRepository
                                     )(implicit ec: ExecutionContext) extends BackendController(cc) {

  def start(dprsId: String, id: Option[String]): Action[StartSubmissionRequest] =
    Action.async(parse.json[StartSubmissionRequest]) { implicit request =>
      id.map { id =>
        submissionRepository.get(dprsId, id).flatMap {
          _.map { submission =>
            if (submission.state == Validated) {

              val updatedSubmission = submission.copy(
                platformOperatorId = request.body.platformOperatorId,
                state = Ready,
                updated = clock.instant()
              )

              submissionRepository.save(updatedSubmission).map { _ =>
                Ok(Json.toJson(updatedSubmission))
              }
            } else {
              Future.successful(Conflict)
            }
          }.getOrElse {
            Future.successful(NotFound)
          }
        }
      }.getOrElse {

        val submission = Submission(
          _id = uuidService.generate(),
          dprsId = dprsId,
          platformOperatorId = request.body.platformOperatorId,
          state = Ready,
          created = clock.instant(),
          updated = clock.instant()
        )

        submissionRepository.save(submission).map { _ =>
          Created(Json.toJson(submission))
        }
      }
    }

  def get(dprsId: String, id: String): Action[AnyContent] = Action.async { implicit request =>
    submissionRepository.get(dprsId, id).map {
      _.map { submission =>
        Ok(Json.toJson(submission))
      }.getOrElse {
        NotFound
      }
    }
  }

  def startUpload(dprsId: String, id: String): Action[AnyContent] = Action.async { implicit request =>
    submissionRepository.get(dprsId, id).flatMap {
      _.map { submission =>
        if (submission.state.isInstanceOf[Ready.type] || submission.state.isInstanceOf[Validated.type]) {

          val updatedSubmission = submission.copy(
            state = Uploading,
            updated = clock.instant()
          )

          submissionRepository.save(updatedSubmission).map { _ =>
            Ok(Json.toJson(updatedSubmission))
          }
        } else {
          Future.successful(Conflict)
        }
      }.getOrElse {
        Future.successful(NotFound)
      }
    }
  }

  def uploadSuccess(dprsId: String, id: String): Action[AnyContent] = ???

  def uploadFailed(dprsId: String, id: String): Action[AnyContent] = ???

  def submit(dprsId: String, id: String): Action[AnyContent] = ???
}
