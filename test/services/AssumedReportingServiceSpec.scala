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

import connectors.{DeliveredSubmissionConnector, SubmissionConnector}
import generated.{DPI_OECD, Generated_DPI_OECDFormat}
import models.assumed.{AssumingOperatorAddress, AssumingPlatformOperator}
import models.operator.TinType.{Utr, Vrn}
import models.operator.responses.PlatformOperator
import models.operator.{AddressDetails, ContactDetails, TinDetails}
import models.submission.DeliveredSubmissionSortBy.SubmissionDate
import models.submission.SortOrder.Descending
import models.submission.SubmissionStatus.{Pending, Rejected, Success}
import models.submission.{DeliveredSubmission, DeliveredSubmissionSortBy, DeliveredSubmissions, ViewSubmissionsRequest}
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.xml.sax.ErrorHandler
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier

import java.nio.file.Paths
import java.time.{Clock, LocalDateTime, Year, ZoneOffset}
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import scala.concurrent.Future
import scala.xml.*

class AssumedReportingServiceSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfterEach
    with ScalaFutures
    with OptionValues
    with IntegrationPatience {

  private val now = LocalDateTime.of(2024, 12, 1, 12, 30, 45).toInstant(ZoneOffset.UTC)
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val mockUuidService = mock[UuidService]
  private val mockSubmissionConnector = mock[SubmissionConnector]
  private val mockDeliveredSubmissionConnector = mock[DeliveredSubmissionConnector]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[Clock].toInstance(clock),
        bind[UuidService].toInstance(mockUuidService),
        bind[SubmissionConnector].toInstance(mockSubmissionConnector),
        bind[DeliveredSubmissionConnector].toInstance(mockDeliveredSubmissionConnector)
      )
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockUuidService, mockSubmissionConnector, mockDeliveredSubmissionConnector)
  }

  private lazy val assumedReportingService: AssumedReportingService = app.injector.instanceOf[AssumedReportingService]

  "createSubmission" - {

    val dprsId = "dprsId"

    "when there is no existing manual assumed report for that reporting period" - {

      "must create a valid submission from complete data" in {

        val submissions = DeliveredSubmissions(
          submissions = Seq.empty,
          resultsCount = 1
        )
        when(mockDeliveredSubmissionConnector.get(any())(using any())).thenReturn(Future.successful(Some(submissions)))

        when(mockUuidService.generate()).thenReturn(
          "86233cb7-4922-4e54-a5ff-75f5e62eec0d",
          "eb6bb8e9-6879-4f4e-bebf-7d2f6a6d95c9",
          "a84001ba-ef8a-45f6-a47f-d5eca823fc33"
        )

        val operator = PlatformOperator(
          operatorId = "operatorId",
          operatorName = "operatorName",
          tinDetails = Seq(
            TinDetails(
              tin = "tin1",
              tinType = Utr,
              issuedBy = "GB"
            ),
            TinDetails(
              tin = "tin2",
              tinType = Vrn,
              issuedBy = "GB"
            )
          ),
          businessName = Some("businessName"),
          tradingName = Some("tradingName"),
          primaryContactDetails = ContactDetails(Some("phoneNumber"), "primaryContactName", "primaryEmail"),
          secondaryContactDetails = None,
          addressDetails = AddressDetails(
            line1 = "line1",
            line2 = Some("line2"),
            line3 = Some("line3"),
            line4 = Some("line4"),
            postCode = Some("postcode"),
            countryCode = Some("GB")
          ),
          notifications = Seq.empty
        )

        val assumingOperator = AssumingPlatformOperator(
          name = "assumingOperator",
          residentCountry = "US",
          tinDetails = Seq(
            TinDetails(
              tin = "tin3",
              tinType = Utr,
              issuedBy = "GB"
            ),
            TinDetails(
              tin = "tin4",
              tinType = Vrn,
              issuedBy = "GB"
            )
          ),
          address = AssumingOperatorAddress(
            line1 = "assumed line1",
            line2 = Some("assumed line2"),
            city = "assumed city",
            region = Some("assumed regionName"),
            postCode = "assumed postcode",
            country = "US"
          )
        )

        val expectedViewSubmissionsRequest = ViewSubmissionsRequest(
          subscriptionId = dprsId,
          assumedReporting = true,
          pageNumber = 1,
          sortBy = SubmissionDate,
          sortOrder = Descending,
          reportingPeriod = Some(2024),
          operatorId = Some("operatorId"),
          fileName = None,
          statuses = Seq(Pending, Rejected, Success)
        )

        val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/test.xml").toURI)
        val expectedPayload = Utility.trim(XML.loadString(expectedPayloadSource.mkString))
        expectedPayloadSource.close()

        val payload = assumedReportingService.createSubmission(dprsId, operator, assumingOperator, Year.of(2024))(using HeaderCarrier()).futureValue

        validate(payload.body)

        payload.messageRef mustEqual "GB2024GB-operatorId-86233cb749224e54a5ff75f5e62eec0d"
        payload.body mustEqual expectedPayload

        verify(mockDeliveredSubmissionConnector).get(eqTo(expectedViewSubmissionsRequest))(using any())
        verify(mockSubmissionConnector, never()).getManualAssumedReportingSubmission(any())(using any())
      }

      "must create a valid submission from minimal data" in {

        val submissions = DeliveredSubmissions(
          submissions = Seq.empty,
          resultsCount = 1
        )
        when(mockDeliveredSubmissionConnector.get(any())(using any())).thenReturn(Future.successful(Some(submissions)))

        when(mockUuidService.generate()).thenReturn(
          "86233cb7-4922-4e54-a5ff-75f5e62eec0d",
          "eb6bb8e9-6879-4f4e-bebf-7d2f6a6d95c9",
          "a84001ba-ef8a-45f6-a47f-d5eca823fc33"
        )

        val operator = PlatformOperator(
          operatorId = "operatorId",
          operatorName = "operatorName",
          tinDetails = Seq.empty,
          businessName = None,
          tradingName = None,
          primaryContactDetails = ContactDetails(Some("phoneNumber"), "primaryContactName", "primaryEmail"),
          secondaryContactDetails = None,
          addressDetails = AddressDetails(
            line1 = "line1",
            line2 = None,
            line3 = None,
            line4 = None,
            postCode = None,
            countryCode = Some("GB")
          ),
          notifications = Seq.empty
        )

        val assumingOperator = AssumingPlatformOperator(
          name = "assumingOperator",
          residentCountry = "US",
          tinDetails = Seq.empty,
          address = AssumingOperatorAddress(
            line1 = "assumed line1",
            line2 = None,
            city = "assumed city",
            region = None,
            postCode = "assumed postcode",
            country = "US"
          )
        )

        val expectedViewSubmissionsRequest = ViewSubmissionsRequest(
          subscriptionId = dprsId,
          assumedReporting = true,
          pageNumber = 1,
          sortBy = SubmissionDate,
          sortOrder = Descending,
          reportingPeriod = Some(2024),
          operatorId = Some("operatorId"),
          fileName = None,
          statuses = Seq(Pending, Rejected, Success)
        )

        val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/test2.xml").toURI)
        val expectedPayload = Utility.trim(XML.loadString(expectedPayloadSource.mkString))
        expectedPayloadSource.close()

        val payload = assumedReportingService.createSubmission(dprsId, operator, assumingOperator, Year.of(2024))(using HeaderCarrier()).futureValue

        validate(payload.body)

        payload.messageRef mustEqual "GB2024GB-operatorId-86233cb749224e54a5ff75f5e62eec0d"
        payload.body mustEqual expectedPayload

        verify(mockDeliveredSubmissionConnector).get(eqTo(expectedViewSubmissionsRequest))(using any())
        verify(mockSubmissionConnector, never()).getManualAssumedReportingSubmission(any())(using any())
      }
    }

    "when there is an existing manual assumed report for that reporting period" - {

      "must create a valid submission from complete data" in {

        val submissions = DeliveredSubmissions(
          submissions = Seq(
            DeliveredSubmission(
              conversationId = "conversationId",
              fileName = "test.xml",
              operatorId = "operatorId",
              operatorName = "operatorName",
              reportingPeriod = "2024",
              submissionCaseId = "submissionCaseId",
              submissionDateTime = now,
              submissionStatus = Success,
              assumingReporterName = Some("assumingReporterName")
            ),
            DeliveredSubmission(
              conversationId = "conversationId",
              fileName = "test2.xml",
              operatorId = "operatorId",
              operatorName = "operatorName",
              reportingPeriod = "2024",
              submissionCaseId = "submissionCaseId2",
              submissionDateTime = now,
              submissionStatus = Success,
              assumingReporterName = Some("assumingReporterName2")
            )
          ),
          resultsCount = 1
        )

        val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/test2.xml").toURI)
        val existingSubmission = scalaxb.fromXML[DPI_OECD](Utility.trim(XML.loadString(existingSubmissionSource.mkString)))
        existingSubmissionSource.close()

        when(mockDeliveredSubmissionConnector.get(any())(using any())).thenReturn(Future.successful(Some(submissions)))
        when(mockSubmissionConnector.getManualAssumedReportingSubmission(any())(using any())).thenReturn(Future.successful(existingSubmission))

        when(mockUuidService.generate()).thenReturn(
          "06abd30f-f302-4832-8a1c-028873b2f4bf",
          "507eb793-f0a5-4045-8828-5300f61e9bd3",
          "c181f0b5-f8f5-4046-9823-4b4978f9ed39"
        )

        val operator = PlatformOperator(
          operatorId = "operatorId",
          operatorName = "operatorName",
          tinDetails = Seq(
            TinDetails(
              tin = "tin1",
              tinType = Utr,
              issuedBy = "GB"
            ),
            TinDetails(
              tin = "tin2",
              tinType = Vrn,
              issuedBy = "GB"
            )
          ),
          businessName = Some("businessName"),
          tradingName = Some("tradingName"),
          primaryContactDetails = ContactDetails(Some("phoneNumber"), "primaryContactName", "primaryEmail"),
          secondaryContactDetails = None,
          addressDetails = AddressDetails(
            line1 = "line1",
            line2 = Some("line2"),
            line3 = Some("line3"),
            line4 = Some("line4"),
            postCode = Some("postcode"),
            countryCode = Some("GB")
          ),
          notifications = Seq.empty
        )

        val assumingOperator = AssumingPlatformOperator(
          name = "assumingOperator",
          residentCountry = "US",
          tinDetails = Seq(
            TinDetails(
              tin = "tin3",
              tinType = Utr,
              issuedBy = "GB"
            ),
            TinDetails(
              tin = "tin4",
              tinType = Vrn,
              issuedBy = "GB"
            )
          ),
          address = AssumingOperatorAddress(
            line1 = "assumed line1",
            line2 = Some("assumed line2"),
            city = "assumed city",
            region = Some("assumed regionName"),
            postCode = "assumed postcode",
            country = "US"
          )
        )

        val expectedViewSubmissionsRequest = ViewSubmissionsRequest(
          subscriptionId = dprsId,
          assumedReporting = true,
          pageNumber = 1,
          sortBy = SubmissionDate,
          sortOrder = Descending,
          reportingPeriod = Some(2024),
          operatorId = Some("operatorId"),
          fileName = None,
          statuses = Seq(Pending, Rejected, Success)
        )

        val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/test-update.xml").toURI)
        val expectedPayload = Utility.trim(XML.loadString(expectedPayloadSource.mkString))
        expectedPayloadSource.close()

        val payload = assumedReportingService.createSubmission(dprsId, operator, assumingOperator, Year.of(2024))(using HeaderCarrier()).futureValue

        validate(payload.body)

        payload.messageRef mustEqual "GB2024GB-operatorId-06abd30ff30248328a1c028873b2f4bf"
        payload.body mustEqual expectedPayload

        verify(mockDeliveredSubmissionConnector).get(eqTo(expectedViewSubmissionsRequest))(using any())
        verify(mockSubmissionConnector).getManualAssumedReportingSubmission(eqTo("submissionCaseId"))(using any())
      }

      "must create a valid submission from minimal data" in {

        val submissions = DeliveredSubmissions(
          submissions = Seq(
            DeliveredSubmission(
              conversationId = "conversationId",
              fileName = "test.xml",
              operatorId = "operatorId",
              operatorName = "operatorName",
              reportingPeriod = "2024",
              submissionCaseId = "submissionCaseId",
              submissionDateTime = now,
              submissionStatus = Success,
              assumingReporterName = Some("assumingReporterName")
            )
          ),
          resultsCount = 1
        )

        val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/test2.xml").toURI)
        val existingSubmission = scalaxb.fromXML[DPI_OECD](Utility.trim(XML.loadString(existingSubmissionSource.mkString)))
        existingSubmissionSource.close()

        when(mockDeliveredSubmissionConnector.get(any())(using any())).thenReturn(Future.successful(Some(submissions)))
        when(mockSubmissionConnector.getManualAssumedReportingSubmission(any())(using any())).thenReturn(Future.successful(existingSubmission))

        when(mockUuidService.generate()).thenReturn(
          "06abd30f-f302-4832-8a1c-028873b2f4bf",
          "507eb793-f0a5-4045-8828-5300f61e9bd3",
          "c181f0b5-f8f5-4046-9823-4b4978f9ed39"
        )

        val operator = PlatformOperator(
          operatorId = "operatorId",
          operatorName = "operatorName",
          tinDetails = Seq.empty,
          businessName = None,
          tradingName = None,
          primaryContactDetails = ContactDetails(Some("phoneNumber"), "primaryContactName", "primaryEmail"),
          secondaryContactDetails = None,
          addressDetails = AddressDetails(
            line1 = "line1",
            line2 = None,
            line3 = None,
            line4 = None,
            postCode = None,
            countryCode = Some("GB")
          ),
          notifications = Seq.empty
        )

        val assumingOperator = AssumingPlatformOperator(
          name = "assumingOperator",
          residentCountry = "US",
          tinDetails = Seq.empty,
          address = AssumingOperatorAddress(
            line1 = "assumed line1",
            line2 = None,
            city = "assumed city",
            region = None,
            postCode = "assumed postcode",
            country = "US"
          )
        )

        val expectedViewSubmissionsRequest = ViewSubmissionsRequest(
          subscriptionId = dprsId,
          assumedReporting = true,
          pageNumber = 1,
          sortBy = SubmissionDate,
          sortOrder = Descending,
          reportingPeriod = Some(2024),
          operatorId = Some("operatorId"),
          fileName = None,
          statuses = Seq(Pending, Rejected, Success)
        )

        val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/test2-update.xml").toURI)
        val expectedPayload = Utility.trim(XML.loadString(expectedPayloadSource.mkString))
        expectedPayloadSource.close()

        val payload = assumedReportingService.createSubmission(dprsId, operator, assumingOperator, Year.of(2024))(using HeaderCarrier()).futureValue

        validate(payload.body)

        payload.messageRef mustEqual "GB2024GB-operatorId-06abd30ff30248328a1c028873b2f4bf"
        payload.body mustEqual expectedPayload

        verify(mockDeliveredSubmissionConnector).get(eqTo(expectedViewSubmissionsRequest))(using any())
        verify(mockSubmissionConnector).getManualAssumedReportingSubmission(eqTo("submissionCaseId"))(using any())
      }
    }
  }

  private def validate(content: NodeSeq): Document = {

    val resource = Paths.get(getClass.getResource("/schemas/DPIXML_v1.08.xsd").toURI).toFile
    val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    val schemaFile = new StreamSource(resource)
    val schema = schemaFactory.newSchema(schemaFile)

    val parserFactory = SAXParserFactory.newInstance()
    parserFactory.setNamespaceAware(true)
    parserFactory.setSchema(schema)

    val reader = parserFactory.newSAXParser().getXMLReader
    reader.setErrorHandler(new ErrorHandler {
      override def warning(exception: SAXParseException): Unit = throw exception

      override def error(exception: SAXParseException): Unit = throw exception

      override def fatalError(exception: SAXParseException): Unit = throw exception
    })

    val xmlLoader = scala.xml.XML.withXMLReader(reader)

    try {
      xmlLoader.loadStringDocument(content.toString)
    } catch {
      case e: SAXParseException =>
        fail(s"Failed schema validation with: ${e.getMessage}")
    }
  }
}
