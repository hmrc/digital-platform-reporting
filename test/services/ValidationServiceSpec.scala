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
import models.assumed.AssumingPlatformOperator
import models.submission.AssumedReportingSubmission
import models.submission.Submission.UploadFailureReason.*
import org.apache.pekko.stream.scaladsl.StreamConverters
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{never, verify, when}
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
  private val mockAssumedReportingService = mock[AssumedReportingService]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "validation.schema-path" -> "schemas/DPIXML_v1.0.xsd",
        "validation.error-limit" -> 2
      )
      .overrides(
        bind[DownloadConnector].toInstance(mockDownloadConnector),
        bind[AssumedReportingService].toInstance(mockAssumedReportingService)
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockDownloadConnector, mockAssumedReportingService)
  }

  private val validationService = app.injector.instanceOf[ValidationService]

  "validateXml" - {

    val validFileName = "test.xml"
    val invalidFileName = "test.xls"
    val downloadUrl = url"http://example.com/test.xml"
    val poid = "1"
    val dprsId = "dprsId"

    "when the given file is valid" - {

      "must return an error when the uploaded file extension is not .xml" in {

        val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/SubmissionSampleAssumed.xml"))

        when(mockDownloadConnector.download(any()))
          .thenReturn(Future.successful(source))
        when(mockAssumedReportingService.getSubmission(any(), any(), any())(using any()))
          .thenReturn(Future.successful(None))

        val result = validationService.validateXml(invalidFileName, dprsId, downloadUrl, poid).futureValue
        result.left.value mustEqual InvalidFileNameExtension

        verify(mockDownloadConnector, never()).download(downloadUrl)
      }

      "must return the reporting period when no manual assumed reports have been submitted for this POID and year" in {

        val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/SubmissionSampleAssumed.xml"))

        when(mockDownloadConnector.download(any()))
          .thenReturn(Future.successful(source))
        when(mockAssumedReportingService.getSubmission(any(), any(), any())(using any()))
          .thenReturn(Future.successful(None))

        val result = validationService.validateXml(validFileName, dprsId, downloadUrl, poid).futureValue
        result.value mustBe Year.of(1957)

        verify(mockDownloadConnector).download(downloadUrl)
      }

      "must return the reporting period when the manual assumed report that has been submitted for this POID and year is deleted" in {

        val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/SubmissionSampleAssumed.xml"))

        val manualAssumedReport = AssumedReportingSubmission(
          operatorId = "operatorId",
          operatorName = "operatorName",
          assumingOperator = AssumingPlatformOperator(
            name = "assumingOperatorName",
            residentCountry = "GB",
            tinDetails = Nil,
            registeredCountry = "GB",
            address = "address"
          ),
          reportingPeriod = Year.of(2024),
          isDeleted = true
        )
        
        when(mockDownloadConnector.download(any()))
          .thenReturn(Future.successful(source))
        when(mockAssumedReportingService.getSubmission(any(), any(), any())(using any()))
          .thenReturn(Future.successful(Some(manualAssumedReport)))

        val result = validationService.validateXml(validFileName, dprsId, downloadUrl, poid).futureValue
        result.value mustBe Year.of(1957)

        verify(mockDownloadConnector).download(downloadUrl)
      }

      "must return an error when the manual assumed report that has been submitted for this POID and year is not deleted" in {

        val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/SubmissionSampleAssumed.xml"))

        val manualAssumedReport = AssumedReportingSubmission(
          operatorId = "operatorId",
          operatorName = "operatorName",
          assumingOperator = AssumingPlatformOperator(
            name = "assumingOperatorName",
            residentCountry = "GB",
            tinDetails = Nil,
            registeredCountry = "GB",
            address = "address"
          ),
          reportingPeriod = Year.of(2024),
          isDeleted = false
        )

        when(mockDownloadConnector.download(any()))
          .thenReturn(Future.successful(source))
        when(mockAssumedReportingService.getSubmission(any(), any(), any())(using any()))
          .thenReturn(Future.successful(Some(manualAssumedReport)))

        val result = validationService.validateXml(validFileName, dprsId, downloadUrl, poid).futureValue
        result.left.value mustEqual ManualAssumedReportExists

        verify(mockDownloadConnector).download(downloadUrl)
      }
    }

    "when the file fails schema validation" - {

      "must return an error when there are more validation errors than the limit" in {

        val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/InvalidSubmissionSample2.xml"))

        when(mockDownloadConnector.download(any()))
          .thenReturn(Future.successful(source))
        when(mockAssumedReportingService.getSubmission(any(), any(), any())(using any()))
          .thenReturn(Future.successful(None))

        val result = validationService.validateXml(validFileName, dprsId, downloadUrl, poid).futureValue.left.value
        result mustBe a[SchemaValidationError]
        result.asInstanceOf[SchemaValidationError].errors mustEqual Seq(
          SchemaValidationError.Error(12, 46, "cvc-enumeration-valid: Value 'broken' is not facet-valid with respect to enumeration '[DPI, DAC7]'. It must be a value from the enumeration."),
          SchemaValidationError.Error(12, 46, "cvc-type.3.1.3: The value 'broken' of element 'dpi:MessageType' is not valid.")
        )
        result.asInstanceOf[SchemaValidationError].moreErrors mustBe true

        verify(mockDownloadConnector).download(downloadUrl)
      }

      "must return an error when there are fewer validation errors than the limit" in {

        val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/InvalidSubmissionSample.xml"))

        when(mockDownloadConnector.download(any()))
          .thenReturn(Future.successful(source))
        when(mockAssumedReportingService.getSubmission(any(), any(), any())(using any()))
          .thenReturn(Future.successful(None))

        val result = validationService.validateXml(validFileName, dprsId, downloadUrl, poid).futureValue.left.value
        result mustBe a[SchemaValidationError]
        result.asInstanceOf[SchemaValidationError].errors mustEqual Seq(
          SchemaValidationError.Error(12, 18, "cvc-complex-type.2.4.a: Invalid content was found starting with element '{\"urn:oecd:ties:dpi:v1\":Warning}'. One of '{\"urn:oecd:ties:dpi:v1\":MessageType}' is expected.")
        )
        result.asInstanceOf[SchemaValidationError].moreErrors mustBe false

        verify(mockDownloadConnector).download(downloadUrl)
      }
    }

    "must return an error when the given file is not XML" in {

      val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/NotXml.xml"))

      when(mockDownloadConnector.download(any()))
        .thenReturn(Future.successful(source))
      when(mockAssumedReportingService.getSubmission(any(), any(), any())(using any()))
        .thenReturn(Future.successful(None))

      val result = validationService.validateXml(validFileName, dprsId, downloadUrl, poid).futureValue
      result.left.value mustEqual NotXml

      verify(mockDownloadConnector).download(downloadUrl)
    }

    "must return an error when the given file does not match the POID of the submission" in {

      val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/SubmissionSampleAssumed.xml"))

      when(mockDownloadConnector.download(any()))
        .thenReturn(Future.successful(source))
      when(mockAssumedReportingService.getSubmission(any(), any(), any())(using any()))
        .thenReturn(Future.successful(None))

      val result = validationService.validateXml(validFileName, dprsId, downloadUrl, "a-different-poid").futureValue
      result.left.value mustEqual PlatformOperatorIdMismatch("a-different-poid", poid)

      verify(mockDownloadConnector).download(downloadUrl)
    }

    "must return an error when the given file does not contain a POID" in {

      val source = StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/MissingPoIdSample.xml"))

      when(mockDownloadConnector.download(any()))
        .thenReturn(Future.successful(source))
      when(mockAssumedReportingService.getSubmission(any(), any(), any())(using any()))
        .thenReturn(Future.successful(None))

      val result = validationService.validateXml(validFileName, dprsId, downloadUrl, poid).futureValue
      result.left.value mustEqual PlatformOperatorIdMissing

      verify(mockDownloadConnector).download(downloadUrl)
    }
  }
}
