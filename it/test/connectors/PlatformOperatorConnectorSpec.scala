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
import models.operator.{AddressDetails, ContactDetails}
import models.operator.requests.CreatePlatformOperatorRequest
import models.operator.responses.PlatformOperatorCreatedResponse
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import services.UuidService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport

import java.time.{Clock, LocalDateTime, ZoneId, ZoneOffset}
import java.util.UUID

class PlatformOperatorConnectorSpec extends AnyFreeSpec
  with Matchers
  with ScalaFutures
  with IntegrationPatience
  with WireMockSupport
  with MockitoSugar
  with BeforeAndAfterEach
  with EitherValues {

  private val instant = LocalDateTime.of(2000, 1, 2, 3, 4, 5).toInstant(ZoneOffset.UTC)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)
  private val mockUuidService = mock[UuidService]

  override def beforeEach(): Unit = {
    Mockito.reset(mockUuidService)
    super.beforeEach()
  }


  private lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      bind[Clock].toInstance(stubClock),
      bind[UuidService].toInstance(mockUuidService)
    )
    .configure("microservice.services.update-platform-operator.port" -> wireMockPort)
    .configure("microservice.services.update-platform-operator.bearer-token" -> "updatePlatformOperatorToken")
    .build()

  private val correlationId = UUID.randomUUID()
  private val conversationId = UUID.randomUUID()

  private lazy val connector = app.injector.instanceOf[PlatformOperatorConnector]

  implicit private lazy val hc: HeaderCarrier = HeaderCarrier()
  
  ".create" - {
    
    "must post a request" - {
      
      "and return the response when the server returns OK" in {
        
        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)
        
        val request = CreatePlatformOperatorRequest(
          subscriptionId = "dprsid",
          operatorName = "foo",
          tinDetails = Seq.empty,
          businessName = None,
          tradingName = None,
          primaryContactDetails = ContactDetails(None, "name", "email"),
          secondaryContactDetails = None,
          addressDetails = AddressDetails("line 1", None, None, None, None, None)
        )

        val responsePayload = Json.obj(
          "success" -> Json.obj(
            "processingDate" -> "2000-01-02T03:04:56Z",
            "ReturnParameters" -> Json.obj(
              "Key" -> "POID",
              "Value" -> "123"
            )
          )
        )
        val expectedResponse = PlatformOperatorCreatedResponse("123")

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/dprs9301/v1"))
            .withHeader("Authorization", equalTo("Bearer updatePlatformOperatorToken"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withHeader("Date", equalTo("Sun, 02 Jan 2000 03:04:05 UTC"))
            .withRequestBody(equalTo(Json.toJson(request)(CreatePlatformOperatorRequest.downstreamWrites).toString))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(responsePayload.toString)
            )
        )

        val result = connector.create(request).futureValue

        result mustEqual expectedResponse
      }
      
      "and return a failed future when the server returns an error" in {

        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)

        val request = CreatePlatformOperatorRequest(
          subscriptionId = "dprsid",
          operatorName = "foo",
          tinDetails = Seq.empty,
          businessName = None,
          tradingName = None,
          primaryContactDetails = ContactDetails(None, "name", "email"),
          secondaryContactDetails = None,
          addressDetails = AddressDetails("line 1", None, None, None, None, None)
        )

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/dprs9301/v1"))
            .withHeader("Authorization", equalTo("Bearer updatePlatformOperatorToken"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withHeader("Date", equalTo("Sun, 02 Jan 2000 03:04:05 UTC"))
            .withRequestBody(equalTo(Json.toJson(request)(CreatePlatformOperatorRequest.downstreamWrites).toString))
            .willReturn(serverError())
        )

        connector.create(request).failed.futureValue
      }
    }
  }
}
