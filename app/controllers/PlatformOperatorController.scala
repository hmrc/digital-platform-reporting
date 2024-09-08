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

import connectors.PlatformOperatorConnector
import controllers.actions.AuthAction
import models.operator.requests.*
import models.operator.responses.*
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class PlatformOperatorController @Inject()(cc: ControllerComponents,
                                           connector: PlatformOperatorConnector,
                                           authenticate: AuthAction)
                                          (implicit ec: ExecutionContext)
  extends BackendController(cc) {

  given OFormat[CreatePlatformOperatorRequest] = CreatePlatformOperatorRequest.defaultFormat
  given OFormat[UpdatePlatformOperatorRequest] = UpdatePlatformOperatorRequest.defaultFormat
  given OFormat[DeletePlatformOperatorRequest] = DeletePlatformOperatorRequest.defaultFormat
  
  def create(): Action[CreatePlatformOperatorRequest] = authenticate(parse.json[CreatePlatformOperatorRequest]).async {
    implicit request =>
      connector.create(request.body).map { result =>
        Ok(Json.toJson(result))
      }
  }

  def update(): Action[UpdatePlatformOperatorRequest] = authenticate(parse.json[UpdatePlatformOperatorRequest]).async {
    implicit request =>
      connector.update(request.body).map(_ => Ok)
  }

  def delete(operatorId: String): Action[AnyContent] = authenticate(parse.default).async {
    implicit request =>
      val deleteRequest = DeletePlatformOperatorRequest(request.dprsId, operatorId)
      connector.delete(deleteRequest).map(_ => Ok)
  }

  def get(): Action[AnyContent] = authenticate(parse.default).async {
    implicit request =>
      connector
        .get(request.dprsId)
        .map(response => Ok(Json.toJson(response)(ViewPlatformOperatorsResponse.defaultWrites)))
  }

  def getOne(operatorId: String): Action[AnyContent] = authenticate(parse.default).async {
    implicit request =>
      connector
        .get(request.dprsId, operatorId)
        .map {
          _.platformOperators
            .find(_.operatorId == operatorId)
            .map(operator => Ok(Json.toJson(operator)(PlatformOperator.defaultWrites)))
            .getOrElse(NotFound)
        }
  }
}
