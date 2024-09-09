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

import controllers.actions.AuthAction
import models.submission.Submission.State.{Ready, Submitted, UploadFailed, Uploading, Validated}
import models.submission.{Submission, UploadFailedRequest}
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
                                       submissionRepository: SubmissionRepository,
                                       auth: AuthAction
                                     )(implicit ec: ExecutionContext) extends BackendController(cc) {

  def start(id: Option[String]): Action[AnyContent] =
    auth.async { implicit request =>
      id.map { id =>
        submissionRepository.get(request.dprsId, id).flatMap {
          _.map { submission =>
            if (submission.state == Validated) {

              val updatedSubmission = submission.copy(
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
          dprsId = request.dprsId,
          state = Ready,
          created = clock.instant(),
          updated = clock.instant()
        )

        submissionRepository.save(submission).map { _ =>
          Created(Json.toJson(submission))
        }
      }
    }

  def get(id: String): Action[AnyContent] = auth.async { implicit request =>
    submissionRepository.get(request.dprsId, id).map {
      _.map { submission =>
        Ok(Json.toJson(submission))
      }.getOrElse {
        NotFound
      }
    }
  }

  def startUpload(id: String): Action[AnyContent] = auth.async { implicit request =>
    submissionRepository.get(request.dprsId, id).flatMap {
      _.map { submission =>
        if (submission.state.isInstanceOf[Ready.type] || submission.state.isInstanceOf[UploadFailed]) {

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

  def uploadSuccess(id: String): Action[AnyContent] = auth.async { implicit request =>
    submissionRepository.get(request.dprsId, id).flatMap {
      _.map { submission =>
        if (submission.state.isInstanceOf[Uploading.type]) {
          // TODO validation

          val updatedSubmission = submission.copy(
            state = Validated,
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

  def uploadFailed(id: String): Action[UploadFailedRequest] = auth.async(parse.json[UploadFailedRequest]) { implicit request =>
    submissionRepository.get(request.dprsId, id).flatMap {
      _.map { submission =>
        if (submission.state.isInstanceOf[Uploading.type]) {

          val updatedSubmission = submission.copy(
            state = UploadFailed(request.body.reason),
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

  def submit(id: String): Action[AnyContent] = auth.async { implicit request =>
    submissionRepository.get(request.dprsId, id).flatMap {
      _.map { submission =>
        if (submission.state.isInstanceOf[Validated.type]) {

          val updatedSubmission = submission.copy(
            state = Submitted,
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
}
