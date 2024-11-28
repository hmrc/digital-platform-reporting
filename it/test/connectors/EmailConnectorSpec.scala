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

import com.github.tomakehurst.wiremock.http.Fault
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.test.WireMockSupport
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, badRequest, post, urlMatching}
import play.api.http.Status.ACCEPTED
import support.builders.SendEmailRequestBuilder.aSendEmailRequest
import uk.gov.hmrc.http.HeaderCarrier

class EmailConnectorSpec extends AnyFreeSpec
  with Matchers
  with ScalaFutures
  with IntegrationPatience
  with WireMockSupport
  with MockitoSugar
  with BeforeAndAfterEach
  with EitherValues
  with OptionValues {

  private lazy val app: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.email.port" -> wireMockPort)
    .build()

  private lazy val underTest = app.injector.instanceOf[EmailConnector]

  implicit private lazy val hc: HeaderCarrier = HeaderCarrier()

  ".send" - {
    "must return success when when the server returns ACCEPTED" in {
      wireMockServer.stubFor(post(urlMatching("/hmrc/email"))
        .willReturn(aResponse.withStatus(ACCEPTED)))

      underTest.send(aSendEmailRequest).futureValue
    }

    "must return success when the server returns an error response" in {
      wireMockServer.stubFor(post(urlMatching("/hmrc/email"))
        .willReturn(badRequest()))

      underTest.send(aSendEmailRequest).futureValue
    }

    "must return success when sending email results in exception" in {
      wireMockServer.stubFor(post(urlMatching("/hmrc/email"))
        .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE)))

      underTest.send(aSendEmailRequest).futureValue
    }
  }
}
