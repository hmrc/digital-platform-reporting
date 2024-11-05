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
import uk.gov.hmrc.http.StringContextOps

import scala.concurrent.Future

class HristoValidationServiceSpec extends AnyFreeSpec
  with Matchers
  with GuiceOneAppPerSuite
  with ScalaFutures
  with MockitoSugar
  with OptionValues
  with BeforeAndAfterEach
  with IntegrationPatience
  with EitherValues {

  private val downloadUrl = url"http://example.com/test.xml"

  private val mockDownloadConnector = mock[DownloadConnector]

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(
    "validation.schema-path" -> "schemas/DPIXML_v1.08.xsd"
  ).overrides(
    bind[DownloadConnector].toInstance(mockDownloadConnector)
  ).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockDownloadConnector)
  }

  private val validationService = app.injector.instanceOf[ValidationService]

  "validateXml" - {
    "hristo" in {
      //  val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Assumed Reporting-new.xml"
      //  val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Assumed Reporting-correction.xml"
      val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Assumed Reporting-deletion.xml"
      //  val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Personal Services-new.xml"
      //  val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Personal Services-correction.xml"
      //  val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Personal Services-deletion.xml"
      //  val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Property Rental-new.xml"
      //  val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Property Rental-correction.xml"
      //  val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Property Rental-deletion.xml"
      //  val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Sale of Goods-new.xml"
      //  val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Sale of Goods-correction.xml"
      //  val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Sale of Goods-deletion.xml"
      //  val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Transportation Rental-new.xml"
      //  val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Transportation Rental-correction.xml"
      //  val fileName = "Digital Platform Reporting Sample GB Submission XML v1.1 - Transportation Rental-deletion.xml"
      val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream(s"/hristo/$fileName"))

      when(mockDownloadConnector.download(any())).thenReturn(Future.successful(source))

      val result = validationService.validateXml(downloadUrl, "XEDPI2078675698").futureValue
      println(s"result.value = ${result.value}")

      verify(mockDownloadConnector).download(downloadUrl)
    }
  }
}
