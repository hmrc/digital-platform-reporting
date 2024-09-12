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
import connectors.SubmissionConnector.SubmissionFailed
import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.http.HeaderNames
import play.api.http.Status.NO_CONTENT
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import play.api.libs.ws.{BodyWritable, SourceBody}
import services.UuidService
import utils.DateTimeFormats.RFC7231Formatter

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionConnector @Inject() (
                                      httpClient: HttpClientV2,
                                      clock: Clock,
                                      appConfig: AppConfig,
                                      uuidService: UuidService
                                    )(using ExecutionContext) {

  private given BodyWritable[Source[ByteString, _]] =
    BodyWritable(SourceBody.apply, "application/xml")

  def submit(submissionId: String, requestBody: Source[ByteString, _])(using HeaderCarrier): Future[Done] = {

    val correlationId = uuidService.generate()

    httpClient.post(url"${appConfig.SubmissionBaseUrl}/dac6/dprs0502/v1")
      .withBody(requestBody)
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer ${appConfig.SubmissionBearerToken}")
      .setHeader("X-Correlation-ID" -> correlationId)
      .setHeader("X-Conversation-ID" -> submissionId)
      .setHeader("X-Forwarded-Host" -> appConfig.AppName)
      .setHeader(HeaderNames.CONTENT_TYPE -> "application/xml")
      .setHeader(HeaderNames.ACCEPT -> "application/xml")
      .setHeader(HeaderNames.DATE -> RFC7231Formatter.format(clock.instant()))
      .execute[HttpResponse]
      .flatMap { response =>
        response.status match {
          case NO_CONTENT =>
            Future.successful(Done)
          case _ =>
            Future.failed(SubmissionFailed(submissionId))
        }
      }
  }
}

object SubmissionConnector {

  final case class SubmissionFailed(submissionId: String) extends Throwable
}