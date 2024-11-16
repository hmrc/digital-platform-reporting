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

package worker

import connectors.{SdesConnector, SdesDownloadConnector}
import models.sdes.CadxResultWorkItem
import models.sdes.list.SdesFile
import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repository.CadxResultWorkItemRepository
import services.CadxResultService
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import scala.concurrent.Future

class CadxResultWorkerSpec
  extends AnyFreeSpec
    with Matchers
    with BeforeAndAfterEach
    with GuiceOneServerPerSuite
    with CleanMongoCollectionSupport
    with IntegrationPatience
    with OptionValues
    with MockitoSugar
    with Eventually {

  private val mockCadxResultService: CadxResultService = mock[CadxResultService]
  private val mockSdesConnector: SdesConnector = mock[SdesConnector]
  private val mockDownloadConnector: SdesDownloadConnector = mock[SdesDownloadConnector]

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(
      bind[MongoComponent].toInstance(mongoComponent),
      bind[CadxResultService].toInstance(mockCadxResultService),
      bind[SdesConnector].toInstance(mockSdesConnector),
      bind[SdesDownloadConnector].toInstance(mockDownloadConnector)
    )
    .configure(
      "workers.cadx-result.initial-delay" -> "1s",
      "workers.cadx-result.interval" -> "1s"
    )
    .build()

  private val cadxResultWorkItemRepository: CadxResultWorkItemRepository =
    app.injector.instanceOf[CadxResultWorkItemRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      mockCadxResultService,
      mockSdesConnector,
      mockDownloadConnector
    )
    cadxResultWorkItemRepository.initialised.futureValue
  }

  "must process waiting submissions" in {

    val workItem = CadxResultWorkItem("test.xml")

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

    cadxResultWorkItemRepository.pushNew(workItem).futureValue

    eventually {
      verify(mockCadxResultService).processResult(any())
    }
  }
}
