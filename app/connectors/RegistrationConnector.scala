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

import config.Service
import connectors.RegistrationWithIdParser.*
import connectors.RegistrationWithoutIdParser.*
import models.registration.requests.{RequestWithId, RequestWithoutId}
import models.registration.responses.{ResponseWithId, ResponseWithoutId}
import play.api.Configuration
import play.api.http.HeaderNames
import play.api.libs.json.*
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import services.UuidService
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RegistrationConnector @Inject()(configuration: Configuration,
                                      httpClient: HttpClientV2,
                                      uuidService: UuidService)
                                     (implicit ec: ExecutionContext) {

  private val baseRegisterWithIdUrl = configuration.get[Service]("microservice.services.register-with-id").baseUrl
  private val registerWithIdBearerToken = configuration.get[String]("microservice.services.register-with-id.bearer-token")

  private val baseRegisterWithoutIdUrl = configuration.get[Service]("microservice.services.register-without-id").baseUrl
  private val registerWithoutIdBearerToken = configuration.get[String]("microservice.services.register-without-id.bearer-token")

  def registerWithId(request: RequestWithId)(implicit hc: HeaderCarrier): Future[ResponseWithId] =
    httpClient.post(url"$baseRegisterWithIdUrl/dac6/dprs0102/v1")
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer $registerWithIdBearerToken")
      .setHeader("X-Correlation-ID" -> uuidService.generate())
      .setHeader("X-Conversation-ID" -> uuidService.generate())
      .setHeader(HeaderNames.CONTENT_TYPE -> "application/json")
      .setHeader(HeaderNames.ACCEPT -> "application/json")
      .withBody(Json.toJson(request))
      .execute[ResponseWithId]

  def registerWithoutId(request: RequestWithoutId)(implicit hc: HeaderCarrier): Future[ResponseWithoutId] =
    httpClient.post(url"$baseRegisterWithoutIdUrl/dac6/dprs0101/v1")
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer $registerWithoutIdBearerToken")
      .setHeader("X-Correlation-ID" -> uuidService.generate())
      .setHeader("X-Conversation-ID" -> uuidService.generate())
      .setHeader(HeaderNames.CONTENT_TYPE -> "application/json")
      .setHeader(HeaderNames.ACCEPT -> "application/json")
      .withBody(Json.toJson(request))
      .execute[ResponseWithoutId]
}
