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
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.StreamConverters
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import play.api.{Configuration, Environment}
import services.ValidatingSaxHandler.{FatalSaxParsingException, platformOperatorPath, reportingPeriodPath}
import services.ValidationService.ValidationError

import java.net.URL
import java.nio.file.Paths
import java.time.Year
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NoStackTrace
import scala.xml.SAXParseException

@Singleton
class ValidationService @Inject() (
                                   downloadConnector: DownloadConnector,
                                   configuration: Configuration,
                                   environment: Environment
                                  )(using ExecutionContext, Materializer) {

  private val schemaPath = configuration.get[String]("validation.schema-path")
  private val resource = environment.resource(schemaPath).map(url => Paths.get(url.toURI).toFile)
    .getOrElse(throw new RuntimeException(s"No XSD found at $schemaPath"))

  private val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
  private val schemaFile = new StreamSource(resource)
  private val schema = schemaFactory.newSchema(schemaFile)

  private val parserFactory = SAXParserFactory.newInstance()
  parserFactory.setNamespaceAware(true)
  parserFactory.setSchema(schema)

  def validateXml(downloadUrl: URL, platformOperatorId: String): Future[Either[ValidationError, Year]] = {
    val parser = parserFactory.newSAXParser()
    // TODO use blocking execution context
    downloadConnector.download(downloadUrl).map { source =>
      val inputStream = source.runWith(StreamConverters.asInputStream())
      try {
        val handler = new ValidatingSaxHandler(platformOperatorId)
        parser.parse(inputStream, handler)

        for {
          _               <- handler.getPlatformOperatorId
          reportingPeriod <- handler.getReportingPeriod
        } yield reportingPeriod
      } catch {
        case _: FatalSaxParsingException =>
          Left(ValidationError("not-xml"))
        case _: SAXParseException =>
          Left(ValidationError("schema"))
      }
    }
  }
}

object ValidationService {

  final case class ValidationError(reason: String)
}

final class ValidatingSaxHandler(platformOperatorId: String) extends DefaultHandler {

  override def warning(e: SAXParseException): Unit = throw e
  override def error(e: SAXParseException): Unit = throw e
  override def fatalError(e: SAXParseException): Unit = throw FatalSaxParsingException(e)

  private var path: List[String] = Nil
  private val platformOperatorBuilder = new java.lang.StringBuilder()
  private val reportingPeriodBuilder = new java.lang.StringBuilder()

  override def startElement(uri: String, localName: String, qName: String, attributes: Attributes): Unit =
    path = localName :: path

  override def endElement(uri: String, localName: String, qName: String): Unit =
    path = path.tail

  override def characters(ch: Array[Char], start: Int, length: Int): Unit =
    path match {
      case `platformOperatorPath` =>
        platformOperatorBuilder.append(ch, start, length)
      case `reportingPeriodPath` =>
        reportingPeriodBuilder.append(ch, start, length)
      case _ => ()
    }

  def getPlatformOperatorId: Either[ValidationError, String] =
    if (platformOperatorBuilder.length == 0) {
      Left(ValidationError("poid.missing"))
    } else if (platformOperatorBuilder.toString != platformOperatorId) {
      Left(ValidationError("poid.incorrect"))
    } else {
      Right(platformOperatorBuilder.toString)
    }

  def getReportingPeriod: Either[ValidationError, Year] =
    Try(Year.from(DateTimeFormatter.ISO_DATE.parse(reportingPeriodBuilder.toString)))
      .toEither
      .left.map(_ => ValidationError("reporting-period.invalid"))
}

object ValidatingSaxHandler {

  private val platformOperatorPath: List[String] = List("SendingEntityIN", "MessageSpec", "DPI_OECD")
  private val reportingPeriodPath: List[String] = List("ReportingPeriod", "MessageSpec", "DPI_OECD")

  final case class FatalSaxParsingException(error: SAXParseException) extends Throwable with NoStackTrace {
    override def getCause: Throwable = error
  }
}
