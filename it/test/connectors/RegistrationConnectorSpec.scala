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
import connectors.RegistrationConnectorExceptions.*
import models.registration.Address
import models.registration.requests.*
import models.registration.responses.*
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

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import java.util.UUID

class RegistrationConnectorSpec
  extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with WireMockSupport
    with MockitoSugar
    with BeforeAndAfterEach
    with EitherValues {

  private val instant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)
  private val mockUuidService = mock[UuidService]

  override def beforeEach(): Unit = {
    Mockito.reset(mockUuidService)
    super.beforeEach()
  }

  private lazy val app: Application =
    new GuiceApplicationBuilder()
      .overrides(
        bind[Clock].toInstance(stubClock),
        bind[UuidService].toInstance(mockUuidService)
      )
      .configure("microservice.services.register-with-id.port" -> wireMockPort)
      .configure("microservice.services.register-with-id.bearerToken" -> "token")
      .configure("microservice.services.register-without-id.port" -> wireMockPort)
      .configure("microservice.services.register-without-id.bearerToken" -> "token")
      .build()

  private val correlationId = UUID.randomUUID()
  private val conversationId = UUID.randomUUID()
  private val acknowledgementReference = UUID.randomUUID()

  private lazy val connector = app.injector.instanceOf[RegistrationConnector]

  implicit private lazy val hc: HeaderCarrier = HeaderCarrier()

  ".registerWithId" - {

    "must post a request" - {

      "and return the response when the server returns OK" in {

        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)

        val requestCommon = RequestCommon(instant, acknowledgementReference.toString)
        val requestDetail = OrganisationWithUtr("utr", None)
        val requestWithId = RequestWithId(requestCommon, requestDetail)

        val responseCommon = ResponseCommon("OK")
        val address = Address("addressLine1", None, None, None, Some("postcode"), "GB")
        val responseDetailWithId = ResponseDetailWithId("safeId", address, None)
        val expectedResponse = ResponseWithId(responseCommon, responseDetailWithId)

        val responsePayload = Json.obj(
          "registerWithIDResponse" -> Json.obj(
            "responseCommon" -> Json.obj(
              "status" -> "OK"
            ),
            "responseDetail" -> Json.obj(
              "SAFEID" -> "safeId",
              "ARN" -> "arn",
              "address" -> Json.obj(
                "addressLine1" -> "addressLine1",
                "postalCode" -> "postcode",
                "countryCode" -> "GB"
              )
            )
          )
        )

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/DPRS0102/v1"))
            .withHeader("Authorization", equalTo("Bearer token"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withRequestBody(equalTo(Json.toJson(requestWithId).toString))
            .willReturn(ok(responsePayload.toString))
        )

        val result = connector.registerWithId(requestWithId).futureValue

        result.value mustEqual expectedResponse
      }

      "and return not found when the server returns NOT_FOUND" in {

        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)

        val requestCommon = RequestCommon(instant, acknowledgementReference.toString)
        val requestDetail = OrganisationWithUtr("utr", None)
        val requestWithId = RequestWithId(requestCommon, requestDetail)

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/DPRS0102/v1"))
            .withHeader("Authorization", equalTo("Bearer token"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withRequestBody(equalTo(Json.toJson(requestWithId).toString))
            .willReturn(notFound())
        )

        val result = connector.registerWithId(requestWithId).futureValue

        result.left.value mustEqual NotFound
      }

      "and return an error when the server returns an error response" in {

        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)

        val requestCommon = RequestCommon(instant, acknowledgementReference.toString)
        val requestDetail = OrganisationWithUtr("utr", None)
        val requestWithId = RequestWithId(requestCommon, requestDetail)

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/DPRS0102/v1"))
            .withHeader("Authorization", equalTo("Bearer token"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withRequestBody(equalTo(Json.toJson(requestWithId).toString))
            .willReturn(serverError())
        )

        val result = connector.registerWithId(requestWithId).futureValue

        result.left.value mustEqual UnexpectedResponse(500)
      }

      "and return an error when the server returns a payload that cannot be parsed" in {

        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)

        val requestCommon = RequestCommon(instant, acknowledgementReference.toString)
        val requestDetail = OrganisationWithUtr("utr", None)
        val requestWithId = RequestWithId(requestCommon, requestDetail)

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/DPRS0102/v1"))
            .withHeader("Authorization", equalTo("Bearer token"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withRequestBody(equalTo(Json.toJson(requestWithId).toString))
            .willReturn(ok(Json.obj().toString))
        )

        val result = connector.registerWithId(requestWithId).futureValue

        result.left.value mustEqual UnableToParseResponse
      }
    }
  }

  ".registerWithoutId" - {

    "must post a request" - {

      "and return the response when the server returns OK" in {

        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)

        val requestCommon = RequestCommon(instant, acknowledgementReference.toString)
        val address = Address("addressLine1", None, None, None, Some("postcode"), "GB")
        val requestDetail = OrganisationWithoutId("name", address)
        val requestWithoutId = RequestWithoutId(requestCommon, requestDetail)

        val responseCommon = ResponseCommon("OK")
        val responseDetailWithoutId = ResponseDetailWithoutId("safeId")
        val expectedResponse = ResponseWithoutId(responseCommon, responseDetailWithoutId)

        val responsePayload = Json.obj(
          "registerWithIDResponse" -> Json.obj(
            "responseCommon" -> Json.obj(
              "status" -> "OK"
            ),
            "responseDetail" -> Json.obj(
              "SAFEID" -> "safeId",
              "ARN" -> "arn",
              "address" -> Json.obj(
                "addressLine1" -> "addressLine1",
                "postalCode" -> "postcode",
                "countryCode" -> "GB"
              ),
              "organisation" -> Json.obj(
                "organisationName" -> "name"
              )
            )
          )
        )

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/DPRS0101/v1"))
            .withHeader("Authorization", equalTo("Bearer token"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withRequestBody(equalTo(Json.toJson(requestWithoutId).toString))
            .willReturn(ok(responsePayload.toString))
        )

        val result = connector.registerWithoutId(requestWithoutId).futureValue

        result.value mustEqual expectedResponse
      }

      "and return not found when the server returns NOT_FOUND" in {

        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)

        val requestCommon = RequestCommon(instant, acknowledgementReference.toString)
        val address = Address("addressLine1", None, None, None, Some("postcode"), "GB")
        val requestDetail = OrganisationWithoutId("name", address)
        val requestWithoutId = RequestWithoutId(requestCommon, requestDetail)

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/DPRS0101/v1"))
            .withHeader("Authorization", equalTo("Bearer token"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withRequestBody(equalTo(Json.toJson(requestWithoutId).toString))
            .willReturn(notFound())
        )

        val result = connector.registerWithoutId(requestWithoutId).futureValue

        result.left.value mustEqual NotFound
      }

      "and return an error when the server returns an error response" in {

        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)

        val requestCommon = RequestCommon(instant, acknowledgementReference.toString)
        val address = Address("addressLine1", None, None, None, Some("postcode"), "GB")
        val requestDetail = OrganisationWithoutId("name", address)
        val requestWithoutId = RequestWithoutId(requestCommon, requestDetail)

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/DPRS0101/v1"))
            .withHeader("Authorization", equalTo("Bearer token"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withRequestBody(equalTo(Json.toJson(requestWithoutId).toString))
            .willReturn(serverError())
        )

        val result = connector.registerWithoutId(requestWithoutId).futureValue

        result.left.value mustEqual UnexpectedResponse(500)
      }

      "and return an error when the server returns a payload that cannot be parsed" in {

        when(mockUuidService.generate())
          .thenReturn(correlationId.toString, conversationId.toString)

        val requestCommon = RequestCommon(instant, acknowledgementReference.toString)
        val address = Address("addressLine1", None, None, None, Some("postcode"), "GB")
        val requestDetail = OrganisationWithoutId("name", address)
        val requestWithoutId = RequestWithoutId(requestCommon, requestDetail)

        wireMockServer.stubFor(
          post(urlMatching(".*/dac6/DPRS0101/v1"))
            .withHeader("Authorization", equalTo("Bearer token"))
            .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
            .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("Accept", equalTo("application/json"))
            .withRequestBody(equalTo(Json.toJson(requestWithoutId).toString))
            .willReturn(ok(Json.obj().toString))
        )

        val result = connector.registerWithoutId(requestWithoutId).futureValue

        result.left.value mustEqual UnableToParseResponse
      }
    }
  }
}
