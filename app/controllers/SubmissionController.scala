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
import models.audit.FileUploadedEvent
import models.audit.FileUploadedEvent.FileUploadOutcome
import models.submission.Submission.State.{Ready, Submitted, UploadFailed, Uploading, Validated}
import models.submission.*
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repository.SubmissionRepository
import services.{AuditService, SubmissionService, UuidService, ValidationService, ViewSubmissionsService}
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
                                       auth: AuthAction,
                                       validationService: ValidationService,
                                       submissionService: SubmissionService,
                                       viewSubmissionsService: ViewSubmissionsService,
                                       auditService: AuditService
                                     )(implicit ec: ExecutionContext) extends BackendController(cc) {

  def start(id: Option[String]): Action[StartSubmissionRequest] =
    auth(parse.json[StartSubmissionRequest]).async { implicit request =>
      id.map { id =>
        submissionRepository.get(request.dprsId, id).flatMap {
          _.map { submission =>
            if (submission.state.isInstanceOf[Validated] || submission.state.isInstanceOf[UploadFailed]) {

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
          submissionType = Submission.SubmissionType.Xml,
          dprsId = request.dprsId,
          operatorId = request.body.operatorId,
          operatorName = request.body.operatorName,
          assumingOperatorName = None,
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

  def uploadSuccess(id: String): Action[UploadSuccessRequest] = Action.async(parse.json[UploadSuccessRequest]) { implicit request =>
    submissionRepository.get(request.body.dprsId, id).flatMap {
      _.map { submission =>
        if (submission.state.isInstanceOf[Ready.type] || submission.state.isInstanceOf[Uploading.type] || submission.state.isInstanceOf[UploadFailed]) {

          validationService.validateXml(request.body.fileName, request.body.dprsId, request.body.downloadUrl, submission.operatorId).flatMap { maybeReportingPeriod =>

            val auditEvent = FileUploadedEvent(
              conversationId = submission._id,
              dprsId = submission.dprsId,
              operatorId = submission.operatorId,
              operatorName = submission.operatorName,
              fileName = Some(request.body.fileName),
              outcome = maybeReportingPeriod
                .map(_ => FileUploadOutcome.Accepted)
                .left.map(e => FileUploadOutcome.Rejected(e))
                .merge
            )

            val updatedSubmission = maybeReportingPeriod.left.map { failureReason =>
              submission.copy(
                state = UploadFailed(failureReason, Some(request.body.fileName)),
                updated = clock.instant()
              )
            }.map { reportingPeriod =>
              submission.copy(
                state = Validated(
                  downloadUrl = request.body.downloadUrl,
                  reportingPeriod = reportingPeriod,
                  fileName = request.body.fileName,
                  checksum = request.body.checksum,
                  size = request.body.size
                ),
                updated = clock.instant()
              )
            }.merge

            auditService.audit(auditEvent)

            submissionRepository.save(updatedSubmission).map { _ =>
              Ok(Json.toJson(updatedSubmission))
            }
          }
        } else {
          Future.successful(Conflict)
        }
      }.getOrElse {
        Future.successful(NotFound)
      }
    }
  }

  def uploadFailed(id: String): Action[UploadFailedRequest] = Action.async(parse.json[UploadFailedRequest]) { implicit request =>
    submissionRepository.get(request.body.dprsId, id).flatMap {
      _.map { submission =>
        if (submission.state.isInstanceOf[Ready.type] || submission.state.isInstanceOf[Uploading.type] || submission.state.isInstanceOf[UploadFailed]) {

          val auditEvent = FileUploadedEvent(
            conversationId = submission._id,
            dprsId = submission.dprsId,
            operatorId = submission.operatorId,
            operatorName = submission.operatorName,
            fileName = None,
            outcome = FileUploadOutcome.Rejected(request.body.reason)
          )

          val updatedSubmission = submission.copy(
            state = UploadFailed(request.body.reason, None),
            updated = clock.instant()
          )

          auditService.audit(auditEvent)

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
        submission.state match {
          case state: Validated =>

            val updatedSubmission = submission.copy(
              state = Submitted(state.fileName, state.reportingPeriod, state.size),
              updated = clock.instant()
            )

            for {
              _ <- submissionService.submit(submission)
              _ <- submissionRepository.save(updatedSubmission)
            } yield Ok(Json.toJson(updatedSubmission))
          case _ =>
            Future.successful(Conflict)
        }
      }.getOrElse {
        Future.successful(NotFound)
      }
    }
  }

  def listDeliveredSubmissions(): Action[ViewSubmissionsInboundRequest] = auth(parse.json[ViewSubmissionsInboundRequest]).async {
    implicit request =>
      val outboundRequest = ViewSubmissionsRequest(request.dprsId, request.body)

      viewSubmissionsService.getDeliveredSubmissions(outboundRequest).map { response =>
        if (response.submissionsExist) Ok(Json.toJson(response)) else NotFound
      }
  }
  
  def listUndeliveredSubmissions(): Action[AnyContent] = auth.async { implicit request =>
    viewSubmissionsService.getUndeliveredSubmissions(request.dprsId).map { response =>
      Ok(Json.toJson(response))
    }
  }
}
