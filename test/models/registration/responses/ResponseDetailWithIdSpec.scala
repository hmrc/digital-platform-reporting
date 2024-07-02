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

package models.registration.responses

import models.registration.Address
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

class ResponseDetailWithIdSpec extends AnyFreeSpec with Matchers {
  
  "response detail with id" - {

    "must deserialise without an organisation name" in {

      val json = Json.obj(
        "SAFEID" -> "123",
        "address" -> Json.obj(
          "line1" -> "line 1",
          "postalCode" -> "postcode",
          "countryCode" -> "GB"
        )
      )

      val expectedResult = ResponseDetailWithId("123", Address("line 1", None, None, None, Some("postcode"), "GB"), None)
      json.as[ResponseDetailWithId] mustEqual expectedResult
    }

    "must deserialise with an organisation name" in {

      val json = Json.obj(
        "SAFEID" -> "123",
        "organisation" -> Json.obj(
          "organisationName" -> "name"
        ),
        "address" -> Json.obj(
          "line1" -> "line 1",
          "postalCode" -> "postcode",
          "countryCode" -> "GB"
        )
      )

      val expectedResult = ResponseDetailWithId("123", Address("line 1", None, None, None, Some("postcode"), "GB"), Some("name"))
      json.as[ResponseDetailWithId] mustEqual expectedResult
    }
  }
}
