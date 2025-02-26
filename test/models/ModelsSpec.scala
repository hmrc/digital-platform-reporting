/*
 * Copyright 2025 HM Revenue & Customs
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

package models

import play.api.libs.json.*
import support.SpecBase

import java.net.{URI, URL}
import java.time.Year

class ModelsSpec extends SpecBase {

  "urlFormat" - {
    "when reading from JSON" - {
      "must successfully parse a valid URL string" in {
        val json = JsString("http://example.com")

        val result = Json.fromJson[URL](json)

        result.isSuccess mustBe true
        result.get mustBe new URI("http://example.com").toURL
      }

      "must fail with 'error.expected.url' for an invalid URL string" in {
        val json = JsString("not-a-url")

        val result = Json.fromJson[URL](json)

        result.isError mustBe true
        result.asInstanceOf[JsError].errors.head._2.head.message mustBe "error.expected.url"
      }

      "must fail when JSON is not a string" in {
        val json = JsNumber(123)

        val result = Json.fromJson[URL](json)

        result.isError mustBe true
        result.asInstanceOf[JsError].errors.head._2.head.message mustBe "error.expected.jsstring"
      }
    }

    "when writing to JSON" - {
      "must convert a URL to its string representation" in {
        val url = new URI("https://example.com").toURL

        Json.toJson(url) mustBe JsString("https://example.com")
      }
    }
  }

  "yearFormat" - {
    "when reading from JSON" - {
      "must successfully parse a valid year as a number" in {
        val json = JsNumber(2024)

        val result = Json.fromJson[Year](json)

        result.isSuccess mustBe true
        result.get mustBe Year.of(2024)
      }

      "must successfully parse a valid year as a string" in {
        val json = JsString("2024")

        val result = Json.fromJson[Year](json)

        result.isSuccess mustBe true
        result.get mustBe Year.of(2024)
      }

      "must fail with 'error.invalid' for an invalid year number" in {
        val json = JsNumber(1999999999) // Outside Year range (-999,999,999 to 999,999,999, but toInt limits it)

        val result = Json.fromJson[Year](json)

        result.isError mustBe true
        result.asInstanceOf[JsError].errors.head._2.head.message mustBe "error.invalid"
      }

      "must fail with 'error.invalid' for a non-numeric string" in {
        val json = JsString("not-a-year")

        val result = Json.fromJson[Year](json)

        result.isError mustBe true
        result.asInstanceOf[JsError].errors.head._2.head.message mustBe "error.invalid"
      }

      "must fail with 'error.invalid' for a non-string/non-number JSON value" in {
        val json = JsBoolean(true)

        val result = Json.fromJson[Year](json)

        result.isError mustBe true
        result.asInstanceOf[JsError].errors.head._2.head.message mustBe "error.invalid"
      }
    }

    "when writing to JSON" - {
      "must convert a Year to its integer value" in {
        val year = Year.of(2024)

        Json.toJson(year) mustBe JsNumber(2024)
      }
    }
  }
}
