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
import models.subscription.requests.SubscriptionRequest
import models.subscription.responses.*
import play.api.Configuration
import play.api.http.HeaderNames
import play.api.http.Status.{CONFLICT, CREATED}
import play.api.libs.json.*
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import services.UuidService
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import java.time.format.DateTimeFormatter
import java.time.{Clock, ZoneId}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionConnector @Inject()(configuration: Configuration,
                                      httpClient: HttpClientV2,
                                      uuidService: UuidService,
                                      clock: Clock)
                                     (implicit ec: ExecutionContext) {

  private val baseSubscribeUrl = configuration.get[Service]("microservice.services.subscribe").baseUrl
  private val userSubscriptionBearerToken = configuration.get[String]("microservice.services.subscribe.bearerTokens.userSubscription")
  private val readContactsBearerToken = configuration.get[String]("microservice.services.subscribe.bearerTokens.readContacts")
  //  private val updateContactsBearerToken = configuration.get[String]("microservice.services.subscribe.bearerTokens.updateContacts")

  private val dateFormat = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz").withZone(ZoneId.of("UTC"))

  def subscribe(request: SubscriptionRequest)(implicit hc: HeaderCarrier): Future[SubscriptionResponse] =
    httpClient.post(url"$baseSubscribeUrl/dac6/dprs0201/v1")
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer $userSubscriptionBearerToken")
      .setHeader("X-Correlation-ID" -> uuidService.generate())
      .setHeader("X-Conversation-ID" -> uuidService.generate())
      .setHeader(HeaderNames.CONTENT_TYPE -> "application/json")
      .setHeader(HeaderNames.ACCEPT -> "application/json")
      .setHeader(HeaderNames.DATE -> dateFormat.format(clock.instant()))
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case CREATED => response.json.as[SubscribedResponse]
          case CONFLICT => AlreadySubscribedResponse
        }
      }

  def get(dprsId: String)(implicit hc: HeaderCarrier): Future[SubscriptionInfo] =
    httpClient.get(url"$baseSubscribeUrl/dac6/dprs0202/v1/$dprsId")
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer $readContactsBearerToken")
      .setHeader("X-Correlation-ID" -> uuidService.generate())
      .setHeader("X-Conversation-ID" -> uuidService.generate())
      .setHeader(HeaderNames.ACCEPT -> "application/json")
      .setHeader(HeaderNames.DATE -> dateFormat.format(clock.instant()))
      .execute[SubscriptionInfo]
}
