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

import models.assumed.{AssumingOperatorAddress, AssumingPlatformOperator}
import models.operator.TinType.{Utr, Vrn}
import models.operator.responses.PlatformOperator
import models.operator.{AddressDetails, ContactDetails, TinDetails}
import org.mockito.Mockito.when
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

import java.nio.file.Paths
import java.time.{Clock, LocalDateTime, Year, ZoneOffset}
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
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

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[Clock].toInstance(clock),
        bind[UuidService].toInstance(mockUuidService)
      )
      .build()

  private lazy val assumedReportingService: AssumedReportingService = app.injector.instanceOf[AssumedReportingService]

  "createSubmission" - {

    "must create a valid submission from complete data" in {

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

      val expectedSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/test.xml").toURI)
      val expectedSubmission = Utility.trim(XML.loadString(expectedSubmissionSource.mkString))
      expectedSubmissionSource.close()

      val submission = assumedReportingService.createSubmission(operator, assumingOperator, Year.of(2024))
      submission mustEqual expectedSubmission

      validate(submission)
    }

    "must create a valid submission from minimal data" in {

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

      val expectedSubmissionSource = scala.io.Source.fromFile(getClass.getResource("/assumed/test2.xml").toURI)
      val expectedSubmission = Utility.trim(XML.loadString(expectedSubmissionSource.mkString))
      expectedSubmissionSource.close()

      val submission = assumedReportingService.createSubmission(operator, assumingOperator, Year.of(2024))
      submission mustEqual expectedSubmission

      validate(submission)
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
