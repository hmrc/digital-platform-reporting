package controllers

import models.submission.Submission.State.{Ready, Uploading, Validated}
import models.submission.{StartSubmissionRequest, Submission}
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import play.api.inject.bind
import play.api.libs.json.Json
import repository.SubmissionRepository
import services.UuidService

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneOffset}
import java.util.UUID
import scala.concurrent.Future

class SubmissionControllerSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with OptionValues
    with ScalaFutures
    with BeforeAndAfterEach {

  private val now = Instant.now()

  private val mockSubmissionRepository = mock[SubmissionRepository]
  private val mockUuidService = mock[UuidService]
  private val clock = Clock.fixed(now, ZoneOffset.UTC)

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(
      bind[SubmissionRepository].toInstance(mockSubmissionRepository),
      bind[Clock].toInstance(clock),
      bind[UuidService].toInstance(mockUuidService)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockSubmissionRepository)
  }

  "start" - {

    val dprsId = "dprsId"
    val platformOperatorId = "poid"
    val uuid = UUID.randomUUID().toString

    "when there is no id given" - {

      "must create and save a new submission for the given DPRS id and return CREATED with the new submission body included" in {

        val request = FakeRequest(routes.SubmissionController.start(dprsId, None))
          .withBody(Json.toJson(StartSubmissionRequest(
            platformOperatorId = platformOperatorId
          )))

        val expectedSubmission = Submission(
          _id = uuid,
          dprsId = dprsId,
          platformOperatorId = platformOperatorId,
          state = Ready,
          created = now,
          updated = now
        )

        when(mockUuidService.generate()).thenReturn(uuid)
        when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

        val result = route(app, request).value

        status(result) mustEqual CREATED
        contentAsJson(result) mustEqual Json.toJson(expectedSubmission)

        verify(mockSubmissionRepository, times(0)).get(any(), any())
        verify(mockSubmissionRepository).save(expectedSubmission)
      }
    }

    "when there is an id given" - {

      "when there is a Validated submission for the given id" - {

        "must update the existing submission and return OK" in {

          val initialPoId = "initialPoId"

          val request = FakeRequest(routes.SubmissionController.start(dprsId, Some(uuid)))
            .withBody(Json.toJson(StartSubmissionRequest(
              platformOperatorId = platformOperatorId
            )))

          val existingSubmission = Submission(
            _id = uuid,
            dprsId = dprsId,
            platformOperatorId = initialPoId,
            state = Validated,
            created = now.minus(1, ChronoUnit.DAYS),
            updated = now.minus(1, ChronoUnit.DAYS)
          )

          val expectedSubmission = existingSubmission
            .copy(
              platformOperatorId = platformOperatorId,
              state = Ready,
              updated = now
            )

          when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(Some(existingSubmission)))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

          val result = route(app, request).value

          status(result) mustEqual OK
          contentAsJson(result) mustEqual Json.toJson(expectedSubmission)

          verify(mockSubmissionRepository).get(dprsId, uuid)
          verify(mockSubmissionRepository).save(expectedSubmission)
        }
      }

      "when there is a submission for the given id but it is not Validated" - {

        "must not update the existing submission and return CONFLICT" in {

          val initialPoId = "initialPoId"

          val request = FakeRequest(routes.SubmissionController.start(dprsId, Some(uuid)))
            .withBody(Json.toJson(StartSubmissionRequest(
              platformOperatorId = platformOperatorId
            )))

          val existingSubmission = Submission(
            _id = uuid,
            dprsId = dprsId,
            platformOperatorId = initialPoId,
            state = Uploading,
            created = now.minus(1, ChronoUnit.DAYS),
            updated = now.minus(1, ChronoUnit.DAYS)
          )

          when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(Some(existingSubmission)))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

          val result = route(app, request).value

          status(result) mustEqual CONFLICT

          verify(mockSubmissionRepository).get(dprsId, uuid)
          verify(mockSubmissionRepository, times(0)).save(any)
        }
      }

      "when there is no submission for the given id" - {

        "must return NOT FOUND" in {

          val request = FakeRequest(routes.SubmissionController.start(dprsId, Some(uuid)))
            .withBody(Json.toJson(StartSubmissionRequest(
              platformOperatorId = platformOperatorId
            )))

          when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(None))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

          val result = route(app, request).value

          status(result) mustEqual NOT_FOUND

          verify(mockSubmissionRepository).get(dprsId, uuid)
          verify(mockSubmissionRepository, times(0)).save(any)
        }
      }
    }
  }

  "get" - {

    val dprsId = "dprsId"
    val platformOperatorId = "poid"
    val uuid = UUID.randomUUID().toString

    "when there is a matching submission" - {

      "must return OK with the submission body included" in {

        val request = FakeRequest(routes.SubmissionController.get(dprsId, uuid))

        val expectedSubmission = Submission(
          _id = uuid,
          dprsId = dprsId,
          platformOperatorId = platformOperatorId,
          state = Ready,
          created = now,
          updated = now
        )

        when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(Some(expectedSubmission)))

        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(expectedSubmission)

        verify(mockSubmissionRepository).get(dprsId, uuid)
      }
    }

    "when there is no matching submission" - {

      "must return NOT_FOUND" in {

        val request = FakeRequest(routes.SubmissionController.get(dprsId, uuid))

        when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(None))

        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND

        verify(mockSubmissionRepository).get(dprsId, uuid)
      }
    }
  }
}
