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
import play.api.{Configuration, Environment}
import services.ValidationService.ValidationError

import java.net.URL
import javax.inject.{Inject, Singleton}
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.SAXParseException

@Singleton
class ValidationService @Inject() (
                                   downloadConnector: DownloadConnector,
                                   configuration: Configuration,
                                   environment: Environment
                                  )(using ExecutionContext, Materializer) {

  private val schemaPath = configuration.get[String]("validation.schema-path")
  private val resource = environment.resourceAsStream(schemaPath).getOrElse(throw new RuntimeException(s"No XSD found at $schemaPath"))

  private val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
  private val schemaFile = new StreamSource(resource)
  private val schema = schemaFactory.newSchema(schemaFile)

  def validateXml(downloadUrl: URL): Future[Option[ValidationError]] = {
    val validator = schema.newValidator()
    // TODO use blocking execution context
    downloadConnector.download(downloadUrl).map { source =>
      val inputStream = source.runWith(StreamConverters.asInputStream())
      try {
        validator.validate(new StreamSource(inputStream))
        None
      } catch { case _: SAXParseException =>
        Some(ValidationError("error.schema"))
      }
    }
  }
}

object ValidationService {

  final case class ValidationError(reason: String)
}