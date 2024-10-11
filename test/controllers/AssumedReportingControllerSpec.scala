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

import models.assumed.{AssumingOperatorAddress, AssumingPlatformOperator}
import models.submission.AssumedReportingSubmissionRequest
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
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
import services.SubmissionService
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}

import java.time.Year
import scala.concurrent.Future

class AssumedReportingControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach {

  private val mockSubmissionService = mock[SubmissionService]
  private val mockAuthConnector = mock[AuthConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      mockSubmissionService,
      mockAuthConnector
    )
  }

  private val dprsId = "dprs id"

  private val validEnrolments = Enrolments(Set(
    Enrolment(
      key = "HMRC-DPRS",
      identifiers = Seq(EnrolmentIdentifier("DPRSID", dprsId)),
      state = "activated",
      delegatedAuthRule = None
    )
  ))

  "submit" - {

    "must stream the errors from the repository" in {

      val requestBody = AssumedReportingSubmissionRequest(
        operatorId = "operatorId",
        assumingOperator = AssumingPlatformOperator(
          name = "assumingOperator",
          residentCountry = "GB",
          tinDetails = Seq.empty,
          address = AssumingOperatorAddress(
            line1 = "line1",
            line2 = None,
            city = "city",
            region = None,
            postCode = "postcode",
            country = "GB"
          )
        ),
        reportingPeriod = Year.of(2024)
      )

      val app =
        GuiceApplicationBuilder()
          .overrides(
            bind[SubmissionService].toInstance(mockSubmissionService),
            bind[AuthConnector].toInstance(mockAuthConnector)
          )
          .build()

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
      when(mockSubmissionService.submitAssumedReporting(any(), any(), any(), any())(using any())).thenReturn(Future.successful(Done))

      running(app) {
        val request = FakeRequest(routes.AssumedReportingController.submit())
          .withJsonBody(Json.toJson(requestBody))
        val result = route(app, request).value

        status(result) mustEqual NO_CONTENT
      }

      verify(mockSubmissionService).submitAssumedReporting(any(), any(), any(), any())(using any())
    }
  }
}