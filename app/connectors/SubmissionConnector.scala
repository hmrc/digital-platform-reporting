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
import connectors.SubmissionConnector.{GetManualAssumedReportingSubmissionFailure, SubmissionFailed}
import generated.{DPI_OECD, Generated_DPI_OECDFormat}
import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.http.HeaderNames
import play.api.http.Status.{NO_CONTENT, OK}
import play.api.libs.ws.{BodyWritable, SourceBody}
import services.UuidService
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import utils.DateTimeFormats.RFC7231Formatter

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.{Utility, XML}

@Singleton
class SubmissionConnector @Inject()(
                                     httpClient: HttpClientV2,
                                     clock: Clock,
                                     appConfig: AppConfig,
                                     uuidService: UuidService
                                   )(using ExecutionContext) {

  private given BodyWritable[Source[ByteString, ?]] =
    BodyWritable(SourceBody.apply, "application/xml")

  def submit(submissionId: String, requestBody: Source[ByteString, ?])(using HeaderCarrier): Future[Done] = {

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

  def getManualAssumedReportingSubmission(submissionCaseId: String)(using HeaderCarrier): Future[DPI_OECD] = {

    val correlationId = uuidService.generate()
    val conversationId = uuidService.generate()

    httpClient.get(url"${appConfig.GetManualAssumedReportingSubmissionUrl}/dac6/dprs0504/v1/$submissionCaseId")
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer ${appConfig.GetManualAssumedReportingSubmissionToken}")
      .setHeader("X-Correlation-ID" -> correlationId)
      .setHeader("X-Conversation-ID" -> conversationId)
      .setHeader("X-Forwarded-Host" -> appConfig.AppName)
      .setHeader(HeaderNames.CONTENT_TYPE -> "application/xml")
      .setHeader(HeaderNames.ACCEPT -> "application/xml")
      .setHeader(HeaderNames.DATE -> RFC7231Formatter.format(clock.instant()))
      .execute[HttpResponse]
      .flatMap { response =>
        response.status match {
          case OK =>
            Future.successful(scalaxb.fromXML[DPI_OECD](Utility.trim(XML.loadString(response.body))))
          case _ =>
            Future.failed(GetManualAssumedReportingSubmissionFailure(submissionCaseId, response.status))
        }
      }
  }
}

object SubmissionConnector {

  final case class SubmissionFailed(submissionId: String) extends Throwable

  final case class GetManualAssumedReportingSubmissionFailure(submissionCaseId: String, status: Int) extends Throwable
}
