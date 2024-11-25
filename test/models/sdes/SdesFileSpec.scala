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

package models.sdes

import models.sdes.list.{MetadataValue, SdesFile}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json
import uk.gov.hmrc.http.StringContextOps

class SdesFileSpec extends AnyFreeSpec with Matchers {

  "must read from json" - {

    "when there is metadata" in {

      val json = Json.obj(
        "filename" -> "test.xml",
        "fileSize" -> 1337,
        "downloadURL" -> "http://example.com/test.xml",
        "metadata" -> Json.arr(
          Json.obj("metadata" -> "key", "value" -> "value")
        )
      )

      val file = SdesFile(
        fileName = "test.xml",
        fileSize = 1337,
        downloadUrl = url"http://example.com/test.xml",
        metadata = Seq(
          MetadataValue(
            "key", "value"
          )
        )
      )

      json.as[SdesFile] mustEqual file
    }

    "when there is no metadata" in {

      val json = Json.obj(
        "filename" -> "test.xml",
        "fileSize" -> 1337,
        "downloadURL" -> "http://example.com/test.xml"
      )

      val file = SdesFile(
        fileName = "test.xml",
        fileSize = 1337,
        downloadUrl = url"http://example.com/test.xml",
        metadata = Seq.empty
      )

      json.as[SdesFile] mustEqual file
    }
  }

  "must write from json" in {

    val json = Json.obj(
      "filename" -> "test.xml",
      "fileSize" -> 1337,
      "downloadURL" -> "http://example.com/test.xml",
      "metadata" -> Json.arr(
        Json.obj("metadata" -> "key", "value" -> "value")
      )
    )

    val file = SdesFile(
      fileName = "test.xml",
      fileSize = 1337,
      downloadUrl = url"http://example.com/test.xml",
      metadata = Seq(
        MetadataValue(
          "key", "value"
        )
      )
    )

    Json.toJson(file) mustEqual json
  }
}
