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

package controllers

import connectors.RegistrationConnector
import controllers.actions.{FakeAuthWithoutEnrolmentAction, AuthWithoutEnrolmentAction}
import models.registration.Address
import models.registration.requests.*
import models.registration.responses.*
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import services.UuidService
import support.builders.ContactDetailsBuilder.aContactDetails

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import java.util.UUID
import scala.concurrent.Future

class RegistrationControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach {

  private val instant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)
  private val mockUuidService = mock[UuidService]
  private val mockConnector = mock[RegistrationConnector]

  override def beforeEach(): Unit = {
    Mockito.reset(mockUuidService, mockConnector)
    super.beforeEach()
  }

  private val app =
    new GuiceApplicationBuilder()
      .overrides(
        bind[Clock].toInstance(stubClock),
        bind[RegistrationConnector].toInstance(mockConnector),
        bind[UuidService].toInstance(mockUuidService),
        bind[AuthWithoutEnrolmentAction].toInstance(new FakeAuthWithoutEnrolmentAction)
      )
      .build()

  private val acknowledgementReferenceUuid = UUID.randomUUID().toString
  private val acknowledgementReference = acknowledgementReferenceUuid.replace("-", "")

  ".register" - {
    "must return OK and the response detail" - {
      "when a 'with Id` request was successfully matched" in {
        val requestDetail = OrganisationWithUtr("123", None)
        val expectedFullRequest = RequestWithId(
          RequestCommon(instant, acknowledgementReference),
          requestDetail
        )
        val responseDetail = ResponseDetailWithId("safeId", Address("addressLine1", None, None, None, Some("postcode"), "GB"), None)
        val fullResponse = MatchResponseWithId(
          ResponseCommon("OK"),
          responseDetail
        )

        when(mockConnector.registerWithId(any())(any())).thenReturn(Future.successful(fullResponse))
        when(mockUuidService.generate()).thenReturn(acknowledgementReferenceUuid)

        val payload = Json.obj(
          "type" -> "organisation",
          "utr" -> "123"
        )

        val request = FakeRequest(routes.RegistrationController.register())
          .withJsonBody(payload)

        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(responseDetail)
        verify(mockConnector, times(1)).registerWithId(eqTo(expectedFullRequest))(any())
      }

      "when a 'without Id` request was successfully matched" in {
        val contactDetails = aContactDetails.copy(emailAddress = "some.email@example.com", phoneNumber = Some("0123456"))
        val requestDetail = OrganisationWithoutId("name", Address("line 1", None, None, None, Some("postcode"), "GB"), contactDetails)
        val expectedFullRequest = RequestWithoutId(
          RequestCommon(instant, acknowledgementReference),
          requestDetail
        )

        val responseDetail = ResponseDetailWithoutId("safeId")
        val fullResponse = MatchResponseWithoutId(
          ResponseCommon("OK"),
          responseDetail
        )

        when(mockConnector.registerWithoutId(any())(any())).thenReturn(Future.successful(fullResponse))
        when(mockUuidService.generate()).thenReturn(acknowledgementReferenceUuid)

        val payload = Json.obj(
          "name" -> "name",
          "address" -> Json.obj(
            "addressLine1" -> "line 1",
            "postalCode" -> "postcode",
            "countryCode" -> "GB"
          ),
          "contactDetails" -> Json.obj(
            "emailAddress" -> "some.email@example.com",
            "phoneNumber" -> "0123456"
          )
        )

        val request = FakeRequest(routes.RegistrationController.register()).withJsonBody(payload)

        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(responseDetail)
        verify(mockConnector, times(1)).registerWithoutId(eqTo(expectedFullRequest))(any())
      }
    }

    "must return NotFound" - {
      "when a `with Id` request was not matched" in {
        when(mockConnector.registerWithId(any())(any())).thenReturn(Future.successful(NoMatchResponse))
        when(mockUuidService.generate()).thenReturn(acknowledgementReferenceUuid)

        val payload = Json.obj(
          "type" -> "organisation",
          "utr" -> "123"
        )

        val request = FakeRequest(routes.RegistrationController.register()).withJsonBody(payload)

        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND
      }

      "when a `without Id` request was not matched" in {
        when(mockConnector.registerWithoutId(any())(any())).thenReturn(Future.successful(NoMatchResponse))
        when(mockUuidService.generate()).thenReturn(acknowledgementReferenceUuid)

        val payload = Json.obj(
          "name" -> "name",
          "address" -> Json.obj(
            "addressLine1" -> "line 1",
            "postalCode" -> "postcode",
            "countryCode" -> "GB"
          ),
          "contactDetails" -> Json.obj(
            "emailAddress" -> "email"
          )
        )

        val request = FakeRequest(routes.RegistrationController.register()).withJsonBody(payload)

        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND
      }
    }

    "must return Conflict" - {
      "when a `with Id` request was  already subscribed" in {
        when(mockConnector.registerWithId(any())(any())).thenReturn(Future.successful(AlreadySubscribedResponse))
        when(mockUuidService.generate()).thenReturn(acknowledgementReferenceUuid)

        val payload = Json.obj(
          "type" -> "organisation",
          "utr" -> "123"
        )

        val request = FakeRequest(routes.RegistrationController.register()).withJsonBody(payload)

        val result = route(app, request).value

        status(result) mustEqual CONFLICT
      }

      "when a `without Id` request was already subscribed" in {
        when(mockConnector.registerWithoutId(any())(any())).thenReturn(Future.successful(AlreadySubscribedResponse))
        when(mockUuidService.generate()).thenReturn(acknowledgementReferenceUuid)

        val payload = Json.obj(
          "name" -> "name",
          "address" -> Json.obj(
            "addressLine1" -> "line 1",
            "postalCode" -> "postcode",
            "countryCode" -> "GB"
          ),
          "contactDetails" -> Json.obj(
            "emailAddress" -> "email"
          )
        )

        val request = FakeRequest(routes.RegistrationController.register()).withJsonBody(payload)

        val result = route(app, request).value

        status(result) mustEqual CONFLICT
      }
    }

    "must fail" - {
      "when a `with Id` request to the backend fails" in {
        when(mockConnector.registerWithId(any())(any())).thenReturn(Future.failed(new Exception("foo")))
        when(mockUuidService.generate()).thenReturn(acknowledgementReferenceUuid)

        val payload = Json.obj(
          "type" -> "organisation",
          "utr" -> "123"
        )

        val request = FakeRequest(routes.RegistrationController.register()).withJsonBody(payload)

        route(app, request).value.failed
      }

      "when a `without Id` request to the backend fails" in {
        when(mockConnector.registerWithoutId(any())(any())).thenReturn(Future.failed(new Exception("foo")))
        when(mockUuidService.generate()).thenReturn(acknowledgementReferenceUuid)

        val payload = Json.obj(
          "name" -> "name",
          "address" -> Json.obj(
            "addressLine1" -> "line 1",
            "postalCode" -> "postcode",
            "countryCode" -> "GB"
          ),
          "contact" -> Json.obj(
            "email" -> "email"
          )
        )

        val request = FakeRequest(routes.RegistrationController.register()).withJsonBody(payload)

        route(app, request).value.failed
      }
    }
  }
}
