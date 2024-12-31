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

import models.assumed.AssumingPlatformOperator
import models.submission.*
import models.submission.Submission.State.Submitted
import models.submission.Submission.SubmissionType
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
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
import services.{AssumedReportingService, SubmissionService, ViewSubmissionsService}
import support.auth.Retrievals.Ops
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}

import java.time.{Instant, Year}
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
  private val mockAssumedReportingService = mock[AssumedReportingService]
  private val mockViewSubmissionsService = mock[ViewSubmissionsService]

  private val now = Instant.now()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      mockSubmissionService,
      mockAuthConnector,
      mockAssumedReportingService,
      mockViewSubmissionsService
    )
  }

  private val dprsId = "dprs id"

  private val validEnrolments = Some("userId") ~ Enrolments(Set(
    Enrolment(
      key = "HMRC-DPRS",
      identifiers = Seq(EnrolmentIdentifier("DPRSID", dprsId)),
      state = "activated",
      delegatedAuthRule = None
    )
  ))

  "submit" - {

    "must submit an assumed reporting submission and return the submission details" in {

      val requestBody = AssumedReportingSubmissionRequest(
        operatorId = "operatorId",
        assumingOperator = AssumingPlatformOperator(
          name = "assumingOperator",
          residentCountry = "GB",
          tinDetails = Seq.empty,
          registeredCountry = "GB",
          address = "line 1\nline2\nline3"
        ),
        reportingPeriod = Year.of(2024)
      )

      val submission = Submission(
        _id = "id",
        dprsId = dprsId,
        submissionType = SubmissionType.ManualAssumedReport,
        operatorId = "operatorId",
        operatorName = "operatorName",
        assumingOperatorName = Some("assuminOperatorName"),
        state = Submitted(
          fileName = "test.xml",
          reportingPeriod = Year.of(2024),
          size = 346457L
        ),
        created = now,
        updated = now
      )

      val app =
        GuiceApplicationBuilder()
          .overrides(
            bind[SubmissionService].toInstance(mockSubmissionService),
            bind[AuthConnector].toInstance(mockAuthConnector)
          )
          .build()

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
      when(mockSubmissionService.submitAssumedReporting(any(), any(), any(), any())(using any())).thenReturn(Future.successful(submission))

      running(app) {
        val request = FakeRequest(routes.AssumedReportingController.submit())
          .withJsonBody(Json.toJson(requestBody))
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(submission)
      }

      verify(mockSubmissionService).submitAssumedReporting(eqTo(dprsId), eqTo(submission.operatorId), eqTo(requestBody.assumingOperator), eqTo(Year.of(2024)))(using any())
    }
  }

  "delete" - {

    "must delete an assumed reporting submission and return the submission details" in {

      val submission = Submission(
        _id = "id",
        submissionType = SubmissionType.ManualAssumedReport,
        dprsId = dprsId,
        operatorId = "operatorId",
        operatorName = "operatorName",
        assumingOperatorName = None,
        state = Submitted(
          fileName = "test.xml",
          reportingPeriod = Year.of(2024),
          size = 7346L
        ),
        created = now,
        updated = now
      )

      val app =
        GuiceApplicationBuilder()
          .overrides(
            bind[SubmissionService].toInstance(mockSubmissionService),
            bind[AuthConnector].toInstance(mockAuthConnector)
          )
          .build()

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
      when(mockSubmissionService.submitAssumedReportingDeletion(any(), any(), any())(using any())).thenReturn(Future.successful(submission))

      running(app) {
        val request = FakeRequest(routes.AssumedReportingController.delete(submission.operatorId, Year.of(2024)))
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(submission)
      }

      verify(mockSubmissionService).submitAssumedReportingDeletion(eqTo(dprsId), eqTo(submission.operatorId), eqTo(Year.of(2024)))(using any())
    }
  }

  "get" - {

    "must return OK and a submission when one can be found" in {

      val assumedReportingSubmission = AssumedReportingSubmission(
        operatorId = "operatorId",
        operatorName = "operatorName",
        assumingOperator = AssumingPlatformOperator(
          name = "name",
          residentCountry = "GB",
          tinDetails = Nil,
          registeredCountry = "GB",
          address = "address"
        ),
        reportingPeriod = Year.of(2024),
        isDeleted = false
      )

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
      when(mockAssumedReportingService.getSubmission(any(), any(), any())(using any())).thenReturn(Future.successful(Some(assumedReportingSubmission)))

      val app =
        GuiceApplicationBuilder()
          .overrides(
            bind[SubmissionService].toInstance(mockSubmissionService),
            bind[AuthConnector].toInstance(mockAuthConnector),
            bind[AssumedReportingService].toInstance(mockAssumedReportingService)
          )
          .build()

      running(app) {
        val request = FakeRequest(routes.AssumedReportingController.get("operatorId", Year.of(2024)))
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(assumedReportingSubmission)

        verify(mockAssumedReportingService).getSubmission(eqTo(dprsId), eqTo("operatorId"), eqTo(Year.of(2024)))(using any())
      }
    }

    "must return Not Found when a submission cannot be found" in {

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
      when(mockAssumedReportingService.getSubmission(any(), any(), any())(using any())).thenReturn(Future.successful(None))

      val app =
        GuiceApplicationBuilder()
          .overrides(
            bind[SubmissionService].toInstance(mockSubmissionService),
            bind[AuthConnector].toInstance(mockAuthConnector),
            bind[AssumedReportingService].toInstance(mockAssumedReportingService)
          )
          .build()

      running(app) {
        val request = FakeRequest(routes.AssumedReportingController.get("operatorId", Year.of(2024)))
        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND

        verify(mockAssumedReportingService).getSubmission(eqTo(dprsId), eqTo("operatorId"), eqTo(Year.of(2024)))(using any())
      }
    }
  }

  "list" - {

    "must return OK and a list of submissions when some can be found" in {

      val submission1 = SubmissionSummary(
        submissionId = "id1",
        fileName = "filename1",
        operatorId = "operatorId",
        operatorName = "operatorName",
        reportingPeriod = Year.of(2024),
        submissionDateTime = now,
        submissionStatus = SubmissionStatus.Success,
        assumingReporterName = Some("assumingOperator"),
        submissionCaseId = Some("caseId1"),
        isDeleted = false,
        localDataExists = false
      )
      val submission2 = SubmissionSummary(
        submissionId = "id2",
        fileName = "filename2",
        operatorId = "operatorId2",
        operatorName = "operatorName2",
        reportingPeriod = Year.of(2024),
        submissionDateTime = now,
        submissionStatus = SubmissionStatus.Success,
        assumingReporterName = Some("assumingOperator2"),
        submissionCaseId = Some("caseId2"),
        isDeleted = false,
        localDataExists = false
      )

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
      when(mockViewSubmissionsService.getAssumedReports(any())(using any())).thenReturn(Future.successful(Seq(submission1, submission2)))

      val app =
        GuiceApplicationBuilder()
          .overrides(
            bind[AuthConnector].toInstance(mockAuthConnector),
            bind[ViewSubmissionsService].toInstance(mockViewSubmissionsService)
          )
          .build()

      running(app) {
        val request = FakeRequest(routes.AssumedReportingController.list())
        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(Seq(submission1, submission2))
      }
    }

    "must return NOT_FOUND when no submissions can be found" in {

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
      when(mockViewSubmissionsService.getAssumedReports(any())(using any())).thenReturn(Future.successful(Nil))

      val app =
        GuiceApplicationBuilder()
          .overrides(
            bind[AuthConnector].toInstance(mockAuthConnector),
            bind[ViewSubmissionsService].toInstance(mockViewSubmissionsService)
          )
          .build()

      running(app) {
        val request = FakeRequest(routes.AssumedReportingController.list())
        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND
      }
    }
  }
}
