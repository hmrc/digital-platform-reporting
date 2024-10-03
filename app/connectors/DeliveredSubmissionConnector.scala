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
import connectors.DeliveredSubmissionConnector.GetDeliveredSubmissionsFailure
import models.submission.{DeliveredSubmissionRequest, DeliveredSubmissions}
import play.api.http.HeaderNames
import play.api.http.Status.{OK, UNPROCESSABLE_ENTITY}
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

class DeliveredSubmissionConnector @Inject()(httpClient: HttpClientV2,
                                             uuidService: UuidService,
                                             clock: Clock,
                                             appConfig: AppConfig)
                                            (implicit ec: ExecutionContext) {

  def get(request: DeliveredSubmissionRequest)
         (implicit hc: HeaderCarrier): Future[Option[DeliveredSubmissions]] = {

    val correlationId = uuidService.generate()
    val conversationId = uuidService.generate()
    
    httpClient.post(url"${appConfig.DeliveredSubmissionsBaseUrl}/dac6/dprs0503/v1")
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer ${appConfig.DeliveredSubmissionsBearerToken}")
      .setHeader("X-Correlation-ID" -> correlationId)
      .setHeader("X-Conversation-ID" -> conversationId)
      .setHeader("X-Forwarded-Host" -> appConfig.AppName)
      .setHeader(HeaderNames.CONTENT_TYPE -> "application/json")
      .setHeader(HeaderNames.ACCEPT -> "application/json")
      .setHeader(HeaderNames.DATE -> RFC7231Formatter.format(clock.instant()))
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .flatMap { response =>
        response.status match {
          case OK                   => Future.successful(Some(response.json.as[DeliveredSubmissions]))
          case UNPROCESSABLE_ENTITY => Future.successful(None)
          case status               => Future.failed(GetDeliveredSubmissionsFailure(correlationId, status))
        }
      }
  }
}

object DeliveredSubmissionConnector {

  final case class GetDeliveredSubmissionsFailure(correlationId: String, status: Int) extends Throwable {
    override def getMessage: String = s"Get delivered submissions failed for correlation ID: $correlationId, got status: $status"
  }
}
