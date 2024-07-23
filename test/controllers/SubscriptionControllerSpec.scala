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

import connectors.SubscriptionConnector
import models.subscription.*
import models.subscription.requests.*
import models.subscription.responses.*
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import scala.concurrent.Future

class SubscriptionControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach {

  private val mockConnector = mock[SubscriptionConnector]

  override def beforeEach(): Unit = {
    Mockito.reset(mockConnector)
    super.beforeEach()
  }

  private val app =
    new GuiceApplicationBuilder()
      .overrides(bind[SubscriptionConnector].toInstance(mockConnector))
      .build()

  ".subscribe" - {

    "must return OK and the response detail" - {

      "when a subscription call was successful" in {

        val individual = IndividualContact(Individual("first", "last"), "email", None)
        val subscriptionRequest = SubscriptionRequest("safe", true, None, individual, None)
        val subscriptionResponse = SubscriptionResponse("dprs id")
        val payload = Json.obj(
          "safeId" -> "safe",
          "gbUser"  -> true,
          "primaryContact" -> Json.obj(
            "individual" -> Json.obj(
              "firstName" -> "first",
              "lastName" -> "last"
            ),
            "email" -> "email"
          )
        )
        
        when(mockConnector.subscribe(any())(any())).thenReturn(Future.successful(subscriptionResponse))

        val request =
          FakeRequest(routes.SubscriptionController.subscribe())
            .withJsonBody(payload)

        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(subscriptionResponse)
        verify(mockConnector, times(1)).subscribe(eqTo(subscriptionRequest))(any())
      }
    }

    "must fail" - {

      "when a request to the backend fails" in {

        when(mockConnector.subscribe(any())(any())).thenReturn(Future.failed(new Exception("foo")))
        
        val payload = Json.obj(
          "safeId" -> "safe",
          "gbUser"  -> false,
          "primaryContact" -> Json.obj(
            "individual" -> Json.obj(
              "firstName" -> "first",
              "lastName" -> "last"
            ),
            "email" -> "email"
          )
        )
        
        val request =
          FakeRequest(routes.SubscriptionController.subscribe())
            .withJsonBody(payload)

        route(app, request).value.failed
      }
    }
  }
}
