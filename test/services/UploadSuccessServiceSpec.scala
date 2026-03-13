/*
 * Copyright 2026 HM Revenue & Customs
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

package services

import models.submission.Submission.SubmissionType
import org.apache.pekko.Done
import org.mockito.Mockito
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import repository.{SubmissionRepository, UploadSuccessWorkItemRepository}
import uk.gov.hmrc.mongo.workitem.ResultStatus
import uk.gov.hmrc.http.StringContextOps

import java.time.{Clock, Duration, Instant, Year, ZoneOffset}
import scala.concurrent.Future
import play.api.inject.bind
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{never, verify, when}
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import models.submission.*
import models.submission.Submission.State.{UploadFailed, Uploading, Validated}
import models.submission.Submission.UploadFailureReason.SchemaValidationError
import org.bson.types.ObjectId
import org.scalatest.BeforeAndAfterEach

import java.net.URL
import java.time.temporal.ChronoUnit

class UploadSuccessServiceSpec extends PlaySpec with MockitoSugar with  BeforeAndAfterEach {
  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      workItemRepository,
      submissionRepository,
      validationService,
      auditService
    )
  }

  private val fixedInstant = Instant.parse("2026-03-10T10:00:00Z")
  private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

  private val workItemRepository = mock[UploadSuccessWorkItemRepository]
  private val submissionRepository = mock[SubmissionRepository]
  private val validationService = mock[ValidationService]
  private val auditService = mock[AuditService]

  private val request = UploadSuccessRequest(
    dprsId = "dprsId",
    fileName = "file.xml",
    downloadUrl = url"https://download-url",
    checksum = "checksum",
    size = 123L
  )


  private def workItemOf(
                          item: UploadSuccessWorkItem,
                          id: ObjectId = new ObjectId()
                        ): WorkItem[UploadSuccessWorkItem] =
    WorkItem(
      id = id,
      receivedAt = fixedInstant,
      updatedAt = fixedInstant,
      availableAt = fixedInstant,
      status = ProcessingStatus.ToDo,
      failureCount = 0,
      item = item
    )

  private val queuedItem = UploadSuccessWorkItem(
    dprsId = "DPRS123",
    submissionId = "submission-1",
    fileName = "file.xml",
    downloadUrl = url"https://download-url",
    checksum = "checksum-123",
    size = 123L,
    receivedAt = fixedInstant
  )

  private val baseSubmission = Submission(
    _id = "conv-1",
    submissionType = SubmissionType.Xml,
    dprsId = "DPRS123",
    operatorId = "operator-1",
    operatorName = "Operator name",
    assumingOperatorName = None,
    state = Uploading,
    created = fixedInstant.minus(1, ChronoUnit.DAYS),
    updated = fixedInstant.minusSeconds(60)
  )


  private def application: Application =
    new GuiceApplicationBuilder()
      .configure(
        "upscan.upload-success.retry-after" -> "5 minutes"
      )
      .overrides(
        bind[Clock].toInstance(clock),
        bind[UploadSuccessWorkItemRepository].toInstance(workItemRepository),
        bind[SubmissionRepository].toInstance(submissionRepository),
        bind[ValidationService].toInstance(validationService),
        bind[AuditService].toInstance(auditService)
      )
      .build()

  "UploadSuccessService.enqueueUploadSuccess" should {
    "push a new work item with callback data and receivedAt" in {
      val app = application
      val uploadSuccessService = app.injector.instanceOf[UploadSuccessService]

      when(
        workItemRepository.pushNew(
          any[UploadSuccessWorkItem],
          any[Instant],
          any()
        )
      ).thenReturn(Future.successful(mock[WorkItem[UploadSuccessWorkItem]]))

      val result = uploadSuccessService.enqueueUploadSuccess("submissionId", request)

      result.futureValue mustBe Done

      val captor = ArgumentCaptor.forClass(classOf[UploadSuccessWorkItem])
      verify(workItemRepository).pushNew(
        captor.capture(),
        eqTo(fixedInstant),
        any()
      )

      captor.getValue mustBe UploadSuccessWorkItem(
        dprsId = "dprsId",
        submissionId = "submissionId",
        fileName = "file.xml",
        downloadUrl = url"https://download-url",
        checksum = "checksum",
        size = 123L,
        receivedAt = fixedInstant
      )
    }

    "fail when repository pushNew fails" in {
      val app = application
      val uploadSuccessService = app.injector.instanceOf[UploadSuccessService]

      when(
        workItemRepository.pushNew(
          any[UploadSuccessWorkItem],
          any[Instant],
          any()
        )
      ).thenReturn(Future.failed(new RuntimeException("insert failed")))

      val ex = uploadSuccessService.enqueueUploadSuccess("submission-1", request).failed.futureValue
      ex.getMessage mustBe "insert failed"
    }
  }

  "processNextUploadSuccess" should {

    "return false when no outstanding work item exists" in {
      val app = application
      val uploadSuccessService = app.injector.instanceOf[UploadSuccessService]

      when(workItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(Future.successful(None))

      uploadSuccessService.processNextUploadSuccess().futureValue mustBe false

      verify(workItemRepository).pullOutstanding(any[Instant], any[Instant])
    }

    "process item and complete work item when XML is valid" in {
      val workItem = workItemOf(queuedItem)
      val app = application
      val uploadSuccessService = app.injector.instanceOf[UploadSuccessService]

      when(workItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(Future.successful(Some(workItem)))
      when(submissionRepository.get("DPRS123", "submission-1"))
        .thenReturn(Future.successful(Some(baseSubmission)))
      when(validationService.validateXml("file.xml", "DPRS123", url"https://download-url", "operator-1"))
        .thenReturn(Future.successful(Right(Year.of(2025))))
      when(submissionRepository.save(any[Submission]))
        .thenReturn(Future.successful(Done))
      when(workItemRepository.complete(eqTo(workItem.id), eqTo(ProcessingStatus.Succeeded)))
        .thenReturn(Future.successful(true))

      uploadSuccessService.processNextUploadSuccess().futureValue mustBe true

      val submissionCaptor = ArgumentCaptor.forClass(classOf[Submission])
      verify(submissionRepository).save(submissionCaptor.capture())

      submissionCaptor.getValue.state mustBe Validated(
        downloadUrl = url"https://download-url",
        reportingPeriod = Year.of(2025),
        fileName = "file.xml",
        checksum = "checksum-123",
        size = 123L
      )
      submissionCaptor.getValue.updated mustBe fixedInstant

      verify(workItemRepository).complete(eqTo(workItem.id), eqTo(ProcessingStatus.Succeeded))
      verify(workItemRepository, never()).markAs(any[ObjectId], any[ProcessingStatus], any[Option[Instant]])
    }

    "process item and complete work item when XML is invalid" in {
      val app = application
      val uploadSuccessService = app.injector.instanceOf[UploadSuccessService]

      val workItem = workItemOf(queuedItem)

      when(workItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(Future.successful(Some(workItem)))
      when(submissionRepository.get("DPRS123", "submission-1"))
        .thenReturn(Future.successful(Some(baseSubmission)))
      when(validationService.validateXml("file.xml", "DPRS123", url"https://download-url", "operator-1"))
        .thenReturn(Future.successful(Left(SchemaValidationError(Seq.empty, false))))
      when(submissionRepository.save(any[Submission]))
        .thenReturn(Future.successful(Done))
      when(workItemRepository.complete(eqTo(workItem.id), eqTo(ProcessingStatus.Succeeded)))
        .thenReturn(Future.successful(true))

      uploadSuccessService.processNextUploadSuccess().futureValue mustBe true

      val submissionCaptor = ArgumentCaptor.forClass(classOf[Submission])
      verify(submissionRepository).save(submissionCaptor.capture())

      submissionCaptor.getValue.state mustBe UploadFailed(SchemaValidationError(Seq.empty, false), Some("file.xml"))
      submissionCaptor.getValue.updated mustBe fixedInstant

      verify(workItemRepository).complete(eqTo(workItem.id), eqTo(ProcessingStatus.Succeeded))
      verify(workItemRepository, never()).markAs(any[ObjectId], any[ProcessingStatus], any[Option[Instant]])

    }

    "mark item failed when validateXml throws" in {
      val app = application
      val uploadSuccessService = app.injector.instanceOf[UploadSuccessService]
      val workItem = workItemOf(queuedItem)

      when(workItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(Future.successful(Some(workItem)))
      when(submissionRepository.get("DPRS123", "submission-1"))
        .thenReturn(Future.successful(Some(baseSubmission)))
      when(validationService.validateXml("file.xml", "DPRS123", url"https://download-url", "operator-1"))
        .thenReturn(Future.failed(new RuntimeException("validation exploded")))
      when(
        workItemRepository.markAs(
          eqTo(workItem.id),
          eqTo(ProcessingStatus.Failed),
          any[Option[Instant]]
        )
      ).thenReturn(Future.successful(true))

      val result = uploadSuccessService.processNextUploadSuccess().failed.futureValue
      result.getMessage mustBe "validation exploded"

      verify(submissionRepository, never()).save(any[Submission])
      verify(workItemRepository).markAs(
        eqTo(workItem.id),
        eqTo(ProcessingStatus.Failed),
        any[Option[Instant]]
      )
    }

    "mark item failed when submission save throws" in {
      val app = application
      val uploadSuccessService = app.injector.instanceOf[UploadSuccessService]
      val workItem = workItemOf(queuedItem)

      when(workItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(Future.successful(Some(workItem)))
      when(submissionRepository.get("DPRS123", "submission-1"))
        .thenReturn(Future.successful(Some(baseSubmission)))
      when(validationService.validateXml("file.xml", "DPRS123", url"https://download-url", "operator-1"))
        .thenReturn(Future.successful(Right(Year.of(2025))))
      when(submissionRepository.save(any[Submission]))
        .thenReturn(Future.failed(new RuntimeException("save failed")))
      when(
        workItemRepository.markAs(
          eqTo(workItem.id),
          eqTo(ProcessingStatus.Failed),
          any[Option[Instant]]
        )
      ).thenReturn(Future.successful(true))

      val result = uploadSuccessService.processNextUploadSuccess().failed.futureValue
      result.getMessage mustBe "save failed"

      verify(workItemRepository).markAs(
        eqTo(workItem.id),
        eqTo(ProcessingStatus.Failed),
        any[Option[Instant]]
      )
      verify(workItemRepository, never()).complete(any[ObjectId], any[ResultStatus])
    }

    "mark item failed when submission is not found during processing" in {
      val workItem = workItemOf(queuedItem)
      val app = application
      val uploadSuccessService = app.injector.instanceOf[UploadSuccessService]

      when(workItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(Future.successful(Some(workItem)))
      when(submissionRepository.get("DPRS123", "submission-1"))
        .thenReturn(Future.successful(None))
      when(
        workItemRepository.markAs(
          eqTo(workItem.id),
          eqTo(ProcessingStatus.Failed),
          any[Option[Instant]]
        )
      ).thenReturn(Future.successful(true))

      val result = uploadSuccessService.processNextUploadSuccess().failed.futureValue
      result.getMessage must include("Submission not found")

      verify(validationService, never()).validateXml(any[String], any[String], any(), any[String])
      verify(submissionRepository, never()).save(any[Submission])
      verify(workItemRepository).markAs(
        eqTo(workItem.id),
        eqTo(ProcessingStatus.Failed),
        any[Option[Instant]]
      )
      verify(workItemRepository, never()).complete(any[ObjectId], any[ResultStatus])
    }

    "return true when item processed and completion succeeds" in {
      val workItem = workItemOf(queuedItem)
      val app = application
      val uploadSuccessService = app.injector.instanceOf[UploadSuccessService]

      when(workItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(Future.successful(Some(workItem)))
      when(submissionRepository.get("DPRS123", "submission-1"))
        .thenReturn(Future.successful(Some(baseSubmission)))
      when(validationService.validateXml("file.xml", "DPRS123", url"https://download-url", "operator-1"))
        .thenReturn(Future.successful(Left(SchemaValidationError(Seq.empty, false))))
      when(submissionRepository.save(any[Submission]))
        .thenReturn(Future.successful(Done))
      when(workItemRepository.complete(eqTo(workItem.id), eqTo(ProcessingStatus.Succeeded)))
        .thenReturn(Future.successful(true))

      uploadSuccessService.processNextUploadSuccess().futureValue mustBe true
    }

    "use retry timeout window when pulling outstanding items" in {
      val app = application
      val uploadSuccessService = app.injector.instanceOf[UploadSuccessService]
      when(workItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(Future.successful(None))

      uploadSuccessService.processNextUploadSuccess().futureValue

      verify(workItemRepository).pullOutstanding(
        eqTo(fixedInstant.minus(Duration.ofMinutes(5))),
        eqTo(fixedInstant)
      )
    }
  }
  "processAllUploadSuccesses" should {

    "stop immediately when there is nothing to process" in {
      val app = application
      val uploadSuccessService = app.injector.instanceOf[UploadSuccessService]

      when(workItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(Future.successful(None))

      uploadSuccessService.processAllUploadSuccesses().futureValue mustBe Done
    }

    "continue processing until queue is empty" in {
      val app = application
      val uploadSuccessService = app.injector.instanceOf[UploadSuccessService]

      val workItem1 = workItemOf(queuedItem, new ObjectId())
      val workItem2 = workItemOf(queuedItem.copy(submissionId = "submission-2"), new ObjectId())

      when(workItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(
          Future.successful(Some(workItem1)),
          Future.successful(Some(workItem2)),
          Future.successful(None)
        )

      when(submissionRepository.get("DPRS123", "submission-1"))
        .thenReturn(Future.successful(Some(baseSubmission)))

      when(submissionRepository.get("DPRS123", "submission-2"))
        .thenReturn(Future.successful(Some(baseSubmission.copy(_id = "conv-2"))))

      when(validationService.validateXml(any(), any(), any(), any()))
        .thenReturn(
          Future.successful(Right(Year.of(2025))),
          Future.successful(Left(SchemaValidationError(Seq.empty, false)))
        )

      when(submissionRepository.save(any[Submission]))
        .thenReturn(Future.successful(Done))

      when(workItemRepository.complete(eqTo(workItem1.id), eqTo(ProcessingStatus.Succeeded)))
        .thenReturn(Future.successful(true))

      when(workItemRepository.complete(eqTo(workItem2.id), eqTo(ProcessingStatus.Succeeded)))
        .thenReturn(Future.successful(true))

      uploadSuccessService.processAllUploadSuccesses().futureValue mustBe Done

      verify(workItemRepository).complete(eqTo(workItem1.id), eqTo(ProcessingStatus.Succeeded))
      verify(workItemRepository).complete(eqTo(workItem2.id), eqTo(ProcessingStatus.Succeeded))
    }

    "fail if one processing step fails" in {
      val app = application
      val uploadSuccessService = app.injector.instanceOf[UploadSuccessService]

      val workItem = workItemOf(queuedItem, new ObjectId())

      when(workItemRepository.pullOutstanding(any[Instant], any[Instant]))
        .thenReturn(Future.successful(Some(workItem)))
      when(submissionRepository.get("DPRS123", "submission-1"))
        .thenReturn(Future.successful(Some(baseSubmission)))
      when(validationService.validateXml(any[String], any[String], any[URL], any[String]))
        .thenReturn(Future.failed(new RuntimeException("error")))
      when(
        workItemRepository.markAs(
          eqTo(workItem.id),
          eqTo(ProcessingStatus.Failed),
          any[Option[Instant]]
        )
      ).thenReturn(Future.successful(true))

      val result = uploadSuccessService.processAllUploadSuccesses().failed.futureValue
      result.getMessage mustBe "error"
    }
  }
}