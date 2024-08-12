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

import config.AppConfig
import connectors.RegistrationWithIdParser.*
import connectors.RegistrationWithoutIdParser.*
import models.registration.requests.{RequestWithId, RequestWithoutId}
import models.registration.responses.{ResponseWithId, ResponseWithoutId}
import play.api.http.HeaderNames
import play.api.libs.json.*
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import services.UuidService
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import utils.DateTimeFormats.RFC7231Formatter

import java.time.Clock
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RegistrationConnector @Inject()(httpClient: HttpClientV2,
                                      uuidService: UuidService,
                                      clock: Clock,
                                      appConfig: AppConfig)
                                     (implicit ec: ExecutionContext) {

  def registerWithId(request: RequestWithId)(implicit hc: HeaderCarrier): Future[ResponseWithId] =
    httpClient.post(url"${appConfig.RegisterWithIdBaseUrl}/dac6/dprs0102/v1")
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer ${appConfig.RegisterWithIdBearerToken}")
      .setHeader("X-Correlation-ID" -> uuidService.generate())
      .setHeader("X-Conversation-ID" -> uuidService.generate())
      .setHeader("X-Forwarded-Host" -> appConfig.AppName)
      .setHeader(HeaderNames.CONTENT_TYPE -> "application/json")
      .setHeader(HeaderNames.ACCEPT -> "application/json")
      .setHeader(HeaderNames.DATE -> RFC7231Formatter.format(clock.instant()))
      .withBody(Json.toJson(request))
      .execute[ResponseWithId]

  def registerWithoutId(request: RequestWithoutId)(implicit hc: HeaderCarrier): Future[ResponseWithoutId] =
    httpClient.post(url"${appConfig.RegisterWithoutIdBaseUrl}/dac6/dprs0101/v1")
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer ${appConfig.RegisterWithoutIdBearerToken}")
      .setHeader("X-Correlation-ID" -> uuidService.generate())
      .setHeader("X-Conversation-ID" -> uuidService.generate())
      .setHeader("X-Forwarded-Host" -> appConfig.AppName)
      .setHeader(HeaderNames.CONTENT_TYPE -> "application/json")
      .setHeader(HeaderNames.ACCEPT -> "application/json")
      .setHeader(HeaderNames.DATE -> RFC7231Formatter.format(clock.instant()))
      .withBody(Json.toJson(request))
      .execute[ResponseWithoutId]
}
