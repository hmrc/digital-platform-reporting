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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.*

class OrganisationWithUtrSpec extends AnyFreeSpec with Matchers {

  "organisation with UTR" - {
    
    "must serialise with no organisation details" in {
      
      val organisation = OrganisationWithUtr("123", None)
      
      val expectedJson = Json.obj(
        "IDType" -> "UTR",
        "IDNumber" -> "123",
        "requiresNameMatch" -> false,
        "isAnAgent" -> false
      )
      
      Json.toJson(organisation) mustEqual expectedJson
    }
    
    "must serialise with organisation details" in {
      
      val organisation = OrganisationWithUtr("123", Some(OrganisationDetails("name", OrganisationType.LimitedCompany)))

      val expectedJson = Json.obj(
        "IDType" -> "UTR",
        "IDNumber" -> "123",
        "requiresNameMatch" -> true,
        "isAnAgent" -> false,
        "organisation" -> Json.obj(
          "organisationName" -> "name",
          "organisationType" -> "0003"
        )
      )

      Json.toJson(organisation) mustEqual expectedJson
    }

    "must read with no organisation details" in {

      val json = Json.obj(
        "type" -> "organisation",
        "utr" -> "123"
      )

      val result = json.as[OrganisationWithUtr]
      result mustEqual OrganisationWithUtr("123", None)
    }

    "must read with organisation details" in {

      val json = Json.obj(
        "type" -> "organisation",
        "utr" -> "123",
        "details" -> Json.obj(
          "name" -> "name",
          "organisationType" -> "limitedCompany"
        )
      )

      val result = json.as[OrganisationWithUtr]
      result mustEqual OrganisationWithUtr("123", Some(OrganisationDetails("name", OrganisationType.LimitedCompany)))
    }

    "must not read when type is not `organisation`" in {

      val json = Json.obj(
        "type" -> "individual",
        "utr" -> "123"
      )

      json.validate[OrganisationWithUtr] mustBe a[JsError]
    }
  }
}
