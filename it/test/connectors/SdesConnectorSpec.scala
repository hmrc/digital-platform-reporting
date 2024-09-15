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

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.http.Fault
import connectors.SdesConnector.SdesCircuitBreaker
import models.sdes.{FileAudit, FileChecksum, FileMetadata, FileNotifyRequest}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Application
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NO_CONTENT}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.running
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.http.test.WireMockSupport

import scala.concurrent.Promise
import scala.concurrent.duration.*

class SdesConnectorSpec extends AnyFreeSpec with Matchers with ScalaFutures with IntegrationPatience with GuiceOneAppPerTest with WireMockSupport {

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.sdes.port" -> wireMockPort,
        "microservice.services.sdes.basePath" -> "",
        "sdes.client-id" -> "client-id",
        "sdes.max-failures" -> 1,
        "sdes.reset-timeout" -> "1 second",
        "sdes.call-timeout" -> "30 seconds"
      )
      .build()

  "notify" - {

    val hc = HeaderCarrier()
    val url = "/notification/fileready"

    val request = FileNotifyRequest(
      "fraud-reporting",
      FileMetadata(
        "tax-fraud-reporting",
        "file1.dat",
        url"http://localhost:8464/object-store/object/tax-fraud-reporting/file1.dat",
        FileChecksum("md5", value = "hashValue"),
        2000,
        List()
      ),
      FileAudit("uuid")
    )

    "must return Done when SDES responds with NO_CONTENT" in {

      val connector = app.injector.instanceOf[SdesConnector]

      wireMockServer.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request))))
          .withHeader("x-client-id", equalTo("client-id"))
          .willReturn(aResponse().withStatus(NO_CONTENT))
      )

      connector.notify(request)(hc).futureValue
    }

    "must return a failed future when SDES responds with anything else" in {

      val connector = app.injector.instanceOf[SdesConnector]

      wireMockServer.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request))))
          .withHeader("x-client-id", equalTo("client-id"))
          .willReturn(aResponse().withBody("body").withStatus(INTERNAL_SERVER_ERROR))
      )

      val exception = connector.notify(request)(hc).failed.futureValue
      exception mustEqual SdesConnector.UnexpectedResponseException(500, "body")
    }

    "must return a failed future when there is a connection error" in {
      val connector = app.injector.instanceOf[SdesConnector]

      wireMockServer.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request))))
          .withHeader("x-client-id", equalTo("client-id"))
          .willReturn(aResponse().withFault(Fault.RANDOM_DATA_THEN_CLOSE))
      )

      connector.notify(request)(hc).failed.futureValue
    }

    "must call the correct endpoint when there is an extra path part configured" in {

      val app = GuiceApplicationBuilder()
        .configure(
          "microservice.services.sdes.port" -> wireMockPort,
          "microservice.services.sdes.basePath" -> "/sdes-stub",
          "services.sdes.client-id" -> "client-id"
        )
        .build()

      running(app) {

        val connector = app.injector.instanceOf[SdesConnector]

        wireMockServer.stubFor(
          post(urlMatching(s"/sdes-stub$url"))
            .withRequestBody(equalToJson(Json.stringify(Json.toJson(request))))
            .withHeader("x-client-id", equalTo("client-id"))
            .willReturn(aResponse().withStatus(NO_CONTENT))
        )

        connector.notify(request)(hc).futureValue
      }
    }

    "must fail fast when the circuit breaker is open" in {

      val connector = app.injector.instanceOf[SdesConnector]
      val circuitBreaker = app.injector.instanceOf[SdesCircuitBreaker].breaker

      circuitBreaker.resetTimeout mustEqual 1.second

      wireMockServer.stubFor(
        post(urlMatching(url))
          .withRequestBody(equalToJson(Json.stringify(Json.toJson(request))))
          .withHeader("x-client-id", equalTo("client-id"))
          .willReturn(aResponse().withBody("body").withStatus(INTERNAL_SERVER_ERROR))
      )

      val onOpen = Promise[Unit]
      circuitBreaker.onOpen(onOpen.success(System.currentTimeMillis()))

      circuitBreaker.isOpen mustBe false
      connector.notify(request)(hc).failed.futureValue
      onOpen.future.futureValue
      circuitBreaker.isOpen mustBe true
      connector.notify(request)(hc).failed.futureValue

      wireMockServer.verify(1, postRequestedFor(urlMatching(url)))
    }
  }
}
