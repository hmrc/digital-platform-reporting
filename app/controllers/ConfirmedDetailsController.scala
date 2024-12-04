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
import models.confirmed.requests.{ConfirmedBusinessDetailsRequest, ConfirmedReportingNotificationsRequest}
import models.confirmed.{ConfirmedBusinessDetails, ConfirmedContactDetails, ConfirmedReportingNotification}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repository.{ConfirmedBusinessDetailsRepository, ConfirmedContactDetailsRepository, ConfirmedReportingNotificationsRepository}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.Clock
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ConfirmedDetailsController @Inject()(authAction: AuthAction,
                                           confirmedBusinessDetailsRepository: ConfirmedBusinessDetailsRepository,
                                           confirmedReportingNotificationsRepository: ConfirmedReportingNotificationsRepository,
                                           confirmedContactDetailsRepository: ConfirmedContactDetailsRepository,
                                           clock: Clock)
                                          (implicit cc: ControllerComponents, ec: ExecutionContext)
  extends BackendController(cc) {

  def businessDetails(operatorId: String): Action[AnyContent] = authAction(parse.default).async { implicit request =>
    confirmedBusinessDetailsRepository.findBy(request.userId, operatorId).map {
      case Some(confirmedBusinessDetails) => Ok(Json.toJson(confirmedBusinessDetails))
      case None => NotFound
    }
  }

  def saveBusinessDetails(): Action[ConfirmedBusinessDetailsRequest] = authAction(parse.json[ConfirmedBusinessDetailsRequest]).async { implicit request =>
    val confirmedBusinessDetails = ConfirmedBusinessDetails(request.userId, request.body.operatorId, clock.instant())
    confirmedBusinessDetailsRepository.save(confirmedBusinessDetails).map(_ => Ok)
  }

  def reportingNotifications(operatorId: String): Action[AnyContent] = authAction(parse.default).async { implicit request =>
    confirmedReportingNotificationsRepository.findBy(request.userId, operatorId).map {
      case Some(confirmedReportingNotifications) => Ok(Json.toJson(confirmedReportingNotifications))
      case None => NotFound
    }
  }

  def saveReportingNotifications(): Action[ConfirmedReportingNotificationsRequest] = authAction(parse.json[ConfirmedReportingNotificationsRequest]).async { implicit request =>
    val confirmedReportingNotifications = ConfirmedReportingNotification(request.userId, request.body.operatorId, clock.instant())
    confirmedReportingNotificationsRepository.save(confirmedReportingNotifications).map(_ => Ok)
  }

  def contactDetails(): Action[AnyContent] = authAction(parse.default).async { implicit request =>
    confirmedContactDetailsRepository.findBy(request.userId).map {
      case Some(confirmedContactDetails) => Ok(Json.toJson(confirmedContactDetails))
      case None => NotFound
    }
  }

  def saveContactDetails(): Action[AnyContent] = authAction(parse.default).async { implicit request =>
    val confirmedContactDetails = ConfirmedContactDetails(request.userId, clock.instant())
    confirmedContactDetailsRepository.save(confirmedContactDetails).map(_ => Ok)
  }
}
