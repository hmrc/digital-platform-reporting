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
import org.apache.pekko.stream.scaladsl.{Source, StreamConverters}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import services.ValidationService.ValidationError
import uk.gov.hmrc.http.StringContextOps

import scala.concurrent.Future

class ValidationServiceSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with ScalaFutures
    with MockitoSugar
    with OptionValues {

  private val mockDownloadConnector = mock[DownloadConnector]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "validation.schema-path" -> "test-schema.xsd"
      )
      .overrides(
        bind[DownloadConnector].toInstance(mockDownloadConnector)
      )
      .build()

  private val validationService = app.injector.instanceOf[ValidationService]

  "validateXml" - {

    "must return None when the given file is valid" in {

      val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/valid-test.xml"))

      when(mockDownloadConnector.download(any()))
        .thenReturn(Future.successful(source))

      val result = validationService.validateXml(url"http://example.com/valid-test.xml").futureValue
      result mustBe None
    }

    "must return an error when the given file fails schema validation" in {

      val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/invalid-test.xml"))

      when(mockDownloadConnector.download(any()))
        .thenReturn(Future.successful(source))

      val result = validationService.validateXml(url"http://example.com/invalid-test.xml").futureValue.value
      result mustEqual ValidationError("error.schema")
    }

    "must return an error when the given file does not match the POID of the submission" ignore {

    }
  }
}
