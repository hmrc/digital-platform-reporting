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
import connectors.SubscriptionConnector.UpdateSubscriptionFailure
import models.subscription.*
import models.subscription.requests.SubscriptionRequest
import models.subscription.responses.*
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, EitherValues}
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

class SubscriptionConnectorSpec extends AnyFreeSpec
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
    .configure("microservice.services.subscribe.port" -> wireMockPort)
    .configure("microservice.services.subscribe.bearerTokens.userSubscription" -> "createSubscriptionToken")
    .configure("microservice.services.subscribe.bearerTokens.updateContacts" -> "updateSubscriptionToken")
    .configure("microservice.services.subscribe.bearerTokens.readContacts" -> "getSubscriptionToken")
    .build()

  private val correlationId = UUID.randomUUID()
  private val conversationId = UUID.randomUUID()

  private lazy val connector = app.injector.instanceOf[SubscriptionConnector]

  implicit private lazy val hc: HeaderCarrier = HeaderCarrier()

  ".subscribe" - {
    "must post a request" - {
      "and return the response when the server returns CREATED" in {
        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)

        val request = SubscriptionRequest("safe id", true, None, OrganisationContact(Organisation("name"), "email", None), None)
        val responsePayload = Json.obj(
          "success" -> Json.obj(
            "processingDate" -> "2000-01-02T03:04:56Z",
            "dprsReference" -> "123"
          )
        )
        val expectedResponse = SubscribedResponse("123")

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/dprs0201/v1"))
            .withHeader("Authorization", equalTo("Bearer createSubscriptionToken"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withHeader("Date", equalTo("Sun, 02 Jan 2000 03:04:05 UTC"))
            .withRequestBody(equalTo(Json.toJson(request).toString))
            .willReturn(
              aResponse()
                .withStatus(201)
                .withBody(responsePayload.toString)
            )
        )

        val result = connector.subscribe(request).futureValue

        result mustEqual expectedResponse
      }

      "and return already subscribed when the server returns CONFLICT" in {
        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)

        val request = SubscriptionRequest("safe id", true, None, OrganisationContact(Organisation("name"), "email", None), None)

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/dprs0201/v1"))
            .withHeader("Authorization", equalTo("Bearer createSubscriptionToken"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withHeader("Date", equalTo("Sun, 02 Jan 2000 03:04:05 UTC"))
            .withRequestBody(equalTo(Json.toJson(request).toString))
            .willReturn(aResponse().withStatus(409))
        )

        val result = connector.subscribe(request).futureValue

        result mustEqual AlreadySubscribedResponse
      }

      "and return a failed future when the server returns an error" in {
        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)

        val request = SubscriptionRequest("safe id", true, None, OrganisationContact(Organisation("name"), "email", None), None)

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/dprs0201/v1"))
            .withHeader("Authorization", equalTo("Bearer token"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withHeader("Date", equalTo("Sun, 02 Jan 2000 03:04:05 UTC"))
            .withRequestBody(equalTo(Json.toJson(request).toString))
            .willReturn(serverError())
        )

        connector.subscribe(request).failed.futureValue
      }
    }
  }

  ".updateSubscription" - {
    "must post a request" - {
      "and return done when the server returns OK" in {
        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)

        val request = SubscriptionRequest("safe id", true, None, OrganisationContact(Organisation("name"), "email", None), None)
        val responsePayload = Json.obj(
          "success" -> Json.obj(
            "processingDate" -> "2000-01-02T03:04:56Z",
            "dprsReference" -> "123"
          )
        )

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/dprs0203/v1"))
            .withHeader("Authorization", equalTo("Bearer updateSubscriptionToken"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withHeader("Date", equalTo("Sun, 02 Jan 2000 03:04:05 UTC"))
            .withRequestBody(equalTo(Json.toJson(request).toString))
            .willReturn(
              aResponse()
                .withStatus(200)
                .withBody(responsePayload.toString)
            )
        )

        connector.updateSubscription(request).futureValue
      }

      "and return a failed future when the server returns an error" in {
        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)

        val request = SubscriptionRequest("safe id", true, None, OrganisationContact(Organisation("name"), "email", None), None)

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/dprs0203/v1"))
            .withHeader("Authorization", equalTo("Bearer updateSubscriptionToken"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withHeader("Date", equalTo("Sun, 02 Jan 2000 03:04:05 UTC"))
            .withRequestBody(equalTo(Json.toJson(request).toString))
            .willReturn(serverError())
        )

        val result = connector.updateSubscription(request).failed.futureValue
        result mustBe a[SubscriptionConnector.UpdateSubscriptionFailure]

        val updateSubscriptionFailure = result.asInstanceOf[UpdateSubscriptionFailure]
        updateSubscriptionFailure.correlationId mustEqual correlationId.toString
        updateSubscriptionFailure.status mustEqual 500
      }
    }
  }

  ".get" - {
    "must return subscription info when the server returns OK" in {
      when(mockUuidService.generate())
        .thenReturn(correlationId.toString, conversationId.toString)

      val responsePayload = Json.obj(
        "success" -> Json.obj(
          "processingDate" -> "2000-01-02T03:04:56Z",
          "customer" -> Json.obj(
            "id" -> "DPRS123",
            "gbUser" -> true,
            "primaryContact" -> Json.obj(
              "individual" -> Json.obj(
                "firstName" -> "first",
                "lastName" -> "last"
              ),
              "email" -> "email"
            )
          )
        )
      )
      val expectedIndividual = IndividualContact(Individual("first", "last"), "email", None)
      val expectedResponse = SubscriptionInfo("DPRS123", true, None, expectedIndividual, None)

      wireMockServer.stubFor(
        get(urlMatching(".*/dac6/dprs0202/v1/DPRS123"))
          .withHeader("Authorization", equalTo("Bearer getSubscriptionToken"))
          .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
          .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
          .withHeader("Accept", equalTo("application/json"))
          .withHeader("Date", equalTo("Sun, 02 Jan 2000 03:04:05 UTC"))
          .willReturn(ok(responsePayload.toString))
      )

      val result = connector.get("DPRS123").futureValue

      result mustEqual expectedResponse
    }
  }
}
