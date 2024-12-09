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

import models.sdes.CadxResultWorkItem
import models.submission
import models.submission.IdAndLastUpdated
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{Format, Json}
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import repository.{CadxResultWorkItemRepository, SubmissionRepository}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.internalauth.client.test.{BackendAuthComponentsStub, StubBehaviour}
import uk.gov.hmrc.internalauth.client.{BackendAuthComponents, Retrieval}
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class AdminControllerSpec
  extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach {

  private val mockSubmissionRepo = mock[SubmissionRepository]
  private val mockCadxResultWorkItemRepo = mock[CadxResultWorkItemRepository]

  private val mockStubBehaviour = mock[StubBehaviour]
  private val backendAuthComponentsStub: BackendAuthComponents =
    BackendAuthComponentsStub(mockStubBehaviour)(Helpers.stubControllerComponents(), ExecutionContext.global)

  private val now: Instant = Instant.now()

  override def beforeEach() = {
    reset(mockSubmissionRepo)
    reset(mockStubBehaviour)
    reset(mockCadxResultWorkItemRepo)
  }

  "getBlockedSubmissions" - {
    "rejects an invalid token from the requester" in {
      when(mockStubBehaviour.stubAuth(any, eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.failed(UpstreamErrorResponse("Unauthorized", UNAUTHORIZED)))

      val app: Application = GuiceApplicationBuilder().overrides(
        bind[SubmissionRepository].toInstance(mockSubmissionRepo),
        bind[BackendAuthComponents].toInstance(backendAuthComponentsStub)
      ).build()

      running(app) {
        val request = FakeRequest(routes.AdminController.getBlockedSubmissions())
                        .withHeaders("Authorization" -> "Token some-token")
        val result = route(app, request).value

        status(result) mustBe UNAUTHORIZED
      }
    }

    "checks the user is authorized" in {
      when(mockStubBehaviour.stubAuth(any, eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.failed(UpstreamErrorResponse("Forbidden", FORBIDDEN)))

      val app: Application = GuiceApplicationBuilder().overrides(
        bind[SubmissionRepository].toInstance(mockSubmissionRepo),
        bind[BackendAuthComponents].toInstance(backendAuthComponentsStub)
      ).build()

      running(app) {
        val request = FakeRequest(routes.AdminController.getBlockedSubmissions())
          .withHeaders("Authorization" -> "Token some-token")
        val result = route(app, request).value

        status(result) mustBe FORBIDDEN
      }
    }

    "retrieves submissions that have been in submitted state for more than a threshold value" in {
      val app: Application = GuiceApplicationBuilder().overrides(
          bind[SubmissionRepository].toInstance(mockSubmissionRepo),
          bind[BackendAuthComponents].toInstance(backendAuthComponentsStub)
          ).build()

      when(mockStubBehaviour.stubAuth(any, eqTo(Retrieval.EmptyRetrieval))).thenReturn(Future.unit)

      when(mockSubmissionRepo.getBlockedSubmissionIds()).thenReturn(Future.successful(List(
        submission.IdAndLastUpdated("ID1",Instant.parse("2024-10-10T10:10:10.100Z")),
        submission.IdAndLastUpdated("ID2",Instant.parse("2024-10-10T10:10:10.100Z"))
      )))

      running(app) {
        val request = FakeRequest(routes.AdminController.getBlockedSubmissions())
                        .withHeaders("Authorization" -> "Token some-token")
        val result = route(app, request).value

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.parse(
          """[
            | {"id":"ID1", "lastUpdated":"2024-10-10T10:10:10.100Z"},
            | {"id":"ID2", "lastUpdated":"2024-10-10T10:10:10.100Z"}
            |]
            |""".stripMargin)
      }
    }
  }

  "getCadxResultWorkItems" - {

    "rejects an invalid token from the requester" in {
      when(mockStubBehaviour.stubAuth(any, eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.failed(UpstreamErrorResponse("Unauthorized", UNAUTHORIZED)))

      val app: Application = GuiceApplicationBuilder().overrides(
        bind[CadxResultWorkItemRepository].toInstance(mockCadxResultWorkItemRepo),
        bind[BackendAuthComponents].toInstance(backendAuthComponentsStub)
      ).build()

      running(app) {
        val request = FakeRequest(routes.AdminController.getCadxResultWorkItems())
          .withHeaders("Authorization" -> "Token some-token")
        val result = route(app, request).value

        status(result) mustBe UNAUTHORIZED
      }
    }

    "checks the user is authorized" in {
      when(mockStubBehaviour.stubAuth(any, eqTo(Retrieval.EmptyRetrieval)))
        .thenReturn(Future.failed(UpstreamErrorResponse("Forbidden", FORBIDDEN)))

      val app: Application = GuiceApplicationBuilder().overrides(
        bind[CadxResultWorkItemRepository].toInstance(mockCadxResultWorkItemRepo),
        bind[BackendAuthComponents].toInstance(backendAuthComponentsStub)
      ).build()

      running(app) {
        val request = FakeRequest(routes.AdminController.getCadxResultWorkItems())
          .withHeaders("Authorization" -> "Token some-token")
        val result = route(app, request).value

        status(result) mustBe FORBIDDEN
      }
    }

    "retrieves the list of CADX result work items" in {
      val app: Application = GuiceApplicationBuilder().overrides(
        bind[CadxResultWorkItemRepository].toInstance(mockCadxResultWorkItemRepo),
        bind[BackendAuthComponents].toInstance(backendAuthComponentsStub)
      ).build()

      when(mockStubBehaviour.stubAuth(any, eqTo(Retrieval.EmptyRetrieval))).thenReturn(Future.unit)

      val workItems = Seq(
        WorkItem(
          id = new ObjectId(),
          receivedAt = now,
          updatedAt = now,
          availableAt = now,
          status = ProcessingStatus.ToDo,
          failureCount = 0,
          item = CadxResultWorkItem("test.xml")
        )
      )

      when(mockCadxResultWorkItemRepo.listWorkItems(any(), any(), any())).thenReturn(Future.successful(workItems))

      given Format[WorkItem[CadxResultWorkItem]] = WorkItem.workItemRestFormat[CadxResultWorkItem]

      running(app) {
        val request = FakeRequest(routes.AdminController.getCadxResultWorkItems(Set(ProcessingStatus.ToDo), 1, 2))
          .withHeaders("Authorization" -> "Token some-token")
        val result = route(app, request).value

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.obj(
          "workItems" -> workItems
        )

        verify(mockCadxResultWorkItemRepo).listWorkItems(Set(ProcessingStatus.ToDo), 1, 2)
      }
    }
  }
}
