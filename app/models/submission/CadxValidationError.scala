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

package models.submission

import play.api.libs.json.{Json, JsonConfiguration, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

enum CadxValidationError {
  case FileError(submissionId: String, dprsId: String, code: String, detail: Option[String], created: Instant)
  case RowError(submissionId: String, dprsId: String, code: String, detail: Option[String], docRef: String, created: Instant)
}

object CadxValidationError {

  private given JsonConfiguration = JsonConfiguration(
    discriminator = "type",
    typeNaming = _.split("\\.").last
  )

  lazy val mongoFormat: OFormat[CadxValidationError] = {
    import MongoJavatimeFormats.Implicits.given

    given OFormat[FileError] = Json.format
    given OFormat[RowError] = Json.format

    Json.format
  }

  given OFormat[CadxValidationError] = {

    given OFormat[FileError] = Json.format
    given OFormat[RowError] = Json.format

    Json.format
  }
}
