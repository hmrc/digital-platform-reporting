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

import connectors.RegistrationConnector
import models.registration.requests.*
import models.registration.responses.*
import play.api.libs.json.Json
import play.api.mvc.{Action, ControllerComponents}
import services.UuidService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.Clock
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class RegistrationController @Inject()(
                                        cc: ControllerComponents,
                                        connector: RegistrationConnector,
                                        uuidService: UuidService,
                                        clock: Clock
                                      )(implicit ec: ExecutionContext)
    extends BackendController(cc) {
  
  def register(): Action[RequestDetail] = Action(parse.json[RequestDetail]).async {
    implicit request =>
      
      val requestCommon = RequestCommon(clock.instant(), uuidService.generate())
      
      request.body match {
        case r: RequestDetailWithId =>
          val request = RequestWithId(requestCommon, r)
          connector.registerWithId(request).map {
            case response: MatchResponseWithId =>
              Ok(Json.toJson(response.responseDetail))
              
            case NoMatchResponse =>
              NotFound
          }

        case r: RequestDetailWithoutId =>
          val request = RequestWithoutId(requestCommon, r)
          connector.registerWithoutId(request).map {
            case response: MatchResponseWithoutId =>
              Ok(Json.toJson(response.responseDetail))
              
            case NoMatchResponse =>
              NotFound
          }
      }
  }
}
