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

package models.operator

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

class AddressDetailsSpec extends AnyFreeSpec with Matchers {

  ".downstreamWrites" - {

    "must write to the correct json" in {

      val model = AddressDetails(
        line1 = "line1",
        line2 = Some("line2"),
        line3 = Some("line3"),
        line4 = Some("line4"),
        postCode = Some("postCode"),
        countryCode = Some("countryCode")
      )

      val expectedJson = Json.obj(
        "AddressLine1" -> "line1",
        "AddressLine2" -> "line2",
        "AddressLine3" -> "line3",
        "AddressLine4" -> "line4",
        "PostalCode" -> "postCode",
        "CountryCode" -> "countryCode"
      )

      Json.toJsObject(model)(AddressDetails.downstreamWrites) mustEqual expectedJson
    }
  }
}
