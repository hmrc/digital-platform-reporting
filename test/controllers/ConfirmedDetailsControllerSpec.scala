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

import controllers.actions.{AuthAction, FakeAuthAction}
import models.confirmed.requests.{ConfirmedBusinessDetailsRequest, ConfirmedReportingNotificationsRequest}
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repository.{ConfirmedBusinessDetailsRepository, ConfirmedContactDetailsRepository, ConfirmedReportingNotificationsRepository}
import support.SpecBase
import support.builders.ConfirmedBusinessDetailsBuilder.aConfirmedBusinessDetails
import support.builders.ConfirmedContactDetailsBuilder.aConfirmedContactDetails
import support.builders.ConfirmedReportingNotificationBuilder.aConfirmedReportingNotification

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.Future

class ConfirmedDetailsControllerSpec extends SpecBase
  with GuiceOneAppPerSuite
  with MockitoSugar
  with OptionValues
  with ScalaFutures
  with BeforeAndAfterEach {

  private val instant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)
  private val userId = "some-user-id"
  private val operatorId = "some-operator-id"

  private val mockConfirmedBusinessDetailsRepository = mock[ConfirmedBusinessDetailsRepository]
  private val mockConfirmedReportingNotificationsRepository = mock[ConfirmedReportingNotificationsRepository]
  private val mockConfirmedContactDetailsRepository = mock[ConfirmedContactDetailsRepository]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder().overrides(
      bind[AuthAction].toInstance(new FakeAuthAction(userId)),
      bind[ConfirmedBusinessDetailsRepository].toInstance(mockConfirmedBusinessDetailsRepository),
      bind[ConfirmedReportingNotificationsRepository].toInstance(mockConfirmedReportingNotificationsRepository),
      bind[ConfirmedContactDetailsRepository].toInstance(mockConfirmedContactDetailsRepository),
      bind[Clock].toInstance(stubClock)
    ).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      mockConfirmedBusinessDetailsRepository,
      mockConfirmedReportingNotificationsRepository,
      mockConfirmedContactDetailsRepository
    )
  }

  ".businessDetails(...)" - {
    "must return confirmed business details when matching item is found" in {
      when(mockConfirmedBusinessDetailsRepository.findBy(userId, operatorId)).thenReturn(Future.successful(Some(aConfirmedBusinessDetails)))

      val request = FakeRequest(routes.ConfirmedDetailsController.businessDetails(operatorId))
      val result = route(app, request).value

      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(aConfirmedBusinessDetails)
    }

    "must return NOT_FOUND when matching confirmed business details not found" in {
      when(mockConfirmedBusinessDetailsRepository.findBy(any(), any())).thenReturn(Future.successful(None))

      val request = FakeRequest(routes.ConfirmedDetailsController.businessDetails(operatorId))
      val result = route(app, request).value

      status(result) mustEqual NOT_FOUND
    }
  }

  ".saveBusinessDetails()" - {
    "must save confirmed contact details and return OK" in {
      val confirmationDetails = aConfirmedBusinessDetails.copy(userId = userId, operatorId = operatorId, created = instant)

      when(mockConfirmedBusinessDetailsRepository.save(any())).thenReturn(Future.successful(Done))

      val jsonRequestBody = Json.toJson(ConfirmedBusinessDetailsRequest(operatorId))
      val result = route(app, FakeRequest(routes.ConfirmedDetailsController.saveBusinessDetails()).withJsonBody(jsonRequestBody)).value

      status(result) mustBe OK

      verify(mockConfirmedBusinessDetailsRepository, times(1)).save(confirmationDetails)
    }
  }

  ".reportingNotifications(...)" - {
    "must return confirmed reporting notifications when matching item is found" in {
      when(mockConfirmedReportingNotificationsRepository.findBy(userId, operatorId))
        .thenReturn(Future.successful(Some(aConfirmedReportingNotification)))

      val request = FakeRequest(routes.ConfirmedDetailsController.reportingNotifications(operatorId))
      val result = route(app, request).value

      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(aConfirmedReportingNotification)
    }

    "must return NOT_FOUND when matching confirmed reporting notifications not found" in {
      when(mockConfirmedReportingNotificationsRepository.findBy(any(), any())).thenReturn(Future.successful(None))

      val request = FakeRequest(routes.ConfirmedDetailsController.reportingNotifications(operatorId))
      val result = route(app, request).value

      status(result) mustEqual NOT_FOUND
    }
  }

  ".saveReportingNotifications()" - {
    "must save confirmed reporting notifications and return OK" in {
      val confirmationDetails = aConfirmedReportingNotification.copy(userId = userId, operatorId = operatorId, created = instant)

      when(mockConfirmedReportingNotificationsRepository.save(any())).thenReturn(Future.successful(Done))

      val jsonRequestBody = Json.toJson(ConfirmedReportingNotificationsRequest(operatorId))
      val result = route(app, FakeRequest(routes.ConfirmedDetailsController.saveReportingNotifications()).withJsonBody(jsonRequestBody))
        .value

      status(result) mustBe OK

      verify(mockConfirmedReportingNotificationsRepository, times(1)).save(confirmationDetails)
    }
  }

  ".contactDetails()" - {
    "must return confirmed contact details when matching item is found" in {
      when(mockConfirmedContactDetailsRepository.findBy(userId)).thenReturn(Future.successful(Some(aConfirmedContactDetails)))

      val request = FakeRequest(routes.ConfirmedDetailsController.contactDetails())
      val result = route(app, request).value

      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(aConfirmedContactDetails)
    }

    "must return NOT_FOUND when matching confirmed contact details not found" in {
      when(mockConfirmedContactDetailsRepository.findBy(any())).thenReturn(Future.successful(None))

      val request = FakeRequest(routes.ConfirmedDetailsController.contactDetails())
      val result = route(app, request).value

      status(result) mustEqual NOT_FOUND
    }
  }

  ".saveContactDetails()" - {
    "must save confirmed contact details and return OK" in {
      val confirmationDetails = aConfirmedContactDetails.copy(userId = userId, created = instant)

      when(mockConfirmedContactDetailsRepository.save(any())).thenReturn(Future.successful(Done))

      val result = route(app, FakeRequest(routes.ConfirmedDetailsController.saveContactDetails())).value

      status(result) mustBe OK

      verify(mockConfirmedContactDetailsRepository, times(1)).save(confirmationDetails)
    }
  }
}
