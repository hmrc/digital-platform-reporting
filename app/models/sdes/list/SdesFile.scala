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

package models.sdes.list

import models.urlFormat
import play.api.libs.functional.syntax.*
import play.api.libs.json.*

import java.net.URL

final case class SdesFile(
                           fileName: String,
                           fileSize: Long,
                           downloadUrl: URL,
                           metadata: Seq[MetadataValue]
                         )

object SdesFile {

  given OFormat[SdesFile] = {

    val reads: Reads[SdesFile] = (
      (__ \ "filename").read[String] and
      (__ \ "fileSize").read[Long] and
      (__ \ "downloadURL").read[URL] and
      (__ \ "metadata").readWithDefault(Seq.empty[MetadataValue])
    )(SdesFile.apply)

    val writes: OWrites[SdesFile] = (
      (__ \ "filename").write[String] and
      (__ \ "fileSize").write[Long] and
      (__ \ "downloadURL").write[URL] and
      (__ \ "metadata").write[Seq[MetadataValue]]
    )(o => Tuple.fromProductTyped(o))

    OFormat(reads, writes)
  }
}

final case class MetadataValue(metadata: String, value: String)

object MetadataValue {

  given OFormat[MetadataValue] = Json.format
}