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

import connectors.PlatformOperatorConnector
import controllers.actions.{AuthAction, FakeAuthAction}
import models.operator.*
import models.operator.requests.*
import models.operator.responses.*
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import scala.concurrent.Future

class PlatformOperatorControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach {

  private val mockConnector = mock[PlatformOperatorConnector]

  override def beforeEach(): Unit = {
    Mockito.reset(mockConnector)
    super.beforeEach()
  }

  ".create" - {

    "must create a platform operator and return OK" in {

      val app =
        new GuiceApplicationBuilder()
          .overrides(
            bind[PlatformOperatorConnector].toInstance(mockConnector),
            bind[AuthAction].toInstance(new FakeAuthAction)
          )
          .build()

      val creationRequest = CreatePlatformOperatorRequest(
        subscriptionId = "dprsid",
        operatorName = "foo",
        tinDetails = Seq.empty,
        businessName = None,
        tradingName = None,
        primaryContactDetails = ContactDetails(None, "name", "email"),
        secondaryContactDetails = None,
        addressDetails = AddressDetails("line 1", None, None, None, None, None)
      )
      val creationResponse = PlatformOperatorCreatedResponse("po id")

      when(mockConnector.create(any())(any())).thenReturn(Future.successful(creationResponse))

      running(app) {

        val request =
          FakeRequest(routes.PlatformOperatorController.create())
            .withJsonBody(Json.toJson(creationRequest)(CreatePlatformOperatorRequest.defaultFormat))

        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(creationResponse)
        verify(mockConnector, times(1)).create(eqTo(creationRequest))(any())
      }
    }
  }

  ".update" - {

    "must update a platform operator and return OK with the response detail" in {
      val app =
        new GuiceApplicationBuilder()
          .overrides(
            bind[PlatformOperatorConnector].toInstance(mockConnector),
            bind[AuthAction].toInstance(new FakeAuthAction)
          )
          .build()

      val updateRequest = UpdatePlatformOperatorRequest(
        subscriptionId = "dprsid",
        operatorId = "operatorid",
        operatorName = "foo",
        tinDetails = Seq.empty,
        businessName = None,
        tradingName = None,
        primaryContactDetails = ContactDetails(None, "name", "email"),
        secondaryContactDetails = None,
        addressDetails = AddressDetails("line 1", None, None, None, None, None),
        notification = None
      )

      when(mockConnector.update(any())(any())).thenReturn(Future.successful(Done))

      running(app) {

        val request =
          FakeRequest(routes.PlatformOperatorController.update())
            .withJsonBody(Json.toJson(updateRequest)(UpdatePlatformOperatorRequest.defaultFormat))

        val result = route(app, request).value

        status(result) mustEqual OK
        verify(mockConnector, times(1)).update(eqTo(updateRequest))(any())
      }
    }
  }

  ".delete" - {

    "must delete a platform operator and return OK" in {
      
      val app =
        new GuiceApplicationBuilder()
          .overrides(
            bind[PlatformOperatorConnector].toInstance(mockConnector),
            bind[AuthAction].toInstance(new FakeAuthAction)
          )
          .build()

      val expectedDeleteRequest = DeletePlatformOperatorRequest(
        subscriptionId = "dprs id",
        operatorId = "operatorid"
      )

      when(mockConnector.delete(any())(any())).thenReturn(Future.successful(Done))

      running(app) {

        val request = FakeRequest(routes.PlatformOperatorController.delete("operatorid"))

        val result = route(app, request).value

        status(result) mustEqual OK
        verify(mockConnector, times(1)).delete(eqTo(expectedDeleteRequest))(any())
      }
    }
  }

  ".get" - {

    "must return platform operator details" in {

      val app =
        new GuiceApplicationBuilder()
          .overrides(
            bind[PlatformOperatorConnector].toInstance(mockConnector),
            bind[AuthAction].toInstance(new FakeAuthAction)
          )
          .build()

      val platformOperatorResponse = ViewPlatformOperatorsResponse(platformOperators = Seq(
        PlatformOperator(
          operatorId = "operatorId",
          operatorName = "operatorName",
          tinDetails = Seq.empty,
          businessName = None,
          tradingName = None,
          primaryContactDetails = ContactDetails(None, "primaryContactName", "primaryEmail"),
          secondaryContactDetails = None,
          addressDetails = AddressDetails("line1", None, None, None, Some("postCode"), None),
          notifications = Seq.empty
        )
      ))

      when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(platformOperatorResponse)))

      running(app) {

        val request = FakeRequest(routes.PlatformOperatorController.get())
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(platformOperatorResponse)(ViewPlatformOperatorsResponse.defaultWrites)
        verify(mockConnector, times(1)).get(eqTo("dprs id"))(any())
      }
    }
    
    "must return NOT_FOUND when no platform operators exist for this subscription" in {

      val app =
        new GuiceApplicationBuilder()
          .overrides(
            bind[PlatformOperatorConnector].toInstance(mockConnector),
            bind[AuthAction].toInstance(new FakeAuthAction)
          )
          .build()

      when(mockConnector.get(any())(any())).thenReturn(Future.successful(None))

      running(app) {

        val request = FakeRequest(routes.PlatformOperatorController.get())
        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND
        verify(mockConnector, times(1)).get(eqTo("dprs id"))(any())
      }
    }
  }

  ".getOne" - {

    "must return a platform operator's details" in {

      val app =
        new GuiceApplicationBuilder()
          .overrides(
            bind[PlatformOperatorConnector].toInstance(mockConnector),
            bind[AuthAction].toInstance(new FakeAuthAction)
          )
          .build()

      val platformOperator = PlatformOperator(
        operatorId = "operatorId",
        operatorName = "operatorName",
        tinDetails = Seq.empty,
        businessName = None,
        tradingName = None,
        primaryContactDetails = ContactDetails(None, "primaryContactName", "primaryEmail"),
        secondaryContactDetails = None,
        addressDetails = AddressDetails("line1", None, None, None, Some("postCode"), None),
        notifications = Seq.empty
      )

      when(mockConnector.get(any(), any())(any())).thenReturn(Future.successful(Some(platformOperator)))

      running(app) {

        val request = FakeRequest(routes.PlatformOperatorController.getOne("operatorId"))
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(platformOperator)(PlatformOperator.defaultWrites)
        verify(mockConnector, times(1)).get(eqTo("dprs id"), eqTo("operatorId"))(any())
      }
    }

    "must return NOT_FOUND when the requested operator isn't in the returned response" in {

      val app =
        new GuiceApplicationBuilder()
          .overrides(
            bind[PlatformOperatorConnector].toInstance(mockConnector),
            bind[AuthAction].toInstance(new FakeAuthAction)
          )
          .build()

      when(mockConnector.get(any(), any())(any())).thenReturn(Future.successful(None))

      running(app) {

        val request = FakeRequest(routes.PlatformOperatorController.getOne("operatorId"))
        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND
        verify(mockConnector, times(1)).get(eqTo("dprs id"), eqTo("operatorId"))(any())
      }
    }
  }
}
