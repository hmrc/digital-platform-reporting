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
import connectors.SubscriptionConnector.UpdateSubscriptionFailure
import models.subscription.requests.SubscriptionRequest
import models.subscription.responses.*
import org.apache.pekko.Done
import play.api.http.HeaderNames
import play.api.http.Status.{CONFLICT, CREATED, OK}
import play.api.libs.json.*
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import services.UuidService
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import utils.DateTimeFormats.RFC7231Formatter

import java.time.Clock
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionConnector @Inject()(httpClient: HttpClientV2,
                                      uuidService: UuidService,
                                      clock: Clock,
                                      appConfig: AppConfig)
                                     (implicit ec: ExecutionContext) {

  def subscribe(request: SubscriptionRequest)(implicit hc: HeaderCarrier): Future[SubscriptionResponse] =
    httpClient.post(url"${appConfig.SubscribeBaseUrl}/dac6/dprs0201/v1")
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer ${appConfig.UserSubscriptionBearerToken}")
      .setHeader("X-Correlation-ID" -> uuidService.generate())
      .setHeader("X-Conversation-ID" -> uuidService.generate())
      .setHeader("X-Forwarded-Host" -> appConfig.AppName)
      .setHeader(HeaderNames.CONTENT_TYPE -> "application/json")
      .setHeader(HeaderNames.ACCEPT -> "application/json")
      .setHeader(HeaderNames.DATE -> RFC7231Formatter.format(clock.instant()))
      .withBody(Json.toJson(request)(SubscriptionRequest.createWrites))
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case CREATED => response.json.as[SubscribedResponse]
          case CONFLICT => AlreadySubscribedResponse
        }
      }

  def updateSubscription(request: SubscriptionRequest)(implicit hc: HeaderCarrier): Future[Done] = {

    val correlationId = uuidService.generate()
    val conversationId = uuidService.generate()

    httpClient.post(url"${appConfig.SubscribeBaseUrl}/dac6/dprs0203/v1")
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer ${appConfig.UpdateContactsBearerToken}")
      .setHeader("X-Correlation-ID" -> correlationId)
      .setHeader("X-Conversation-ID" -> conversationId)
      .setHeader("X-Forwarded-Host" -> appConfig.AppName)
      .setHeader(HeaderNames.CONTENT_TYPE -> "application/json")
      .setHeader(HeaderNames.ACCEPT -> "application/json")
      .setHeader(HeaderNames.DATE -> RFC7231Formatter.format(clock.instant()))
      .withBody(Json.toJson(request)(SubscriptionRequest.updateWrites))
      .execute[HttpResponse]
      .flatMap { response =>
        response.status match {
          case OK      => Future.successful(Done)
          case status  => Future.failed(UpdateSubscriptionFailure(correlationId, status))
        }
      }
  }

  def get(dprsId: String)(implicit hc: HeaderCarrier): Future[SubscriptionInfo] =
    httpClient.get(url"${appConfig.SubscribeBaseUrl}/dac6/dprs0202/v1/$dprsId")
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer ${appConfig.ReadContactsBearerToken}")
      .setHeader("X-Correlation-ID" -> uuidService.generate())
      .setHeader("X-Conversation-ID" -> uuidService.generate())
      .setHeader("X-Forwarded-Host" -> appConfig.AppName)
      .setHeader(HeaderNames.CONTENT_TYPE -> "application/json")
      .setHeader(HeaderNames.ACCEPT -> "application/json")
      .setHeader(HeaderNames.DATE -> RFC7231Formatter.format(clock.instant()))
      .execute[SubscriptionInfo]
}

object SubscriptionConnector {

  final case class UpdateSubscriptionFailure(correlationId: String, status: Int) extends Throwable {
    override def getMessage: String = s"Update subscription failed for correlation ID: $correlationId, got status: $status"
  }
}
