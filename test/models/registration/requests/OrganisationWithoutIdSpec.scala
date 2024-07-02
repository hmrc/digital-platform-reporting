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
import play.api.libs.json.Json

class OrganisationWithoutIdSpec extends AnyFreeSpec with Matchers {
  
  "organisation without Id" - {

    "must serialise" in {

      val address = Address("line 1", Some("line 2"), None, None, Some("postcode"), "GB")
      val organisation = OrganisationWithoutId("name", address)

      val expectedJson = Json.obj(
        "organisation" -> Json.obj(
          "organisationName" -> "name"
        ),
        "address" -> Json.obj(
          "line1" -> "line 1",
          "line2" -> "line 2",
          "postalCode" -> "postcode",
          "countryCode" -> "GB"
        ),
        "isAnAgent" -> false,
        "isAGroup" -> false
      )

      Json.toJson(organisation) mustEqual expectedJson
    }
  }
}
