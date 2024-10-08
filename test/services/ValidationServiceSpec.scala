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

import connectors.DownloadConnector
import org.apache.pekko.stream.scaladsl.StreamConverters
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, EitherValues, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import services.ValidationService.ValidationError
import uk.gov.hmrc.http.StringContextOps

import java.time.Year
import scala.concurrent.Future

class ValidationServiceSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with ScalaFutures
    with MockitoSugar
    with OptionValues
    with BeforeAndAfterEach
    with IntegrationPatience
    with EitherValues {

  private val mockDownloadConnector = mock[DownloadConnector]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "validation.schema-path" -> "schemas/DPIXML_v1.08.xsd"
      )
      .overrides(
        bind[DownloadConnector].toInstance(mockDownloadConnector)
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockDownloadConnector)
  }

  private val validationService = app.injector.instanceOf[ValidationService]

  "validateXml" - {

    val downloadUrl = url"http://example.com/test.xml"
    val poid = "1"

    "must return the reporting period when the given file is valid" in {

      val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/SubmissionSample.xml"))

      when(mockDownloadConnector.download(any()))
        .thenReturn(Future.successful(source))

      val result = validationService.validateXml(downloadUrl, poid).futureValue
      result.value mustBe Year.of(1957)

      verify(mockDownloadConnector).download(downloadUrl)
    }

    "must return an error when the given file fails schema validation" in {

      val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/InvalidSubmissionSample.xml"))

      when(mockDownloadConnector.download(any()))
        .thenReturn(Future.successful(source))

      val result = validationService.validateXml(downloadUrl, poid).futureValue
      result.left.value mustEqual ValidationError("schema")

      verify(mockDownloadConnector).download(downloadUrl)
    }

    "must return an error when the given file is not XML" in {

      val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/NotXml.xml"))

      when(mockDownloadConnector.download(any()))
        .thenReturn(Future.successful(source))

      val result = validationService.validateXml(downloadUrl, poid).futureValue
      result.left.value mustEqual ValidationError("not-xml")

      verify(mockDownloadConnector).download(downloadUrl)
    }

    "must return an error when the given file does not match the POID of the submission" in {

      val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/SubmissionSample.xml"))

      when(mockDownloadConnector.download(any()))
        .thenReturn(Future.successful(source))

      val result = validationService.validateXml(downloadUrl, "a-different-poid").futureValue
      result.left.value mustEqual ValidationError("poid.incorrect")

      verify(mockDownloadConnector).download(downloadUrl)
    }

    "must return an error when the given file does not contain a POID" in {

      val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/MissingPoIdSample.xml"))

      when(mockDownloadConnector.download(any()))
        .thenReturn(Future.successful(source))

      val result = validationService.validateXml(downloadUrl, poid).futureValue
      result.left.value mustEqual ValidationError("poid.missing")

      verify(mockDownloadConnector).download(downloadUrl)
    }
  }
}
