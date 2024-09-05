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
import connectors.PlatformOperatorConnector.CreatePlatformOperatorFailure
import models.ErrorResponse
import models.operator.requests.CreatePlatformOperatorRequest
import models.operator.responses.PlatformOperatorCreatedResponse
import models.subscription.requests.SubscriptionRequest
import models.subscription.responses.*
import org.apache.pekko.Done
import play.api.http.HeaderNames
import play.api.http.Status.{CREATED, OK, UNPROCESSABLE_ENTITY}
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

class PlatformOperatorConnector @Inject()(httpClient: HttpClientV2,
                                          uuidService: UuidService,
                                          clock: Clock,
                                          appConfig: AppConfig)
                                         (implicit ec: ExecutionContext) {
  
  def createPlatformOperator(request: CreatePlatformOperatorRequest)
                            (implicit hc: HeaderCarrier): Future[PlatformOperatorCreatedResponse] = {
    
    val correlationId = uuidService.generate()
    val conversationId = uuidService.generate()

    httpClient.post(url"${appConfig.UpdatePlatformOperatorBaseUrl}/dac6/dprs9301/v1")
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer ${appConfig.UpdatePlatformOperatorBearerToken}")
      .setHeader("X-Correlation-ID" -> correlationId)
      .setHeader("X-Conversation-ID" -> conversationId)
      .setHeader("X-Forwarded-Host" -> appConfig.AppName)
      .setHeader(HeaderNames.CONTENT_TYPE -> "application/json")
      .setHeader(HeaderNames.ACCEPT -> "application/json")
      .setHeader(HeaderNames.DATE -> RFC7231Formatter.format(clock.instant()))
      .withBody(Json.toJson(request)(CreatePlatformOperatorRequest.downstreamWrites))
      .execute[HttpResponse]
      .flatMap { response =>
        response.status match {
          case OK => Future.successful(response.json.as[PlatformOperatorCreatedResponse])
          case status => Future.failed(CreatePlatformOperatorFailure(correlationId, status))
        }
      }
  }
}

object PlatformOperatorConnector {
  
  final case class CreatePlatformOperatorFailure(correlationId: String, status: Int) extends Throwable {
    override def getMessage: String = s"Create platform operator failed for correlation ID: $correlationId, got status: $status"
  }
}