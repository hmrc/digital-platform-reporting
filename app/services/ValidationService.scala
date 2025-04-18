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

import cats.data.EitherT
import connectors.DownloadConnector
import models.submission.Submission.UploadFailureReason
import models.submission.Submission.UploadFailureReason.*
import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.StreamConverters
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import play.api.{Configuration, Environment}
import services.ValidatingSaxHandler.{FatalSaxParsingException, platformOperatorPath, reportingPeriodPath}
import uk.gov.hmrc.http.HeaderCarrier
import utils.FileUtils.stripExtension

import java.io.UnsupportedEncodingException
import java.net.URL
import java.nio.file.Paths
import java.time.Year
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import scala.util.control.NoStackTrace
import scala.xml.SAXParseException

@Singleton
class ValidationService @Inject()(downloadConnector: DownloadConnector,
                                  configuration: Configuration,
                                  environment: Environment,
                                  assumedReportingService: AssumedReportingService)
                                 (using ExecutionContext, Materializer) {

  private val schemaPath = configuration.get[String]("validation.schema-path")
  private val errorLimit = configuration.get[Int]("validation.error-limit")
  private val maxFileNameSize = configuration.get[Int]("max-filename-size") 
  private val resource = environment.resource(schemaPath).map(url => Paths.get(url.toURI).toFile)
    .getOrElse(throw new RuntimeException(s"No XSD found at $schemaPath"))

  private val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
  private val schemaFile = new StreamSource(resource)
  private val schema = schemaFactory.newSchema(schemaFile)

  private val parserFactory = SAXParserFactory.newInstance()
  parserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
  parserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false)
  parserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
  parserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
  parserFactory.setXIncludeAware(false)
  parserFactory.setNamespaceAware(true)
  parserFactory.setSchema(schema)

  def validateXml(fileName: String, dprsId: String, downloadUrl: URL, platformOperatorId: String): Future[Either[UploadFailureReason, Year]] = {
    if(stripExtension(fileName).length > maxFileNameSize){
      Future.successful(Left(FileNameTooLong))
    } else if (fileName.toLowerCase.endsWith(".xml")) {
      val parser = parserFactory.newSAXParser()
      downloadConnector.download(downloadUrl).flatMap { source =>
        val inputStream = source.runWith(StreamConverters.asInputStream())
        val handler = new ValidatingSaxHandler(platformOperatorId, errorLimit)
        try {
          parser.parse(inputStream, handler)

          val result = for {
            _ <- handler.checkErrors
            operatorId <- handler.getPlatformOperatorId
            reportingPeriod <- handler.getReportingPeriod
            _ <- checkForManualAssumedReport(dprsId, operatorId, reportingPeriod)
          } yield reportingPeriod

          result.value
        } catch {
          case _: FatalSaxParsingException =>
            val hasMoreErrors: Boolean = handler.schemaErrors.length >= errorLimit
            Future.successful(Left(SchemaValidationError(handler.schemaErrors.result, moreErrors = hasMoreErrors)))
          case _: SAXParseException =>
            Future.successful(Left(SchemaValidationError(handler.schemaErrors.result, moreErrors = true)))
          case _: UnsupportedEncodingException => Future.successful(Left(SchemaValidationError(
            Seq(SchemaValidationError.Error(1, 1, "XML encoding must be UTF-8")), moreErrors = false
          )))
        }
      }
    } else {
      Future.successful(Left(InvalidFileNameExtension))
    }
  }

  private def checkForManualAssumedReport(dprsId: String, operatorId: String, reportingPeriod: Year): EitherT[Future, UploadFailureReason, Done] = {

    given hc: HeaderCarrier = HeaderCarrier()

    EitherT(assumedReportingService.getSubmission(dprsId, operatorId, reportingPeriod).map(
      _.map { assumedReport =>
        if (assumedReport.isDeleted) {
          Right(Done)
        } else {
          Left(ManualAssumedReportExists)
        }
      }.getOrElse(Right(Done))
    ))
  }
}

final class ValidatingSaxHandler(platformOperatorId: String, errorLimit: Int) extends DefaultHandler {

  override def warning(e: SAXParseException): Unit = addError(e)
  override def error(e: SAXParseException): Unit = addError(e)
  override def fatalError(e: SAXParseException): Unit = {
    schemaErrors.addOne(SchemaValidationError.Error(e.getLineNumber, e.getColumnNumber, e.getMessage))
    throw FatalSaxParsingException(e)
  }

  private def addError(e: SAXParseException): Unit =
    if (schemaErrors.length >= errorLimit) {
      throw e
    } else {
      schemaErrors.addOne(SchemaValidationError.Error(e.getLineNumber, e.getColumnNumber, e.getMessage))
    }

  private var path: List[String] = Nil
  private val platformOperatorBuilder = new java.lang.StringBuilder()
  private val reportingPeriodBuilder = new java.lang.StringBuilder()

  val schemaErrors: ListBuffer[SchemaValidationError.Error] = ListBuffer.empty

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

  def getPlatformOperatorId(using ExecutionContext): EitherT[Future, UploadFailureReason, String] =
    EitherT.fromEither(if (platformOperatorBuilder.length == 0) {
      Left(PlatformOperatorIdMissing)
    } else if (platformOperatorBuilder.toString != platformOperatorId) {
      Left(PlatformOperatorIdMismatch(platformOperatorId, platformOperatorBuilder.toString))
    } else {
      Right(platformOperatorBuilder.toString)
    })

  def getReportingPeriod(using ExecutionContext): EitherT[Future, UploadFailureReason, Year] =
    EitherT.fromEither(Try(Year.from(DateTimeFormatter.ISO_DATE.parse(reportingPeriodBuilder.toString)))
      .toEither
      .left.map(_ => ReportingPeriodInvalid))

  def checkErrors(using ExecutionContext): EitherT[Future, UploadFailureReason, Unit] =
    EitherT.fromEither {
      if (schemaErrors.isEmpty) {
        Right(())
      } else {
        Left(SchemaValidationError(schemaErrors.result, moreErrors = false))
      }
    }
}

object ValidatingSaxHandler {

  private val platformOperatorPath: List[String] = List("SendingEntityIN", "MessageSpec", "DPI_OECD")
  private val reportingPeriodPath: List[String] = List("ReportingPeriod", "MessageSpec", "DPI_OECD")

  final case class FatalSaxParsingException(error: SAXParseException) extends Throwable with NoStackTrace {
    override def getCause: Throwable = error
  }
}
