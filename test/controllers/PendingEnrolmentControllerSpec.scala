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

import controllers.actions.{AuthPendingEnrolmentAction, FakeAuthPendingEnrolmentAction}
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
import repository.PendingEnrolmentRepository
import support.SpecBase
import support.builders.PendingEnrolmentBuilder.aPendingEnrolment
import support.builders.PendingEnrolmentRequestBuilder.aPendingEnrolmentRequest

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.Future

class PendingEnrolmentControllerSpec extends SpecBase
  with GuiceOneAppPerSuite
  with MockitoSugar
  with OptionValues
  with ScalaFutures
  with BeforeAndAfterEach {

  private val instant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

  private val mockPendingEnrolmentRepository = mock[PendingEnrolmentRepository]

  private val action = new FakeAuthPendingEnrolmentAction(aPendingEnrolment.userId,
    aPendingEnrolment.providerId, aPendingEnrolment.groupIdentifier)

  override def fakeApplication(): Application = GuiceApplicationBuilder().overrides(
    bind[PendingEnrolmentRepository].toInstance(mockPendingEnrolmentRepository),
    bind[AuthPendingEnrolmentAction].toInstance(action),
    bind[Clock].toInstance(stubClock)
  ).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockPendingEnrolmentRepository)
  }

  ".get(...)" - {
    "must return pending enrolment when matching one is found" in {
      when(mockPendingEnrolmentRepository.find(aPendingEnrolment.userId)).thenReturn(Future.successful(Some(aPendingEnrolment)))

      val request = FakeRequest(routes.PendingEnrolmentController.get())
      val result = route(app, request).value

      status(result) mustEqual OK
      contentAsJson(result) mustEqual Json.toJson(aPendingEnrolment)
    }

    "must return NOT_FOUND when no matching pending enrolment found" in {
      when(mockPendingEnrolmentRepository.find(any())).thenReturn(Future.successful(None))

      val request = FakeRequest(routes.PendingEnrolmentController.get())
      val result = route(app, request).value

      status(result) mustEqual NOT_FOUND
    }
  }

  ".save(...)" - {
    "must save pending enrolment and return OK" in {
      val pendingEnrolment = aPendingEnrolment.copy(
        verifierKey = aPendingEnrolmentRequest.verifierKey,
        verifierValue = aPendingEnrolmentRequest.verifierValue,
        dprsId = aPendingEnrolmentRequest.dprsId,
        created = stubClock.instant()
      )

      when(mockPendingEnrolmentRepository.insert(any())).thenReturn(Future.successful(Done))

      val request = FakeRequest(routes.PendingEnrolmentController.save()).withJsonBody(Json.toJson(aPendingEnrolmentRequest))
      val result = route(app, request).value

      status(result) mustBe OK

      verify(mockPendingEnrolmentRepository, times(1)).insert(pendingEnrolment)
    }
  }
}
