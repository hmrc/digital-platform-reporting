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

import com.github.tomakehurst.wiremock.client.WireMock.*
import generated.{DPI_OECD, Generated_DPI_OECDFormat}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK, UNPROCESSABLE_ENTITY}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import services.UuidService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport
import utils.DateTimeFormats.RFC7231Formatter

import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID
import scala.xml.{Utility, XML}

class SubmissionConnectorSpec
  extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WireMockSupport
    with MockitoSugar
    with EitherValues
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach
    with OptionValues {

  private val now = Instant.now()
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val mockUuidService = mock[UuidService]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockUuidService)
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.report-submission.port" -> wireMockPort,
        "microservice.services.report-submission.bearer-token" -> "token",
        "microservice.services.get-manual-assumed-reporting-submission.port" -> wireMockPort,
        "microservice.services.get-manual-assumed-reporting-submission.bearer-token" -> "token"
      )
      .overrides(
        bind[Clock].toInstance(clock),
        bind[UuidService].toInstance(mockUuidService)
      )
      .build()

  private lazy val connector: SubmissionConnector = app.injector.instanceOf[SubmissionConnector]

  given Materializer = app.materializer

  "submit" - {

    val submissionId = "submissionId"
    val correlationId = UUID.randomUUID().toString
    val expectedDate = RFC7231Formatter.format(now.atZone(ZoneOffset.UTC))

    "must return successfully when the server responds with NO_CONTENT" in {

      wireMockServer.stubFor(
        post(urlPathEqualTo("/digital-platform-reporting-stubs/dac6/dprs0502/v1"))
          .withRequestBody(equalTo("foobar"))
          .withHeader("Authorization", equalTo("Bearer token"))
          .withHeader("X-Forwarded-Host", equalTo("digital-platform-reporting"))
          .withHeader("X-Correlation-ID", equalTo(correlationId))
          .withHeader("X-Conversation-ID", equalTo(submissionId))
          .withHeader("Content-Type", equalTo("application/xml"))
          .withHeader("Accept", equalTo("application/xml"))
          .withHeader("Date", equalTo(expectedDate))
          .willReturn(noContent())
      )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val body = Source(Seq(ByteString.fromString("foo"), ByteString.fromString("bar")))
      connector.submit(submissionId, body)(using HeaderCarrier()).futureValue
    }

    "must fail when the server responds with anything else" in {

      wireMockServer.stubFor(
        post(urlPathEqualTo("/digital-platform-reporting-stubs/dac6/dprs0502/v1"))
          .withRequestBody(equalTo("foobar"))
          .withHeader("Authorization", equalTo("Bearer token"))
          .withHeader("X-Forwarded-Host", equalTo("digital-platform-reporting"))
          .withHeader("X-Correlation-ID", equalTo(correlationId))
          .withHeader("X-Correlation-ID", equalTo(correlationId))
          .withHeader("X-Conversation-ID", equalTo(submissionId))
          .withHeader("Content-Type", equalTo("application/xml"))
          .withHeader("Accept", equalTo("application/xml"))
          .withHeader("Date", equalTo(expectedDate))
          .willReturn(aResponse().withStatus(INTERNAL_SERVER_ERROR))
      )

      when(mockUuidService.generate()).thenReturn(correlationId)

      val body = Source(Seq(ByteString.fromString("foo"), ByteString.fromString("bar")))
      connector.submit(submissionId, body)(using HeaderCarrier()).failed.futureValue
    }
  }

  "getManualAssumedReportingSubmission" - {

    val caseId = "caseId"
    val correlationId = UUID.randomUUID().toString
    val conversationId = UUID.randomUUID().toString
    val expectedDate = RFC7231Formatter.format(now.atZone(ZoneOffset.UTC))

    "must return the DPI_OECD body from the response when the server responds OK" in {

      val expectedBodySource = scala.io.Source.fromFile(getClass.getResource("/assumed/create/test.xml").toURI)
      val payload = expectedBodySource.mkString
      val expectedBody = scalaxb.fromXML[DPI_OECD](Utility.trim(XML.loadString(payload)))
      expectedBodySource.close()

      wireMockServer.stubFor(
        get(urlPathEqualTo("/digital-platform-reporting-stubs/dac6/dprs0504/v1/caseId"))
          .withHeader("Authorization", equalTo("Bearer token"))
          .withHeader("X-Forwarded-Host", equalTo("digital-platform-reporting"))
          .withHeader("X-Correlation-ID", equalTo(correlationId))
          .withHeader("X-Conversation-ID", equalTo(conversationId))
          .withHeader("Accept", equalTo("application/xml"))
          .withHeader("Date", equalTo(expectedDate))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(payload)
          )
      )

      when(mockUuidService.generate()).thenReturn(correlationId, conversationId)

      val result = connector.getManualAssumedReportingSubmission(caseId)(using HeaderCarrier()).futureValue
      result mustEqual expectedBody
    }

    "must fail when the server responds with anything else" in {

      wireMockServer.stubFor(
        get(urlPathEqualTo("/digital-platform-reporting-stubs/dac6/dprs0504/v1/caseId"))
          .withHeader("Authorization", equalTo("Bearer token"))
          .withHeader("X-Forwarded-Host", equalTo("digital-platform-reporting"))
          .withHeader("X-Correlation-ID", equalTo(correlationId))
          .withHeader("X-Conversation-ID", equalTo(conversationId))
          .withHeader("Accept", equalTo("application/xml"))
          .withHeader("Date", equalTo(expectedDate))
          .willReturn(
            aResponse()
              .withStatus(UNPROCESSABLE_ENTITY)
          )
      )

      when(mockUuidService.generate()).thenReturn(correlationId, conversationId)

      connector.getManualAssumedReportingSubmission(caseId)(using HeaderCarrier()).failed.futureValue
    }
  }
}
