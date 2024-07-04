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

package connectors

import connectors.RegistrationConnectorExceptions.*
import logging.Logging
import models.registration.responses.ResponseWithoutId
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json.*
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

object RegistrationWithoutIdParser {

  implicit object RegistrationWithoutIdReads extends HttpReads[Either[Exception, ResponseWithoutId]] with Logging {

    override def read(method: String, url: String, response: HttpResponse): Either[Exception, ResponseWithoutId] =
      response.status match {
        case OK =>
          response.json.validate[ResponseWithoutId] match {
            case JsSuccess(model, _) =>
              Right(model)
            case JsError(errors) =>
              logger.warn(s"Unable to parse response: $errors")
              Left(UnableToParseResponse)
          }

        case NOT_FOUND =>
          Left(NotFound)

        case status =>
          logger.warn(s"Error response: $status")
          Left(UnexpectedResponse(status))
      }
  }
}
