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

package services

import connectors.{SdesConnector, SdesDownloadConnector}
import controllers.routes
import models.sdes.*
import models.sdes.list.SdesFile
import models.submission.Submission.State.Validated
import models.subscription.responses.SubscriptionInfo
import models.subscription.{Individual, IndividualContact, Organisation, OrganisationContact}
import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito.{never, times, verify, when}
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
import play.api.test.Helpers.{route, status}
import repository.{CadxResultWorkItemRepository, SdesSubmissionWorkItemRepository}
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.mongo.lock.{Lock, MongoLockRepository}
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import utils.DateTimeFormats.ISO8601Formatter

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, Year, ZoneOffset}
import scala.concurrent.Future
import scala.concurrent.duration.given

class CadxResultWorkItemServiceSpec
  extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach
    with MockitoSugar
    with OptionValues {

  private val now = Instant.now()
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val mockCadxResultWorkItemRepository: CadxResultWorkItemRepository = mock[CadxResultWorkItemRepository]
  private val mockCadxResultService: CadxResultService = mock[CadxResultService]
  private val mockSdesConnector: SdesConnector = mock[SdesConnector]
  private val mockDownloadConnector: SdesDownloadConnector = mock[SdesDownloadConnector]
  private val mockLockRepository: MongoLockRepository = mock[MongoLockRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      mockCadxResultWorkItemRepository,
      mockCadxResultService,
      mockSdesConnector,
      mockDownloadConnector,
      mockLockRepository
    )
  }

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "sdes.cadx-result.retry-after" -> "30m"
      )
      .overrides(
        bind[CadxResultWorkItemRepository].toInstance(mockCadxResultWorkItemRepository),
        bind[CadxResultService].toInstance(mockCadxResultService),
        bind[SdesConnector].toInstance(mockSdesConnector),
        bind[SdesDownloadConnector].toInstance(mockDownloadConnector),
        bind[MongoLockRepository].toInstance(mockLockRepository),
        bind[Clock].toInstance(clock)
      )
      .build()

  private lazy val cadxResultWorkItemService: CadxResultWorkItemService = app.injector.instanceOf[CadxResultWorkItemService]

  "enqueueResult" - {

    val fileName = "test.xml"

    "must save a work item with the relevant data" in {

      val expectedWorkItem = CadxResultWorkItem(fileName = fileName)

      when(mockCadxResultWorkItemRepository.pushNew(any(), any(), any())).thenReturn(Future.successful(null))

      cadxResultWorkItemService.enqueueResult(fileName).futureValue

      verify(mockCadxResultWorkItemRepository).pushNew(expectedWorkItem, now)
    }

    "must fail when saving the work item fails" in {

      when(mockCadxResultWorkItemRepository.pushNew(any(), any(), any())).thenReturn(Future.failed(new RuntimeException()))

      cadxResultWorkItemService.enqueueResult(fileName).failed.futureValue
    }
  }

  "processNextResult" - {

    "when there is a file in the work item queue" - {

      val fileName = "test.xml"

      val workItem = WorkItem(
        id = ObjectId(),
        receivedAt = now.minus(1, ChronoUnit.HOURS),
        updatedAt = now.minus(1, ChronoUnit.HOURS),
        availableAt = now.minus(1, ChronoUnit.HOURS),
        status = ToDo,
        failureCount = 0,
        item = CadxResultWorkItem(fileName = fileName)
      )

      "must retrieve the file from SDES, process it, and return true" in {

        val files = Seq(
          SdesFile(
            fileName = "test.xml",
            fileSize = 1337,
            downloadUrl = url"http://example.com/test.xml",
            metadata = Seq.empty
          ),
          SdesFile(
            fileName = "test2.xml",
            fileSize = 1337,
            downloadUrl = url"http://example.com/test2.xml",
            metadata = Seq.empty
          )
        )

        val requestedFileContents = "foobar"

        when(mockSdesConnector.listFiles(any())(using any())).thenReturn(Future.successful(files))
        when(mockDownloadConnector.download(any())).thenReturn(Future.successful(Source.single(ByteString.fromString(requestedFileContents))))
        when(mockCadxResultService.processResult(any())).thenReturn(Future.successful(Done))

        when(mockCadxResultWorkItemRepository.pullOutstanding(any(), any())).thenReturn(Future.successful(Some(workItem)))
        when(mockCadxResultWorkItemRepository.complete(any(), any())).thenReturn(Future.successful(true))

        cadxResultWorkItemService.processNextResult().futureValue mustBe true

        val captor: ArgumentCaptor[Source[ByteString, ?]] = ArgumentCaptor.forClass(classOf[Source[ByteString, NotUsed]])

        verify(mockCadxResultWorkItemRepository).pullOutstanding(now.minus(30, ChronoUnit.MINUTES), now)
        verify(mockSdesConnector).listFiles(eqTo("cadx-result"))(using any())
        verify(mockDownloadConnector).download(url"http://example.com/test.xml")
        verify(mockCadxResultService).processResult(captor.capture())
        verify(mockCadxResultWorkItemRepository).complete(workItem.id, ProcessingStatus.Succeeded)

        given Materializer = app.materializer

        val receivedFile = captor.getValue.runWith(Sink.fold(ByteString.empty)(_ ++ _)).futureValue.utf8String
        receivedFile mustEqual requestedFileContents
      }

      "must mark the work item as failed and fail when the file is not in the list of files from SDES" in {

        val files = Seq(
          SdesFile(
            fileName = "test2.xml",
            fileSize = 1337,
            downloadUrl = url"http://example.com/test2.xml",
            metadata = Seq.empty
          )
        )

        when(mockSdesConnector.listFiles(any())(using any())).thenReturn(Future.successful(files))
        when(mockCadxResultWorkItemRepository.pullOutstanding(any(), any())).thenReturn(Future.successful(Some(workItem)))
        when(mockCadxResultWorkItemRepository.markAs(any(), any(), any())).thenReturn(Future.successful(true))

        cadxResultWorkItemService.processNextResult().failed.futureValue

        verify(mockSdesConnector).listFiles(eqTo("cadx-result"))(using any())
        verify(mockCadxResultWorkItemRepository).markAs(workItem.id, ProcessingStatus.Failed)
        verify(mockDownloadConnector, never()).download(any())
        verify(mockCadxResultService, never()).processResult(any())
      }

      "must mark the work item as failed and fail when the file cannot be retrieved from SDES" in {

        val files = Seq(
          SdesFile(
            fileName = "test.xml",
            fileSize = 1337,
            downloadUrl = url"http://example.com/test.xml",
            metadata = Seq.empty
          ),
          SdesFile(
            fileName = "test2.xml",
            fileSize = 1337,
            downloadUrl = url"http://example.com/test2.xml",
            metadata = Seq.empty
          )
        )

        when(mockSdesConnector.listFiles(any())(using any())).thenReturn(Future.successful(files))
        when(mockDownloadConnector.download(any())).thenReturn(Future.failed(new RuntimeException()))

        when(mockCadxResultWorkItemRepository.pullOutstanding(any(), any())).thenReturn(Future.successful(Some(workItem)))
        when(mockCadxResultWorkItemRepository.markAs(any(), any(), any())).thenReturn(Future.successful(true))

        cadxResultWorkItemService.processNextResult().failed.futureValue

        verify(mockSdesConnector).listFiles(eqTo("cadx-result"))(using any())
        verify(mockCadxResultWorkItemRepository).markAs(workItem.id, ProcessingStatus.Failed)
        verify(mockDownloadConnector).download(url"http://example.com/test.xml")
        verify(mockCadxResultService, never()).processResult(any())
      }

      "must mark the work item as failed and fail when the CadxResultService fails" in {

        val files = Seq(
          SdesFile(
            fileName = "test.xml",
            fileSize = 1337,
            downloadUrl = url"http://example.com/test.xml",
            metadata = Seq.empty
          ),
          SdesFile(
            fileName = "test2.xml",
            fileSize = 1337,
            downloadUrl = url"http://example.com/test2.xml",
            metadata = Seq.empty
          )
        )

        val requestedFileContents = "foobar"

        when(mockSdesConnector.listFiles(any())(using any())).thenReturn(Future.successful(files))
        when(mockDownloadConnector.download(any())).thenReturn(Future.successful(Source.single(ByteString.fromString(requestedFileContents))))
        when(mockCadxResultService.processResult(any())).thenReturn(Future.failed(new RuntimeException()))

        when(mockCadxResultWorkItemRepository.pullOutstanding(any(), any())).thenReturn(Future.successful(Some(workItem)))
        when(mockCadxResultWorkItemRepository.markAs(any(), any(), any())).thenReturn(Future.successful(true))

        cadxResultWorkItemService.processNextResult().failed.futureValue

        verify(mockCadxResultWorkItemRepository).pullOutstanding(now.minus(30, ChronoUnit.MINUTES), now)
        verify(mockSdesConnector).listFiles(eqTo("cadx-result"))(using any())
        verify(mockDownloadConnector).download(url"http://example.com/test.xml")
        verify(mockCadxResultService).processResult(any())
        verify(mockCadxResultWorkItemRepository).markAs(workItem.id, ProcessingStatus.Failed)
      }

      "must mark the work item as permanently failed and return true when the CadxResultService fails with a SubmissionNotFoundException" in {

        val files = Seq(
          SdesFile(
            fileName = "test.xml",
            fileSize = 1337,
            downloadUrl = url"http://example.com/test.xml",
            metadata = Seq.empty
          ),
          SdesFile(
            fileName = "test2.xml",
            fileSize = 1337,
            downloadUrl = url"http://example.com/test2.xml",
            metadata = Seq.empty
          )
        )

        val requestedFileContents = "foobar"

        when(mockSdesConnector.listFiles(any())(using any())).thenReturn(Future.successful(files))
        when(mockDownloadConnector.download(any())).thenReturn(Future.successful(Source.single(ByteString.fromString(requestedFileContents))))
        when(mockCadxResultService.processResult(any())).thenReturn(Future.failed(CadxResultService.SubmissionNotFoundException("submissionId")))

        when(mockCadxResultWorkItemRepository.pullOutstanding(any(), any())).thenReturn(Future.successful(Some(workItem)))
        when(mockCadxResultWorkItemRepository.markAs(any(), any(), any())).thenReturn(Future.successful(true))

        cadxResultWorkItemService.processNextResult().futureValue mustBe true

        verify(mockCadxResultWorkItemRepository).pullOutstanding(now.minus(30, ChronoUnit.MINUTES), now)
        verify(mockSdesConnector).listFiles(eqTo("cadx-result"))(using any())
        verify(mockDownloadConnector).download(eqTo(url"http://example.com/test.xml"))
        verify(mockCadxResultService).processResult(any())
        verify(mockCadxResultWorkItemRepository).markAs(eqTo(workItem.id), eqTo(ProcessingStatus.PermanentlyFailed), any())
      }
    }

    "when there is no submission in the work item queue" - {

      "must return false" in {

        when(mockCadxResultWorkItemRepository.pullOutstanding(any(), any())).thenReturn(Future.successful(None))

        cadxResultWorkItemService.processNextResult().futureValue mustBe false

        verify(mockCadxResultWorkItemRepository).pullOutstanding(now.minus(30, ChronoUnit.MINUTES), now)
        verify(mockSdesConnector, never()).listFiles(any())(using any())
        verify(mockDownloadConnector, never()).download(any())
        verify(mockCadxResultService, never()).processResult(any())
        verify(mockCadxResultWorkItemRepository, never()).markAs(any(), any(), any())
        verify(mockCadxResultWorkItemRepository, never()).complete(any(), any())
      }
    }
  }

  "processAllResults" - {

    val fileName = "test.xml"

    val workItem = WorkItem(
      id = ObjectId(),
      receivedAt = now.minus(1, ChronoUnit.HOURS),
      updatedAt = now.minus(1, ChronoUnit.HOURS),
      availableAt = now.minus(1, ChronoUnit.HOURS),
      status = ToDo,
      failureCount = 0,
      item = CadxResultWorkItem(fileName = fileName)
    )

    val files = Seq(
      SdesFile(
        fileName = "test.xml",
        fileSize = 1337,
        downloadUrl = url"http://example.com/test.xml",
        metadata = Seq.empty
      ),
      SdesFile(
        fileName = "test2.xml",
        fileSize = 1337,
        downloadUrl = url"http://example.com/test2.xml",
        metadata = Seq.empty
      )
    )

    "when the lock is free" - {

      val lock = Lock("id", "owner", now, now.plus(1, ChronoUnit.HOURS))

      "must process all submissions" in {

        when(mockLockRepository.takeLock(any(), any(), any())).thenReturn(Future.successful(Some(lock)))
        when(mockLockRepository.releaseLock(any(), any())).thenReturn(Future.unit)

        when(mockSdesConnector.listFiles(any())(using any())).thenReturn(Future.successful(files))
        when(mockDownloadConnector.download(any())).thenReturn(Future.successful(Source.single(ByteString.fromString("foobar"))))
        when(mockCadxResultService.processResult(any())).thenReturn(Future.successful(Done))

        when(mockCadxResultWorkItemRepository.pullOutstanding(any(), any())).thenReturn(
          Future.successful(Some(workItem)),
          Future.successful(Some(workItem)),
          Future.successful(Some(workItem)),
          Future.successful(None)
        )
        when(mockCadxResultWorkItemRepository.complete(any(), any())).thenReturn(Future.successful(true))

        cadxResultWorkItemService.processAllResults().futureValue

        verify(mockCadxResultWorkItemRepository, times(4)).pullOutstanding(now.minus(30, ChronoUnit.MINUTES), now)
        verify(mockCadxResultService, times(3)).processResult(any())
        verify(mockCadxResultWorkItemRepository, times(3)).complete(workItem.id, ProcessingStatus.Succeeded)
      }

      "must fail when one of the submissions fails" in {

        when(mockLockRepository.takeLock(any(), any(), any())).thenReturn(Future.successful(Some(lock)))
        when(mockLockRepository.releaseLock(any(), any())).thenReturn(Future.unit)

        when(mockSdesConnector.listFiles(any())(using any())).thenReturn(Future.successful(files))
        when(mockDownloadConnector.download(any())).thenReturn(Future.successful(Source.single(ByteString.fromString("foobar"))))
        when(mockCadxResultWorkItemRepository.pullOutstanding(any(), any())).thenReturn(Future.successful(Some(workItem)))
        when(mockCadxResultService.processResult(any())).thenReturn(
          Future.successful(Done),
          Future.failed(new RuntimeException())
        )

        when(mockCadxResultWorkItemRepository.markAs(any(), any(), any())).thenReturn(Future.successful(true))
        when(mockCadxResultWorkItemRepository.complete(any(), any())).thenReturn(Future.successful(true))

        cadxResultWorkItemService.processAllResults().failed.futureValue

        verify(mockCadxResultWorkItemRepository, times(2)).pullOutstanding(now.minus(30, ChronoUnit.MINUTES), now)
        verify(mockCadxResultService, times(2)).processResult(any())
        verify(mockCadxResultWorkItemRepository, times(1)).complete(workItem.id, ProcessingStatus.Succeeded)
        verify(mockCadxResultWorkItemRepository, times(1)).markAs(workItem.id, ProcessingStatus.Failed)
      }
    }

    "when the lock is not free" - {

      "must return without processing anything" in {

        when(mockLockRepository.takeLock(any(), any(), any())).thenReturn(Future.successful(None))
        when(mockLockRepository.releaseLock(any(), any())).thenReturn(Future.unit)

        when(mockSdesConnector.listFiles(any())(using any())).thenReturn(Future.successful(files))
        when(mockDownloadConnector.download(any())).thenReturn(Future.successful(Source.single(ByteString.fromString("foobar"))))
        when(mockCadxResultService.processResult(any())).thenReturn(Future.successful(Done))

        when(mockCadxResultWorkItemRepository.pullOutstanding(any(), any())).thenReturn(
          Future.successful(Some(workItem)),
          Future.successful(Some(workItem)),
          Future.successful(Some(workItem)),
          Future.successful(None)
        )
        when(mockCadxResultWorkItemRepository.complete(any(), any())).thenReturn(Future.successful(true))

        cadxResultWorkItemService.processAllResults().futureValue

        verify(mockCadxResultWorkItemRepository, never()).pullOutstanding(any(), any())
      }
    }
  }
}
