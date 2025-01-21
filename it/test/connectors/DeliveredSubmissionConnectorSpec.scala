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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock.*
import connectors.DeliveredSubmissionConnector.GetDeliveredSubmissionsFailure
import models.submission.*
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import services.UuidService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.WireMockSupport

import java.time.{Clock, LocalDateTime, Year, ZoneId, ZoneOffset}
import java.util.UUID

class DeliveredSubmissionConnectorSpec extends AnyFreeSpec
  with Matchers
  with ScalaFutures
  with IntegrationPatience
  with WireMockSupport
  with MockitoSugar
  with BeforeAndAfterEach
  with EitherValues
  with OptionValues {

  private val instant = LocalDateTime.of(2000, 1, 2, 3, 4, 5).toInstant(ZoneOffset.UTC)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)
  private val mockUuidService = mock[UuidService]

  override def beforeEach(): Unit = {
    Mockito.reset(mockUuidService)
    super.beforeEach()
  }

  private lazy val app: Application = new GuiceApplicationBuilder()
    .overrides(
      bind[Clock].toInstance(stubClock),
      bind[UuidService].toInstance(mockUuidService)
    )
    .configure("microservice.services.view-submissions.port" -> wireMockPort)
    .configure("microservice.services.view-submissions.bearer-token" -> "viewSubmissionsToken")
    .build()
  
  private val correlationId = UUID.randomUUID()
  private val conversationId = UUID.randomUUID()

  private lazy val connector = app.injector.instanceOf[DeliveredSubmissionConnector]

  implicit private lazy val hc: HeaderCarrier = HeaderCarrier()
  
  ".get" - {
    
    "must return the response when the server returns OK" in {

      when(mockUuidService.generate())
        .thenReturn(correlationId.toString, conversationId.toString)

      val responsePayload = Json.obj(
        "submissionsListResponse" -> Json.obj(
          "responseCommon" -> Json.obj(
            "regime" -> "DPRS",
            "resultsCount" -> 10
          ),
          "responseDetails" -> Json.obj(
            "submissionsList" -> Json.arr(
              Json.obj(
                "conversationId" -> conversationId.toString,
                "fileName" -> "file.xml",
                "pOId" -> "operatorId",
                "pOName" -> "operatorName",
                "reportingYear" -> "2024",
                "submissionCaseId" -> "DPI-SUB-1",
                "submissionDateTime" -> "2000-01-02T03:04:05Z",
                "submissionStatus" -> "REJECTED"
              )
            )
          )
        )
      )
      
      val expectedResponse = DeliveredSubmissions(
        submissions = Seq(
          DeliveredSubmission(
            conversationId = conversationId.toString,
            fileName = "file.xml",
            operatorId = Some("operatorId"),
            operatorName = Some("operatorName"),
            reportingPeriod = Some(Year.of(2024)),
            submissionCaseId = "DPI-SUB-1",
            submissionDateTime = instant,
            submissionStatus = SubmissionStatus.Rejected,
            assumingReporterName = None
          )
        ),
        resultsCount = 10
      )

      val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Ascending, None, None, None, Nil)

      wireMockServer.stubFor(
        post(urlMatching(".*/dac6/dprs0503/v1"))
          .withHeader("Authorization", equalTo("Bearer viewSubmissionsToken"))
          .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
          .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
          .withHeader("Accept", equalTo("application/json"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withHeader("Date", equalTo("Sun, 02 Jan 2000 03:04:05 UTC"))
          .withRequestBody(equalTo(Json.toJson(request).toString))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(responsePayload.toString)
          )
      )
      
      val result = connector.get(request).futureValue
      result.value mustEqual expectedResponse
    }
    
    "must return None when the server returns 422" in {

      when(mockUuidService.generate())
        .thenReturn(correlationId.toString, conversationId.toString)

      val responsePayload = Json.obj(
        "errorDetail" -> Json.obj(
          "errorCode" -> "001",
          "errorMessage" -> "No Matching Records found for the request"
        )
      )

      val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Ascending, None, None, None, Nil)

      wireMockServer.stubFor(
        post(urlMatching(".*/dac6/dprs0503/v1"))
          .withHeader("Authorization", equalTo("Bearer viewSubmissionsToken"))
          .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
          .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
          .withHeader("Accept", equalTo("application/json"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withHeader("Date", equalTo("Sun, 02 Jan 2000 03:04:05 UTC"))
          .withRequestBody(equalTo(Json.toJson(request).toString))
          .willReturn(
            aResponse()
              .withStatus(422)
              .withBody(responsePayload.toString)
          )
      )

      val result = connector.get(request).futureValue
      result must not be defined
    }
    
    "must return a failed future when the server returns an error" in {

      when(mockUuidService.generate())
        .thenReturn(correlationId.toString, conversationId.toString)

      val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Ascending, None, None, None, Nil)

      wireMockServer.stubFor(
        post(urlMatching(".*/dac6/dprs0503/v1"))
          .withHeader("Authorization", equalTo("Bearer viewSubmissionsToken"))
          .withHeader("X-Correlation-ID", equalTo(correlationId.toString))
          .withHeader("X-Conversation-ID", equalTo(conversationId.toString))
          .withHeader("Accept", equalTo("application/json"))
          .withHeader("Content-Type", equalTo("application/json"))
          .withHeader("Date", equalTo("Sun, 02 Jan 2000 03:04:05 UTC"))
          .withRequestBody(equalTo(Json.toJson(request).toString))
          .willReturn(serverError())
      )
      
      val result = connector.get(request).failed.futureValue
      result mustBe a[GetDeliveredSubmissionsFailure]
      
      val failure = result.asInstanceOf[GetDeliveredSubmissionsFailure]
      failure.status mustEqual 500
      failure.correlationId mustEqual correlationId.toString
    }
  }
}
