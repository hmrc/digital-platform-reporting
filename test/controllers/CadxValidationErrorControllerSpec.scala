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

import models.submission.CadxValidationError
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
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
import repository.CadxValidationErrorRepository
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}

import java.time.Instant
import scala.concurrent.Future

class CadxValidationErrorControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach {

  private val mockCadxValidationErrorRepository = mock[CadxValidationErrorRepository]
  private val mockAuthConnector = mock[AuthConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      mockCadxValidationErrorRepository,
      mockAuthConnector
    )
  }

  private val now = Instant.now()
  private val dprsId = "dprs id"
  private val submissionId = "submissionId"

  private val validEnrolments = Enrolments(Set(
    Enrolment(
      key = "HMRC-DPRS",
      identifiers = Seq(EnrolmentIdentifier("DPRSID", dprsId)),
      state = "activated",
      delegatedAuthRule = None
    )
  ))

  "getCadxValidationErrors" - {

    "must stream the errors from the repository" in {

      val app =
        GuiceApplicationBuilder()
          .overrides(
            bind[CadxValidationErrorRepository].toInstance(mockCadxValidationErrorRepository),
            bind[AuthConnector].toInstance(mockAuthConnector)
          )
          .build()

      val error1 = CadxValidationError.FileError(submissionId, dprsId, "001", None, now)
      val error2 = CadxValidationError.RowError(submissionId, dprsId, "001", None, "docRef\n", now)

      println(Json.toJson(error2))

      when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
      when(mockCadxValidationErrorRepository.getErrorsForSubmission(any(), any())).thenReturn(Source(Seq(error1, error2)))

      running(app) {
        given Materializer = app.materializer

        val request = FakeRequest(routes.CadxValidationErrorController.getCadxValidationErrors(submissionId))
        val result = route(app, request).value

        status(result) mustEqual OK
        contentType(result).value mustEqual "application/x-ndjson"

        val results = contentAsString(result).split("\n").map(Json.parse(_).as[CadxValidationError])
        results must contain only (error1, error2)
      }
    }
  }
}
