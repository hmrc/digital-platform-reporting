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

import models.submission.Submission.State
import models.submission.Submission.State.{Approved, Ready, Rejected, Submitted, UploadFailed, Uploading, Validated}
import models.submission.{Submission, UploadFailedRequest, UploadSuccessRequest}
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repository.SubmissionRepository
import services.UuidService
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolment, EnrolmentIdentifier, Enrolments}

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
  private val mockAuthConnector = mock[AuthConnector]
  private val clock = Clock.fixed(now, ZoneOffset.UTC)

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(
      bind[SubmissionRepository].toInstance(mockSubmissionRepository),
      bind[Clock].toInstance(clock),
      bind[UuidService].toInstance(mockUuidService),
      bind[AuthConnector].toInstance(mockAuthConnector)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockSubmissionRepository, mockAuthConnector)
  }

  private val readyGen: Gen[Ready.type] = Gen.const(Ready)
  private val uploadingGen: Gen[Uploading.type] = Gen.const(Uploading)
  private val uploadFailedGen: Gen[UploadFailed] = Gen.asciiPrintableStr.map(UploadFailed.apply)
  private val validatedGen: Gen[Validated.type] = Gen.const(Validated)
  private val submittedGen: Gen[Submitted.type] = Gen.const(Submitted)
  private val approvedGen: Gen[Approved.type] = Gen.const(Approved)
  private val rejectedGen: Gen[Rejected] = Gen.asciiPrintableStr.map(Rejected.apply)

  private val dprsId = "dprsId"
  private val uuid = UUID.randomUUID().toString

  private val validEnrolments = Enrolments(Set(
    Enrolment(
      key = "HMRC-DPRS",
      identifiers = Seq(EnrolmentIdentifier("DPRSID", dprsId)),
      state = "activated",
      delegatedAuthRule = None
    )
  ))

  "start" - {

    "when there is no id given" - {

      "must create and save a new submission for the given DPRS id and return CREATED with the new submission body included" in {

        val request = FakeRequest(routes.SubmissionController.start(None))

        val expectedSubmission = Submission(
          _id = uuid,
          dprsId = dprsId,
          state = Ready,
          created = now,
          updated = now
        )

        when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
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

          val request = FakeRequest(routes.SubmissionController.start(Some(uuid)))

          val existingSubmission = Submission(
            _id = uuid,
            dprsId = dprsId,
            state = Validated,
            created = now.minus(1, ChronoUnit.DAYS),
            updated = now.minus(1, ChronoUnit.DAYS)
          )

          val expectedSubmission = existingSubmission
            .copy(
              state = Ready,
              updated = now
            )

          when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
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

          val request = FakeRequest(routes.SubmissionController.start(Some(uuid)))

          val state = Gen.oneOf(readyGen, uploadingGen, uploadFailedGen, submittedGen, approvedGen, rejectedGen).sample.value
          val existingSubmission = Submission(
            _id = uuid,
            dprsId = dprsId,
            state = state,
            created = now.minus(1, ChronoUnit.DAYS),
            updated = now.minus(1, ChronoUnit.DAYS)
          )

          when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
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

          val request = FakeRequest(routes.SubmissionController.start(Some(uuid)))

          when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
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

    "when there is a matching submission" - {

      "must return OK with the submission body included" in {

        val request = FakeRequest(routes.SubmissionController.get(uuid))

        val existingSubmission = Submission(
          _id = uuid,
          dprsId = dprsId,
          state = Ready,
          created = now,
          updated = now
        )

        when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
        when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(Some(existingSubmission)))

        val result = route(app, request).value

        status(result) mustEqual OK
        contentAsJson(result) mustEqual Json.toJson(existingSubmission)

        verify(mockSubmissionRepository).get(dprsId, uuid)
      }
    }

    "when there is no matching submission" - {

      "must return NOT_FOUND" in {

        val request = FakeRequest(routes.SubmissionController.get(uuid))

        when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
        when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(None))

        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND

        verify(mockSubmissionRepository).get(dprsId, uuid)
      }
    }
  }

  "startUpload" - {

    "when there is a matching submission" - {

      "when the matching submission is in a Ready or UploadFailed state" - {

        "must set the state of the submission to Uploading and return OK" in {

          val request = FakeRequest(routes.SubmissionController.startUpload(uuid))

          val state = Gen.oneOf(readyGen, uploadFailedGen).sample.value
          val existingSubmission = Submission(
            _id = uuid,
            dprsId = dprsId,
            state = state,
            created = now.minus(1, ChronoUnit.DAYS),
            updated = now.minus(1, ChronoUnit.DAYS)
          )

          val expectedSubmission = existingSubmission.copy(
            state = Uploading,
            updated = now
          )

          when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
          when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(Some(existingSubmission)))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

          val result = route(app, request).value

          status(result) mustEqual OK
          contentAsJson(result) mustEqual Json.toJson(expectedSubmission)

          verify(mockSubmissionRepository).get(dprsId, uuid)
          verify(mockSubmissionRepository).save(expectedSubmission)
        }
      }

      "when the matching submission is in any other state" - {

        "must return CONFLICT" in {

          val request = FakeRequest(routes.SubmissionController.startUpload(uuid))

          val state = Gen.oneOf(uploadingGen, validatedGen, submittedGen, approvedGen, rejectedGen).sample.value
          val existingSubmission = Submission(
            _id = uuid,
            dprsId = dprsId,
            state = state,
            created = now,
            updated = now
          )

          when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
          when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(Some(existingSubmission)))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

          val result = route(app, request).value

          status(result) mustEqual CONFLICT

          verify(mockSubmissionRepository).get(dprsId, uuid)
          verify(mockSubmissionRepository, times(0)).save(any())
        }
      }
    }

    "when there is no matching submission" - {

      "must return NOT_FOUND" in {

        val request = FakeRequest(routes.SubmissionController.startUpload(uuid))

        when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
        when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(None))
        when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND

        verify(mockSubmissionRepository).get(dprsId, uuid)
        verify(mockSubmissionRepository, times(0)).save(any())
      }
    }
  }

  "uploadSuccess" - {

    val downloadUrl = "downloadUrl"

    "when there is a matching submission" - {

      "when the matching submission is in an Uploading state" - {

        "when the submission fails validation" - {
          // TODO add validation
        }

        "when the submission passes validation" - {

          "must set the state of the submission to Validated and return OK" in {

            val request = FakeRequest(routes.SubmissionController.uploadSuccess(uuid))
              .withBody(Json.toJson(UploadSuccessRequest(dprsId, downloadUrl)))

            val existingSubmission = Submission(
              _id = uuid,
              dprsId = dprsId,
              state = Uploading,
              created = now.minus(1, ChronoUnit.DAYS),
              updated = now.minus(1, ChronoUnit.DAYS)
            )

            val expectedSubmission = existingSubmission.copy(
              state = Validated,
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
      }

      "when the matching submission is in any other state" - {

        "must return CONFLICT" in {

          val request = FakeRequest(routes.SubmissionController.uploadSuccess(uuid))
            .withBody(Json.toJson(UploadSuccessRequest(dprsId, downloadUrl)))

          val state = Gen.oneOf(readyGen, uploadFailedGen, validatedGen, submittedGen, approvedGen, rejectedGen).sample.value
          val existingSubmission = Submission(
            _id = uuid,
            dprsId = dprsId,
            state = state,
            created = now,
            updated = now
          )

          when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(Some(existingSubmission)))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

          val result = route(app, request).value

          status(result) mustEqual CONFLICT

          verify(mockSubmissionRepository).get(dprsId, uuid)
          verify(mockSubmissionRepository, times(0)).save(any())
        }
      }
    }

    "when there is no matching submission" - {

      "must return NOT_FOUND" in {

        val request = FakeRequest(routes.SubmissionController.uploadSuccess(uuid))
          .withBody(Json.toJson(UploadSuccessRequest(dprsId, downloadUrl)))

        when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(None))
        when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND

        verify(mockSubmissionRepository).get(dprsId, uuid)
        verify(mockSubmissionRepository, times(0)).save(any())
      }
    }
  }

  "uploadFailed" - {

    "when there is a matching submission" - {

      "when the matching submission is in a Ready, Uploading, or UploadFailed state" - {

        "must set the state of the submission to UploadFailed and return OK" in {

          val request = FakeRequest(routes.SubmissionController.uploadFailed(uuid))
            .withBody(Json.toJson(UploadFailedRequest(
              dprsId = dprsId,
              reason = "some reason"
            )))

          val state = Gen.oneOf(readyGen, uploadFailedGen, uploadingGen).sample.value
          val existingSubmission = Submission(
            _id = uuid,
            dprsId = dprsId,
            state = state,
            created = now.minus(1, ChronoUnit.DAYS),
            updated = now.minus(1, ChronoUnit.DAYS)
          )

          val expectedSubmission = existingSubmission.copy(
            state = UploadFailed("some reason"),
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

      "when the matching submission is in any other state" - {

        "must return CONFLICT" in {

          val request = FakeRequest(routes.SubmissionController.uploadFailed(uuid))
            .withBody(Json.toJson(UploadFailedRequest(
              dprsId = dprsId,
              reason = "some reason"
            )))

          val state = Gen.oneOf(validatedGen, submittedGen, approvedGen, rejectedGen).sample.value
          val existingSubmission = Submission(
            _id = uuid,
            dprsId = dprsId,
            state = state,
            created = now,
            updated = now
          )

          when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(Some(existingSubmission)))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

          val result = route(app, request).value

          status(result) mustEqual CONFLICT

          verify(mockSubmissionRepository).get(dprsId, uuid)
          verify(mockSubmissionRepository, times(0)).save(any())
        }
      }
    }

    "when there is no matching submission" - {

      "must return NOT_FOUND" in {

        val request = FakeRequest(routes.SubmissionController.uploadFailed(uuid))
          .withBody(Json.toJson(UploadFailedRequest(
            dprsId = dprsId,
            reason = "some reason"
          )))

        when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(None))
        when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND

        verify(mockSubmissionRepository).get(dprsId, uuid)
        verify(mockSubmissionRepository, times(0)).save(any())
      }
    }
  }

  "submit" - {

    "when there is a matching submission" - {

      "when the matching submission is in a Validated state" - {

        "must set the state of the submission to UploadFailed and return OK" in {

          val request = FakeRequest(routes.SubmissionController.submit(uuid))

          val existingSubmission = Submission(
            _id = uuid,
            dprsId = dprsId,
            state = Validated,
            created = now.minus(1, ChronoUnit.DAYS),
            updated = now.minus(1, ChronoUnit.DAYS)
          )

          val expectedSubmission = existingSubmission.copy(
            state = Submitted,
            updated = now
          )

          when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
          when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(Some(existingSubmission)))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

          val result = route(app, request).value

          status(result) mustEqual OK
          contentAsJson(result) mustEqual Json.toJson(expectedSubmission)

          verify(mockSubmissionRepository).get(dprsId, uuid)
          verify(mockSubmissionRepository).save(expectedSubmission)
        }
      }

      "when the matching submission is in any other state" - {

        "must return CONFLICT" in {

          val request = FakeRequest(routes.SubmissionController.submit(uuid))

          val state = Gen.oneOf(readyGen, uploadingGen, uploadFailedGen, submittedGen, approvedGen, rejectedGen).sample.value
          val existingSubmission = Submission(
            _id = uuid,
            dprsId = dprsId,
            state = state,
            created = now,
            updated = now
          )

          when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
          when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(Some(existingSubmission)))
          when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

          val result = route(app, request).value

          status(result) mustEqual CONFLICT

          verify(mockSubmissionRepository).get(dprsId, uuid)
          verify(mockSubmissionRepository, times(0)).save(any())
        }
      }
    }

    "when there is no matching submission" - {

      "must return NOT_FOUND" in {

        val request = FakeRequest(routes.SubmissionController.submit(uuid))

        when(mockAuthConnector.authorise(any(), any())(any(), any())).thenReturn(Future.successful(validEnrolments))
        when(mockSubmissionRepository.get(any(), any())).thenReturn(Future.successful(None))
        when(mockSubmissionRepository.save(any())).thenReturn(Future.successful(Done))

        val result = route(app, request).value

        status(result) mustEqual NOT_FOUND

        verify(mockSubmissionRepository).get(dprsId, uuid)
        verify(mockSubmissionRepository, times(0)).save(any())
      }
    }
  }
}
