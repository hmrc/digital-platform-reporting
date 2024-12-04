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
import models.recentsubmissions.requests.RecentSubmissionRequest
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
import repository.RecentSubmissionsRepository
import support.SpecBase
import support.builders.RecentSubmissionDetailsBuilder.aRecentSubmissionDetails

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.Future

class RecentSubmissionsControllerSpec extends SpecBase
  with GuiceOneAppPerSuite
  with MockitoSugar
  with OptionValues
  with ScalaFutures
  with BeforeAndAfterEach {

  private val instant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)
  private val userId = "some-user-id"

  private val mockRecentSubmissionsRepository = mock[RecentSubmissionsRepository]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder().overrides(
      bind[AuthAction].toInstance(new FakeAuthAction(userId)),
      bind[RecentSubmissionsRepository].toInstance(mockRecentSubmissionsRepository),
      bind[Clock].toInstance(stubClock)
    ).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockRecentSubmissionsRepository)
  }

  ".get(...)" - {
    "must return recent submission when matching one is found" in {
      when(mockRecentSubmissionsRepository.findBy(userId, "some-operator-id")).thenReturn(Future.successful(Some(aRecentSubmissionDetails)))

      val request = FakeRequest(routes.RecentSubmissionsController.get("some-operator-id"))
      val result = route(app, request).value

      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(aRecentSubmissionDetails)
    }

    "must return NOT_FOUND when matching recent submission not found" in {
      when(mockRecentSubmissionsRepository.findBy(any(), any())).thenReturn(Future.successful(None))

      val request = FakeRequest(routes.RecentSubmissionsController.get("any-operator-id"))
      val result = route(app, request).value

      status(result) mustEqual NOT_FOUND
    }
  }

  ".save()" - {
    "must save pending enrolment and return OK" in {
      val recentSubmissionDetails = aRecentSubmissionDetails.copy(
        userId = userId,
        operatorId = "some-operator-id",
        createdAt = instant
      )

      when(mockRecentSubmissionsRepository.save(any())).thenReturn(Future.successful(Done))

      val recentSubmissionRequest = RecentSubmissionRequest(operatorId = "some-operator-id")
      val request = FakeRequest(routes.RecentSubmissionsController.save()).withJsonBody(Json.toJson(recentSubmissionRequest))
      val result = route(app, request).value

      status(result) mustBe OK

      verify(mockRecentSubmissionsRepository, times(1)).save(recentSubmissionDetails)
    }
  }
}
