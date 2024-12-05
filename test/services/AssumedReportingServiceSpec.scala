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
import models.assumed.AssumingPlatformOperator
import models.operator.TinType.{Other, Utr, Vrn}
import models.operator.responses.PlatformOperator
import models.operator.{AddressDetails, ContactDetails, TinDetails}
import models.submission.DeliveredSubmissionSortBy.SubmissionDate
import models.submission.SortOrder.Descending
import models.submission.SubmissionStatus.{Pending, Success}
import models.submission.*
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
import services.AssumedReportingService.{NoPreviousSubmissionException, SubmissionAlreadyDeletedException}
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

  private val dprsId = "dprsId"
    
  "createSubmission" - {

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
          registeredCountry = "US",
          address = "assumed line 1\nassumed line 2\nassumed line 3"
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
          statuses = Seq(Pending, Success)
        )

        val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/create/test.xml").toURI)
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
          registeredCountry = "US",
          address = "assumed line 1\nassumed line 2\nassumed line 3"
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
          statuses = Seq(Pending, Success)
        )

        val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/create/test2.xml").toURI)
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

      "when the latest submission is a create" - {

        "must create a valid submission from complete data" in {

          val submissions = DeliveredSubmissions(
            submissions = Seq(
              DeliveredSubmission(
                conversationId = "conversationId",
                fileName = "test.xml",
                operatorId = "operatorId",
                operatorName = "operatorName",
                reportingPeriod = Year.of(2024),
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
                reportingPeriod = Year.of(2024),
                submissionCaseId = "submissionCaseId2",
                submissionDateTime = now,
                submissionStatus = Success,
                assumingReporterName = Some("assumingReporterName2")
              )
            ),
            resultsCount = 1
          )

          val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/create/test2.xml").toURI)
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
            registeredCountry = "US",
            address = "assumed line 1\nassumed line 2\nassumed line 3"
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
            statuses = Seq(Pending, Success)
          )

          val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/update/test.xml").toURI)
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
                reportingPeriod = Year.of(2024),
                submissionCaseId = "submissionCaseId",
                submissionDateTime = now,
                submissionStatus = Success,
                assumingReporterName = Some("assumingReporterName")
              )
            ),
            resultsCount = 1
          )

          val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/create/test2.xml").toURI)
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
            registeredCountry = "US",
            address = "assumed line 1\nassumed line 2\nassumed line 3"
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
            statuses = Seq(Pending, Success)
          )

          val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/update/test2.xml").toURI)
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

      "when the latest submission is an update" - {

        "must create a valid submission from complete data" in {

          val submissions = DeliveredSubmissions(
            submissions = Seq(
              DeliveredSubmission(
                conversationId = "conversationId",
                fileName = "test.xml",
                operatorId = "operatorId",
                operatorName = "operatorName",
                reportingPeriod = Year.of(2024),
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
                reportingPeriod = Year.of(2024),
                submissionCaseId = "submissionCaseId2",
                submissionDateTime = now,
                submissionStatus = Success,
                assumingReporterName = Some("assumingReporterName2")
              )
            ),
            resultsCount = 1
          )

          val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/update/test2.xml").toURI)
          val existingSubmission = scalaxb.fromXML[DPI_OECD](Utility.trim(XML.loadString(existingSubmissionSource.mkString)))
          existingSubmissionSource.close()

          when(mockDeliveredSubmissionConnector.get(any())(using any())).thenReturn(Future.successful(Some(submissions)))
          when(mockSubmissionConnector.getManualAssumedReportingSubmission(any())(using any())).thenReturn(Future.successful(existingSubmission))

          when(mockUuidService.generate()).thenReturn(
            "6059e859-7cca-47b1-8861-9197197b076c",
            "5cb7aa20-a7af-4464-8bc4-d1181f69a77c",
            "7e46fb7a-d790-4dba-9b4a-d76100d08f11"
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
            registeredCountry = "US",
            address = "assumed line 1\nassumed line 2\nassumed line 3"
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
            statuses = Seq(Pending, Success)
          )

          val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/update/test3.xml").toURI)
          val expectedPayload = Utility.trim(XML.loadString(expectedPayloadSource.mkString))
          expectedPayloadSource.close()

          val payload = assumedReportingService.createSubmission(dprsId, operator, assumingOperator, Year.of(2024))(using HeaderCarrier()).futureValue

          validate(payload.body)

          payload.messageRef mustEqual "GB2024GB-operatorId-6059e8597cca47b188619197197b076c"
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
                reportingPeriod = Year.of(2024),
                submissionCaseId = "submissionCaseId",
                submissionDateTime = now,
                submissionStatus = Success,
                assumingReporterName = Some("assumingReporterName")
              )
            ),
            resultsCount = 1
          )

          val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/update/test2.xml").toURI)
          val existingSubmission = scalaxb.fromXML[DPI_OECD](Utility.trim(XML.loadString(existingSubmissionSource.mkString)))
          existingSubmissionSource.close()

          when(mockDeliveredSubmissionConnector.get(any())(using any())).thenReturn(Future.successful(Some(submissions)))
          when(mockSubmissionConnector.getManualAssumedReportingSubmission(any())(using any())).thenReturn(Future.successful(existingSubmission))

          when(mockUuidService.generate()).thenReturn(
            "6059e859-7cca-47b1-8861-9197197b076c",
            "5cb7aa20-a7af-4464-8bc4-d1181f69a77c",
            "7e46fb7a-d790-4dba-9b4a-d76100d08f11"
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
            registeredCountry = "US",
            address = "assumed line 1\nassumed line 2\nassumed line 3"
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
            statuses = Seq(Pending, Success)
          )

          val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/update/test4.xml").toURI)
          val expectedPayload = Utility.trim(XML.loadString(expectedPayloadSource.mkString))
          expectedPayloadSource.close()

          val payload = assumedReportingService.createSubmission(dprsId, operator, assumingOperator, Year.of(2024))(using HeaderCarrier()).futureValue

          validate(payload.body)

          payload.messageRef mustEqual "GB2024GB-operatorId-6059e8597cca47b188619197197b076c"
          payload.body mustEqual expectedPayload

          verify(mockDeliveredSubmissionConnector).get(eqTo(expectedViewSubmissionsRequest))(using any())
          verify(mockSubmissionConnector).getManualAssumedReportingSubmission(eqTo("submissionCaseId"))(using any())
        }
      }

      "when the latest submission is a delete" - {

        "must create a valid submission from complete data" in {

          val submissions = DeliveredSubmissions(
            submissions = Seq(
              DeliveredSubmission(
                conversationId = "conversationId",
                fileName = "test.xml",
                operatorId = "operatorId",
                operatorName = "operatorName",
                reportingPeriod = Year.of(2024),
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
                reportingPeriod = Year.of(2024),
                submissionCaseId = "submissionCaseId2",
                submissionDateTime = now,
                submissionStatus = Success,
                assumingReporterName = Some("assumingReporterName2")
              )
            ),
            resultsCount = 1
          )

          val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/delete/test.xml").toURI)
          val existingSubmission = scalaxb.fromXML[DPI_OECD](Utility.trim(XML.loadString(existingSubmissionSource.mkString)))
          existingSubmissionSource.close()

          when(mockDeliveredSubmissionConnector.get(any())(using any())).thenReturn(Future.successful(Some(submissions)))
          when(mockSubmissionConnector.getManualAssumedReportingSubmission(any())(using any())).thenReturn(Future.successful(existingSubmission))

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
            registeredCountry = "US",
            address = "assumed line 1\nassumed line 2\nassumed line 3"
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
            statuses = Seq(Pending, Success)
          )

          val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/create/test.xml").toURI)
          val expectedPayload = Utility.trim(XML.loadString(expectedPayloadSource.mkString))
          expectedPayloadSource.close()

          val payload = assumedReportingService.createSubmission(dprsId, operator, assumingOperator, Year.of(2024))(using HeaderCarrier()).futureValue

          validate(payload.body)

          payload.messageRef mustEqual "GB2024GB-operatorId-86233cb749224e54a5ff75f5e62eec0d"
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
                reportingPeriod = Year.of(2024),
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
                reportingPeriod = Year.of(2024),
                submissionCaseId = "submissionCaseId2",
                submissionDateTime = now,
                submissionStatus = Success,
                assumingReporterName = Some("assumingReporterName2")
              )
            ),
            resultsCount = 1
          )

          val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/delete/test.xml").toURI)
          val existingSubmission = scalaxb.fromXML[DPI_OECD](Utility.trim(XML.loadString(existingSubmissionSource.mkString)))
          existingSubmissionSource.close()

          when(mockDeliveredSubmissionConnector.get(any())(using any())).thenReturn(Future.successful(Some(submissions)))
          when(mockSubmissionConnector.getManualAssumedReportingSubmission(any())(using any())).thenReturn(Future.successful(existingSubmission))

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
            registeredCountry = "US",
            address = "assumed line 1\nassumed line 2\nassumed line 3"
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
            statuses = Seq(Pending, Success)
          )

          val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/create/test2.xml").toURI)
          val expectedPayload = Utility.trim(XML.loadString(expectedPayloadSource.mkString))
          expectedPayloadSource.close()

          val payload = assumedReportingService.createSubmission(dprsId, operator, assumingOperator, Year.of(2024))(using HeaderCarrier()).futureValue

          validate(payload.body)

          payload.messageRef mustEqual "GB2024GB-operatorId-86233cb749224e54a5ff75f5e62eec0d"
          payload.body mustEqual expectedPayload

          verify(mockDeliveredSubmissionConnector).get(eqTo(expectedViewSubmissionsRequest))(using any())
          verify(mockSubmissionConnector).getManualAssumedReportingSubmission(eqTo("submissionCaseId"))(using any())
        }
      }

      "when the latest submission is pending" - {

        "must fail" in {

          val submissions = DeliveredSubmissions(
            submissions = Seq(
              DeliveredSubmission(
                conversationId = "conversationId",
                fileName = "test.xml",
                operatorId = "operatorId",
                operatorName = "operatorName",
                reportingPeriod = Year.of(2024),
                submissionCaseId = "submissionCaseId",
                submissionDateTime = now,
                submissionStatus = Pending,
                assumingReporterName = Some("assumingReporterName")
              ),
              DeliveredSubmission(
                conversationId = "conversationId",
                fileName = "test2.xml",
                operatorId = "operatorId",
                operatorName = "operatorName",
                reportingPeriod = Year.of(2024),
                submissionCaseId = "submissionCaseId2",
                submissionDateTime = now,
                submissionStatus = Success,
                assumingReporterName = Some("assumingReporterName2")
              )
            ),
            resultsCount = 1
          )

          val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/create/test2.xml").toURI)
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
            registeredCountry = "US",
            address = "assumed line 1\nassumed line 2\nassumed line 3"
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
            statuses = Seq(Pending, Success)
          )

          assumedReportingService.createSubmission(dprsId, operator, assumingOperator, Year.of(2024))(using HeaderCarrier()).failed.futureValue

          verify(mockDeliveredSubmissionConnector).get(eqTo(expectedViewSubmissionsRequest))(using any())
          verify(mockSubmissionConnector, never()).getManualAssumedReportingSubmission(any())(using any())
        }
      }
    }
  }

  "createDeleteSubmission" - {

    "when there is an existing manual assumed report submission" - {

      "when the latest submission is a create" - {

        "must create a valid submission" in {

          val submissions = DeliveredSubmissions(
            submissions = Seq(
              DeliveredSubmission(
                conversationId = "conversationId",
                fileName = "test.xml",
                operatorId = "operatorId",
                operatorName = "operatorName",
                reportingPeriod = Year.of(2024),
                submissionCaseId = "submissionCaseId",
                submissionDateTime = now,
                submissionStatus = Success,
                assumingReporterName = Some("assumingReporterName")
              )
            ),
            resultsCount = 1
          )

          val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/create/test2.xml").toURI)
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

          val expectedViewSubmissionsRequest = ViewSubmissionsRequest(
            subscriptionId = dprsId,
            assumedReporting = true,
            pageNumber = 1,
            sortBy = SubmissionDate,
            sortOrder = Descending,
            reportingPeriod = Some(2024),
            operatorId = Some("operatorId"),
            fileName = None,
            statuses = Seq(Pending, Success)
          )

          val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/delete/test.xml").toURI)
          val expectedPayload = Utility.trim(XML.loadString(expectedPayloadSource.mkString))
          expectedPayloadSource.close()

          val payload = assumedReportingService.createDeleteSubmission(dprsId, operator.operatorId, Year.of(2024))(using HeaderCarrier()).futureValue

          validate(payload.body)

          payload.messageRef mustEqual "GB2024GB-operatorId-06abd30ff30248328a1c028873b2f4bf"
          payload.body mustEqual expectedPayload

          verify(mockDeliveredSubmissionConnector).get(eqTo(expectedViewSubmissionsRequest))(using any())
          verify(mockSubmissionConnector).getManualAssumedReportingSubmission(eqTo("submissionCaseId"))(using any())
        }
      }

      "when the latest submission is an update" - {

        "must create a valid submission" in {

          val submissions = DeliveredSubmissions(
            submissions = Seq(
              DeliveredSubmission(
                conversationId = "conversationId",
                fileName = "test.xml",
                operatorId = "operatorId",
                operatorName = "operatorName",
                reportingPeriod = Year.of(2024),
                submissionCaseId = "submissionCaseId",
                submissionDateTime = now,
                submissionStatus = Success,
                assumingReporterName = Some("assumingReporterName")
              )
            ),
            resultsCount = 1
          )

          val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/update/test2.xml").toURI)
          val existingSubmission = scalaxb.fromXML[DPI_OECD](Utility.trim(XML.loadString(existingSubmissionSource.mkString)))
          existingSubmissionSource.close()

          when(mockDeliveredSubmissionConnector.get(any())(using any())).thenReturn(Future.successful(Some(submissions)))
          when(mockSubmissionConnector.getManualAssumedReportingSubmission(any())(using any())).thenReturn(Future.successful(existingSubmission))

          when(mockUuidService.generate()).thenReturn(
            "0488fc70-bc98-42d7-b49c-583d5d74768f",
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

          val expectedViewSubmissionsRequest = ViewSubmissionsRequest(
            subscriptionId = dprsId,
            assumedReporting = true,
            pageNumber = 1,
            sortBy = SubmissionDate,
            sortOrder = Descending,
            reportingPeriod = Some(2024),
            operatorId = Some("operatorId"),
            fileName = None,
            statuses = Seq(Pending, Success)
          )

          val expectedPayloadSource = scala.io.Source.fromFile(getClass.getResource("/assumed/delete/test2.xml").toURI)
          val expectedPayload = Utility.trim(XML.loadString(expectedPayloadSource.mkString))
          expectedPayloadSource.close()

          val payload = assumedReportingService.createDeleteSubmission(dprsId, operator.operatorId, Year.of(2024))(using HeaderCarrier()).futureValue

          validate(payload.body)

          payload.messageRef mustEqual "GB2024GB-operatorId-0488fc70bc9842d7b49c583d5d74768f"
          payload.body mustEqual expectedPayload

          verify(mockDeliveredSubmissionConnector).get(eqTo(expectedViewSubmissionsRequest))(using any())
          verify(mockSubmissionConnector).getManualAssumedReportingSubmission(eqTo("submissionCaseId"))(using any())
        }
      }

      "when the latest submission is a delete" - {

        "must fail to create a submission" in {

          val submissions = DeliveredSubmissions(
            submissions = Seq(
              DeliveredSubmission(
                conversationId = "conversationId",
                fileName = "test.xml",
                operatorId = "operatorId",
                operatorName = "operatorName",
                reportingPeriod = Year.of(2024),
                submissionCaseId = "submissionCaseId",
                submissionDateTime = now,
                submissionStatus = Success,
                assumingReporterName = Some("assumingReporterName")
              )
            ),
            resultsCount = 1
          )

          val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/delete/test.xml").toURI)
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

          val failure = assumedReportingService.createDeleteSubmission(dprsId, operator.operatorId, Year.of(2024))(using HeaderCarrier()).failed.futureValue
          failure mustBe a[SubmissionAlreadyDeletedException]
        }
      }
    }

    "when there is no existing manual assumed report submission" - {

      "must fail to create a submission" in {

        val submissions = DeliveredSubmissions(
          submissions = Seq.empty,
          resultsCount = 0
        )

        when(mockDeliveredSubmissionConnector.get(any())(using any())).thenReturn(Future.successful(Some(submissions)))

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

        val failure = assumedReportingService.createDeleteSubmission(dprsId, operator.operatorId, Year.of(2024))(using HeaderCarrier()).failed.futureValue
        failure mustBe a[NoPreviousSubmissionException]
      }
    }
  }

  "getSubmission" - {
    
    "must return a submission with complete data" in {

      val submissions = DeliveredSubmissions(
        submissions = Seq(
          DeliveredSubmission(
            conversationId = "conversationId",
            fileName = "test.xml",
            operatorId = "operatorId",
            operatorName = "operatorName",
            reportingPeriod = Year.of(2024),
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
            reportingPeriod = Year.of(2024),
            submissionCaseId = "submissionCaseId2",
            submissionDateTime = now,
            submissionStatus = Success,
            assumingReporterName = Some("assumingReporterName2")
          )
        ),
        resultsCount = 2
      )

      val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/update/test.xml").toURI)
      val existingSubmission = scalaxb.fromXML[DPI_OECD](XML.loadString(existingSubmissionSource.mkString))
      existingSubmissionSource.close()

      when(mockDeliveredSubmissionConnector.get(any())(using any())).thenReturn(Future.successful(Some(submissions)))
      when(mockSubmissionConnector.getManualAssumedReportingSubmission(any())(using any())).thenReturn(Future.successful(existingSubmission))

      val expectedAssumingOperator = AssumingPlatformOperator(
        name = "assumingOperator",
        residentCountry = "US",
        tinDetails = Seq(
          TinDetails(
            tin = "tin3",
            tinType = Other,
            issuedBy = "GB"
          ),
          TinDetails(
            tin = "tin4",
            tinType = Other,
            issuedBy = "GB"
          )
        ),
        registeredCountry = "US",
        address = "assumed line 1\nassumed line 2\nassumed line 3"
      )
      
      val expectedAssumedReportingSubmission = AssumedReportingSubmission(
        operatorId       = "operatorId",
        operatorName     = "operatorName",
        assumingOperator = expectedAssumingOperator,
        reportingPeriod  = Year.of(2024),
        isDeleted        = false
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
        statuses = Seq(Pending, Success)
      )

      val result = assumedReportingService.getSubmission(dprsId, "operatorId", Year.of(2024))(using HeaderCarrier()).futureValue
      
      result.value mustEqual expectedAssumedReportingSubmission
      
      verify(mockDeliveredSubmissionConnector).get(eqTo(expectedViewSubmissionsRequest))(using any())
      verify(mockSubmissionConnector).getManualAssumedReportingSubmission(eqTo("submissionCaseId"))(using any())
    }
    
    "must return a submission with minimal data" in {

      val submissions = DeliveredSubmissions(
        submissions = Seq(
          DeliveredSubmission(
            conversationId = "conversationId",
            fileName = "test.xml",
            operatorId = "operatorId",
            operatorName = "operatorName",
            reportingPeriod = Year.of(2024),
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
            reportingPeriod = Year.of(2024),
            submissionCaseId = "submissionCaseId2",
            submissionDateTime = now,
            submissionStatus = Success,
            assumingReporterName = Some("assumingReporterName2")
          )
        ),
        resultsCount = 2
      )

      val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/update/test2.xml").toURI)
      val existingSubmission = scalaxb.fromXML[DPI_OECD](XML.loadString(existingSubmissionSource.mkString))
      existingSubmissionSource.close()

      when(mockDeliveredSubmissionConnector.get(any())(using any())).thenReturn(Future.successful(Some(submissions)))
      when(mockSubmissionConnector.getManualAssumedReportingSubmission(any())(using any())).thenReturn(Future.successful(existingSubmission))

      val expectedAssumingOperator = AssumingPlatformOperator(
        name = "assumingOperator",
        residentCountry = "US",
        tinDetails = Nil,
        registeredCountry = "US",
        address = "assumed line 1\nassumed line 2\nassumed line 3"
      )

      val expectedAssumedReportingSubmission = AssumedReportingSubmission(
        operatorId       = "operatorId",
        operatorName     = "operatorName",
        assumingOperator = expectedAssumingOperator,
        reportingPeriod  = Year.of(2024),
        isDeleted        = false
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
        statuses = Seq(Pending, Success)
      )

      val result = assumedReportingService.getSubmission(dprsId, "operatorId", Year.of(2024))(using HeaderCarrier()).futureValue

      result.value mustEqual expectedAssumedReportingSubmission

      verify(mockDeliveredSubmissionConnector).get(eqTo(expectedViewSubmissionsRequest))(using any())
      verify(mockSubmissionConnector).getManualAssumedReportingSubmission(eqTo("submissionCaseId"))(using any())
    }
    
    "must return None when there are no previous submissions" in {

      val submissions = DeliveredSubmissions(
        submissions = Seq.empty,
        resultsCount = 0
      )

      when(mockDeliveredSubmissionConnector.get(any())(using any())).thenReturn(Future.successful(Some(submissions)))

      val result = assumedReportingService.getSubmission(dprsId, "operatorId", Year.of(2024))(using HeaderCarrier()).futureValue
      
      result must not be defined
    }
  }

  "must return None when there is a non-string address" in {

    val submissions = DeliveredSubmissions(
      submissions = Seq(
        DeliveredSubmission(
          conversationId = "conversationId",
          fileName = "test.xml",
          operatorId = "operatorId",
          operatorName = "operatorName",
          reportingPeriod = Year.of(2024),
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
          reportingPeriod = Year.of(2024),
          submissionCaseId = "submissionCaseId2",
          submissionDateTime = now,
          submissionStatus = Success,
          assumingReporterName = Some("assumingReporterName2")
        )
      ),
      resultsCount = 2
    )

    val existingSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/create/test3.xml").toURI)
    val existingSubmission = scalaxb.fromXML[DPI_OECD](XML.loadString(existingSubmissionSource.mkString))
    existingSubmissionSource.close()

    when(mockDeliveredSubmissionConnector.get(any())(using any())).thenReturn(Future.successful(Some(submissions)))
    when(mockSubmissionConnector.getManualAssumedReportingSubmission(any())(using any())).thenReturn(Future.successful(existingSubmission))

    val expectedViewSubmissionsRequest = ViewSubmissionsRequest(
      subscriptionId = dprsId,
      assumedReporting = true,
      pageNumber = 1,
      sortBy = SubmissionDate,
      sortOrder = Descending,
      reportingPeriod = Some(2024),
      operatorId = Some("operatorId"),
      fileName = None,
      statuses = Seq(Pending, Success)
    )

    val result = assumedReportingService.getSubmission(dprsId, "operatorId", Year.of(2024))(using HeaderCarrier()).futureValue

    result mustBe None

    verify(mockDeliveredSubmissionConnector).get(eqTo(expectedViewSubmissionsRequest))(using any())
    verify(mockSubmissionConnector).getManualAssumedReportingSubmission(eqTo("submissionCaseId"))(using any())
  }

  private def validate(content: NodeSeq): Document = {

    val resource = Paths.get(getClass.getResource("/schemas/DPIXML_v1.0.xsd").toURI).toFile
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
