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

package models.registration.requests

import models.registration.Address
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.*
import java.time.LocalDate

class RequestDetailWithoutIdSpec extends AnyFreeSpec with Matchers {
  
  "request detail without id" - {

    "must read an individual without id" in {

      val json = Json.obj(
        "firstName" -> "first",
        "lastName" -> "last",
        "dateOfBirth" -> "2000-01-02",
        "address" -> Json.obj(
          "line1" -> "line 1",
          "postalCode" -> "postcode",
          "countryCode" -> "GB"
        )
      )

      val result = json.as[RequestDetailWithoutId]
      val expectedAddress = Address("line 1", None, None, None, Some("postcode"), "GB")
      result mustEqual IndividualWithoutId("first", "last", LocalDate.of(2000, 1, 2), expectedAddress)
    }

    "must read an organisation without id" in {

      val json = Json.obj(
        "name" -> "name",
        "address" -> Json.obj(
          "line1" -> "line 1",
          "postalCode" -> "postcode",
          "countryCode" -> "GB"
        )
      )

      val result = json.as[RequestDetailWithoutId]
      val expectedAddress = Address("line 1", None, None, None, Some("postcode"), "GB")
      result mustEqual OrganisationWithoutId("name", expectedAddress)
    }
  }
}
