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

import connectors.DeliveredSubmissionConnector
import controllers.actions.{AuthAction, FakeAuthAction}
import models.submission.*
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
import play.api.libs.json.{JsString, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import scala.concurrent.Future
import java.time.Instant

class DeliveredSubmissionControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach {

  private val mockConnector = mock[DeliveredSubmissionConnector]

  override def beforeEach(): Unit = {
    Mockito.reset(mockConnector)
    super.beforeEach()
  }
  
  ".get" - {

    "must return platform operator details when the server returns them" in {

      val app =
        new GuiceApplicationBuilder()
          .overrides(
            bind[DeliveredSubmissionConnector].toInstance(mockConnector),
            bind[AuthAction].toInstance(new FakeAuthAction)
          )
          .build()

      val response = DeliveredSubmissions(
        submissions = Seq(DeliveredSubmission(
          conversationId = "conversationId",
          fileName = "file.xml",
          operatorId = "operatorId",
          operatorName = "operatorName",
          reportingPeriod = "2024",
          submissionDateTime = Instant.now,
          submissionStatus = DeliveredSubmissionStatus.Success,
          assumingReporterName = None
        )),
        resultsCount = 1
      )

      when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(response)))

      val requestJson = Json.obj(
        "subscriptionId" -> "dprsId",
        "assumedReporting" -> true,
        "pageNumber" -> 2,
        "sortBy" -> "PONAME",
        "sortOrder" -> "ASC"
      )

      running(app) {

        val request =
          FakeRequest(routes.DeliveredSubmissionController.get())
            .withJsonBody(requestJson)

        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(response)
      }
    }

    "must return NOT_FOUND when no submissions exist for this subscription" in {

      val app =
        new GuiceApplicationBuilder()
          .overrides(
            bind[DeliveredSubmissionConnector].toInstance(mockConnector),
            bind[AuthAction].toInstance(new FakeAuthAction)
          )
          .build()

      when(mockConnector.get(any())(any())).thenReturn(Future.successful(None))

      val requestJson = Json.obj(
        "subscriptionId" -> "dprsId",
        "assumedReporting" -> true,
        "pageNumber" -> 2,
        "sortBy" -> "PONAME",
        "sortOrder" -> "ASC"
      )

      running(app) {

        val request =
          FakeRequest(routes.DeliveredSubmissionController.get())
            .withJsonBody(requestJson)

        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND
      }
    }
  }
}
