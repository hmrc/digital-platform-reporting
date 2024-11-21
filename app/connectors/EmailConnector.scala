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
import models.email.requests.SendEmailRequest
import org.apache.pekko.Done
import play.api.Logging
import play.api.http.Status.ACCEPTED
import play.api.libs.json.Json
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class EmailConnector @Inject()(appConfig: AppConfig, httpClient: HttpClientV2)
                              (implicit ec: ExecutionContext) extends Logging {

  def send(sendEmailRequest: SendEmailRequest)(implicit hc: HeaderCarrier): Future[Done] =
    httpClient.post(url"${appConfig.emailServiceUrl}/hmrc/email")
      .withBody(Json.toJson(sendEmailRequest))
      .execute[HttpResponse]
      .flatMap { response =>
        response.status match {
          case ACCEPTED => Future.successful(Done)
          case status =>
            logger.warn(s"Send email failed with status: $status")
            Future.successful(Done)
        }
      }.recoverWith {
        case NonFatal(e) =>
          logger.warn("Error sending email", e)
          Future.successful(Done)
      }
}