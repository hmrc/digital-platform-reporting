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
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repository.CadxValidationErrorRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class CadxValidationErrorController @Inject()(
                                               cc: ControllerComponents,
                                               cadxValidationErrorRepository: CadxValidationErrorRepository,
                                               auth: AuthAction,
                                               configuration: Configuration
                                             )(using Materializer, ExecutionContext) extends BackendController(cc) {

  private val errorLimit = configuration.get[Int]("cadx.max-errors")

  def getCadxValidationErrors(submissionId: String): Action[AnyContent] =
    auth { implicit request =>

      val source = cadxValidationErrorRepository.getErrorsForSubmission(request.dprsId, submissionId, errorLimit)
        .map { error =>
          ByteString.fromString {
            Json.toJson(error).toString + "\n"
          }
        }

      Ok.chunked(source, contentType = Some("application/x-ndjson"))
    }
}
