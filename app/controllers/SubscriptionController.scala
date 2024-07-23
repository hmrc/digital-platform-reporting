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

import connectors.SubscriptionConnector
import controllers.actions.AuthAction
import models.subscription.requests.SubscriptionRequest
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class SubscriptionController @Inject()(cc: ControllerComponents,
                                       connector: SubscriptionConnector,
                                       authenticate: AuthAction)
                                      (implicit ec: ExecutionContext)
  extends BackendController(cc) {

  def subscribe(): Action[SubscriptionRequest] = Action(parse.json[SubscriptionRequest]).async {
    implicit request =>
      connector
        .subscribe(request.body)
        .map(response => Ok(Json.toJson(response)))
  }
  
  def get(): Action[AnyContent] = authenticate(parse.default).async {
    implicit request =>
      connector
        .get(request.dprsId)
        .map(response => Ok(Json.toJson(response)))
  }
}