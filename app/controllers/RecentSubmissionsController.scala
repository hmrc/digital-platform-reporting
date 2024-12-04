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
import models.recentsubmissions.RecentSubmissionDetails
import models.recentsubmissions.requests.RecentSubmissionRequest
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repository.RecentSubmissionsRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.Clock
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class RecentSubmissionsController @Inject()(authAction: AuthAction,
                                            recentSubmissionsRepository: RecentSubmissionsRepository,
                                            clock: Clock)
                                           (implicit cc: ControllerComponents, ec: ExecutionContext)
  extends BackendController(cc) {

  def get(): Action[AnyContent] = authAction(parse.default).async { implicit request =>
    recentSubmissionsRepository.findBy(request.userId).map {
      case Some(recentSubmissionDetails) => Ok(Json.toJson(recentSubmissionDetails))
      case None => NotFound
    }
  }

  def save(): Action[RecentSubmissionRequest] = authAction(parse.json[RecentSubmissionRequest]).async { implicit request =>
    val recentSubmissionDetails = RecentSubmissionDetails(
      userId = request.userId,
      operatorDetails = request.body.operatorDetails,
      yourContactDetailsCorrect = request.body.yourContactDetailsCorrect,
      created = clock.instant(),
    )
    recentSubmissionsRepository.save(recentSubmissionDetails).map(_ => Ok)
  }
}
