/*
 * Copyright 2023 HM Revenue & Customs
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
import connectors.SdesConnector.SdesCircuitBreaker
import logging.Logging
import models.sdes.FileNotifyRequest
import org.apache.pekko.Done
import org.apache.pekko.pattern.CircuitBreaker
import play.api.Configuration
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace
import play.api.libs.ws.writeableOf_JsValue

@Singleton
class SdesConnector @Inject() (
                                httpClient: HttpClientV2,
                                configuration: Configuration,
                                sdesCircuitBreaker: SdesCircuitBreaker
                              )(using ExecutionContext) extends Logging {

  private val service: Service = configuration.get[Service]("microservice.services.sdes")
  private val clientId: String = configuration.get[String]("sdes.client-id")

  def notify(request: FileNotifyRequest)(using HeaderCarrier): Future[Done] = sdesCircuitBreaker.breaker.withCircuitBreaker {
    httpClient
      .post(url"$service/notification/fileready")
      .withBody(Json.toJson(request))
      .setHeader("x-client-id" -> clientId)
      .execute
      .flatMap { response =>
        if (response.status == NO_CONTENT) {
          Future.successful(Done)
        } else {
          val exception = SdesConnector.UnexpectedResponseException(response.status, response.body)
          logger.warn("Unexpected response", exception)
          Future.failed(exception)
        }
      }
  }
}

object SdesConnector {

  final case class UnexpectedResponseException(status: Int, body: String) extends Exception with NoStackTrace {
    override def getMessage: String = s"Unexpected response from SDES, status: $status, body: $body"
  }

  final case class SdesCircuitBreaker(breaker: CircuitBreaker)
}