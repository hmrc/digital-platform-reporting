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

import logging.Logging
import models.registration.responses.*
import play.api.http.Status.{CONFLICT, NOT_FOUND, OK}
import uk.gov.hmrc.http.{HttpReads, HttpResponse}

object RegistrationWithIdParser {

  implicit object RegistrationWithIdReads extends HttpReads[ResponseWithId] with Logging {

    override def read(method: String, url: String, response: HttpResponse): ResponseWithId =
      response.status match {
        case OK => response.json.as[MatchResponseWithId]
        case NOT_FOUND => NoMatchResponse
        case CONFLICT => AlreadySubscribedResponse
      }
  }
}
