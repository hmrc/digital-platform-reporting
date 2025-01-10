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
import models.submission.{AssumedReportingSubmission, AssumedReportingSubmissionRequest, SubmissionSummary}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.{AssumedReportingService, SubmissionService, ViewSubmissionsService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.Year
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class AssumedReportingController @Inject()(cc: ControllerComponents,
                                           submissionService: SubmissionService,
                                           assumedReportingService: AssumedReportingService,
                                           viewSubmissionsService: ViewSubmissionsService,
                                           auth: AuthAction)
                                          (using ExecutionContext) extends BackendController(cc) {

  def submit(): Action[AssumedReportingSubmissionRequest] =
    auth.async(parse.json[AssumedReportingSubmissionRequest]) { implicit request =>
      submissionService.submitAssumedReporting(
        dprsId = request.dprsId,
        operatorId = request.body.operatorId,
        assumingOperator = request.body.assumingOperator,
        reportingPeriod = request.body.reportingPeriod
      ).map(submission => Ok(Json.toJson(submission)))
    }

  def delete(operatorId: String, reportingPeriod: Year): Action[AnyContent] =
    auth.async { implicit request =>
      submissionService.submitAssumedReportingDeletion(
        dprsId = request.dprsId,
        operatorId = operatorId,
        reportingPeriod = reportingPeriod
      ).map(submission => Ok(Json.toJson(submission)))
    }

  def get(operatorId: String, reportingPeriod: Year): Action[AnyContent] = auth.async { implicit request =>
    assumedReportingService.getSubmission(request.dprsId, operatorId, reportingPeriod)
      .map(_.map(submission => Ok(Json.toJson(submission)))
        .getOrElse(NotFound)
      )
  }

  def list(): Action[AnyContent] = auth.async { implicit request =>
    viewSubmissionsService.getAssumedReports(request.dprsId)
      .map { submissions =>
        if (submissions.isEmpty) {
          NotFound
        } else {
          Ok(Json.toJson(submissions))
        }
      }
  }

  def listFor(operatorId: String): Action[AnyContent] = auth.async { implicit request =>
    viewSubmissionsService.getAssumedReports(request.dprsId, Some(operatorId)).map {
      case Nil => NotFound
      case operatorSubmissions => Ok(Json.toJson(operatorSubmissions))
    }
  }
}
