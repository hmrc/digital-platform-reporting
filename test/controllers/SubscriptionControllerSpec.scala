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
import controllers.actions.AuthAction
import models.AuthenticatedRequest
import models.subscription.*
import models.subscription.requests.*
import models.subscription.responses.*
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.{BodyParsers, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.AuthConnector

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubscriptionControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach {

  private val mockConnector = mock[SubscriptionConnector]
  private val dprsId = "dprs id"

  override def beforeEach(): Unit = {
    Mockito.reset(mockConnector)
    super.beforeEach()
  }

  ".subscribe" - {

    "must return OK and the response detail" - {

      "when a subscription call was successful" in {

        val app =
          new GuiceApplicationBuilder()
            .overrides(bind[SubscriptionConnector].toInstance(mockConnector))
            .build()
          
        val individual = IndividualContact(Individual("first", "last"), "email", None)
        val subscriptionRequest = SubscriptionRequest("userId", true, None, individual, None)
        val subscriptionResponse = SubscribedResponse("dprs id")
        val payload = Json.obj(
          "id" -> "userId",
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

        running(app) {

          val request =
            FakeRequest(routes.SubscriptionController.subscribe())
              .withJsonBody(payload)

          val result = route(app, request).value

          status(result) mustEqual OK
          contentAsJson(result) mustEqual Json.toJson(subscriptionResponse)
          verify(mockConnector, times(1)).subscribe(eqTo(subscriptionRequest))(any())
        }
      }
    }

    "must return CONFLICT when the server returns duplicate submission" in {

      val app =
        new GuiceApplicationBuilder()
          .overrides(bind[SubscriptionConnector].toInstance(mockConnector))
          .build()

      val individual = IndividualContact(Individual("first", "last"), "email", None)
      val subscriptionRequest = SubscriptionRequest("userId", true, None, individual, None)
      val payload = Json.obj(
        "id" -> "userId",
        "gbUser" -> true,
        "primaryContact" -> Json.obj(
          "individual" -> Json.obj(
            "firstName" -> "first",
            "lastName" -> "last"
          ),
          "email" -> "email"
        )
      )

      when(mockConnector.subscribe(any())(any())).thenReturn(Future.successful(AlreadySubscribedResponse))

      running(app) {

        val request =
          FakeRequest(routes.SubscriptionController.subscribe())
            .withJsonBody(payload)

        val result = route(app, request).value

        status(result) mustEqual CONFLICT
        verify(mockConnector, times(1)).subscribe(eqTo(subscriptionRequest))(any())
      }
    }

    "must return INTERNAL_SERVER_ERROR when the server returns an unexpected error" in {

      val app =
        new GuiceApplicationBuilder()
          .overrides(bind[SubscriptionConnector].toInstance(mockConnector))
          .build()

      val individual = IndividualContact(Individual("first", "last"), "email", None)
      val subscriptionRequest = SubscriptionRequest("userId", true, None, individual, None)
      val payload = Json.obj(
        "id" -> "userId",
        "gbUser" -> true,
        "primaryContact" -> Json.obj(
          "individual" -> Json.obj(
            "firstName" -> "first",
            "lastName" -> "last"
          ),
          "email" -> "email"
        )
      )

      when(mockConnector.subscribe(any())(any())).thenReturn(Future.successful(UnexpectedResponse("")))

      running(app) {

        val request =
          FakeRequest(routes.SubscriptionController.subscribe())
            .withJsonBody(payload)

        val result = route(app, request).value

        status(result) mustEqual INTERNAL_SERVER_ERROR
        verify(mockConnector, times(1)).subscribe(eqTo(subscriptionRequest))(any())
      }
    }

    "must fail" - {

      "when a request to the backend fails" in {

        val app =
          new GuiceApplicationBuilder()
            .overrides(bind[SubscriptionConnector].toInstance(mockConnector))
            .build()
          
        when(mockConnector.subscribe(any())(any())).thenReturn(Future.failed(new Exception("foo")))
        
        val payload = Json.obj(
          "id" -> "userId",
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

        running(app) {
          route(app, request).value.failed
        }
      }
    }
  }

  ".updateSubscription" - {

    "must return OK" - {

      "when an update subscription call was successful" in {

        val app =
          new GuiceApplicationBuilder()
            .overrides(bind[SubscriptionConnector].toInstance(mockConnector))
            .build()

        val individual = IndividualContact(Individual("first", "last"), "email", None)
        val subscriptionRequest = SubscriptionRequest("userId", true, None, individual, None)
        val payload = Json.obj(
          "id" -> "userId",
          "gbUser"  -> true,
          "primaryContact" -> Json.obj(
            "individual" -> Json.obj(
              "firstName" -> "first",
              "lastName" -> "last"
            ),
            "email" -> "email"
          )
        )

        when(mockConnector.updateSubscription(any())(any())).thenReturn(Future.successful(Done))

        val request =
          FakeRequest(routes.SubscriptionController.updateSubscription())
            .withJsonBody(payload)

        running(app) {

          val result = route(app, request).value

          status(result) mustEqual OK
          verify(mockConnector, times(1)).updateSubscription(eqTo(subscriptionRequest))(any())
        }
      }
    }

    "must fail" - {

      "when a request to the backend fails" in {

        val app =
          new GuiceApplicationBuilder()
            .overrides(bind[SubscriptionConnector].toInstance(mockConnector))
            .build()

        when(mockConnector.updateSubscription(any())(any())).thenReturn(Future.failed(new Exception("foo")))

        val payload = Json.obj(
          "id" -> "userId",
          "gbUser"  -> false,
          "primaryContact" -> Json.obj(
            "individual" -> Json.obj(
              "firstName" -> "first",
              "lastName" -> "last"
            ),
            "email" -> "email"
          )
        )

        running(app) {

          val request =
            FakeRequest(routes.SubscriptionController.updateSubscription())
              .withJsonBody(payload)

          route(app, request).value.failed
        }
      }
    }
  }

  ".get" - {
    
    "must return the user's subscription info when the user is authenticated and the server returns OK" in {

      val app =
        new GuiceApplicationBuilder()
          .overrides(
            bind[SubscriptionConnector].toInstance(mockConnector),
            bind[AuthAction].toInstance(new FakeAuthAction)
          )
          .build()
        
      val contact = OrganisationContact(Organisation("name"), "email", None)
      val subscriptionInfo = SubscriptionInfo(dprsId, true, None, contact, None)
      when(mockConnector.get(eqTo(dprsId))(any())).thenReturn(Future.successful(subscriptionInfo))
      
      val request = FakeRequest(GET, routes.SubscriptionController.get().url)

      running(app) {

        val result = route(app, request).value
        status(result) mustEqual OK
      }
    }
  }
}

class FakeAuthAction @Inject() extends AuthAction(mock[AuthConnector], mock[BodyParsers.Default]) {

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] =
    block(AuthenticatedRequest(request, "dprs id"))
}
