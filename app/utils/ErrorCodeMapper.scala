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

package utils

import play.api.Logging
import play.api.libs.json._

import scala.io.Source

object ErrorCodeMapper extends Logging {

  private lazy val errorDescriptions: Map[String, String] = loadErrorDescriptions("conf/error_codes.json")

  def loadErrorDescriptions(filePath: String): Map[String, String] = {
    val source = Source.fromFile(filePath)
    try {
      val jsonStr = source.mkString
      Json.parse(jsonStr).as[Map[String, String]].map {
        case (key, value) => key -> value
      }
    } catch {
      case ex: Exception =>
        logger.warn(s"Error loading error descriptions: ${ex.getMessage}")
        Map.empty[String, String]
    } finally {
      source.close()
    }
  }

  def getErrorDescription(errorCode: String, detail: Option[String]): String =
    errorDescriptions.getOrElse(errorCode, detail.getOrElse("Unknown error"))

}
