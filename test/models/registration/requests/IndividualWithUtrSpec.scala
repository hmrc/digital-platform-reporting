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

class IndividualWithUtrSpec extends AnyFreeSpec with Matchers {

  "individual with UTR" - {

    "must serialise with no individual details" in {

      val individual = IndividualWithUtr("123", None)

      val expectedJson = Json.obj(
        "IDType" -> "UTR",
        "IDNumber" -> "123",
        "requiresNameMatch" -> false,
        "isAnAgent" -> false
      )

      Json.toJson(individual) mustEqual expectedJson
    }

    "must serialise with individual details" in {

      val individual = IndividualWithUtr("123", Some(IndividualWithUtrDetails("first", "last")))

      val expectedJson = Json.obj(
        "IDType" -> "UTR",
        "IDNumber" -> "123",
        "requiresNameMatch" -> true,
        "isAnAgent" -> false,
        "individual" -> Json.obj(
          "firstName" -> "first",
          "lastName" -> "last"
        )
      )

      Json.toJson(individual) mustEqual expectedJson
    }

    "must read with no individual details" in {

      val json = Json.obj(
        "type" -> "individual",
        "utr" -> "123"
      )

      val result = json.as[IndividualWithUtr]
      result mustEqual IndividualWithUtr("123", None)
    }
    
    "must read with individual details" in {

      val json = Json.obj(
        "type" -> "individual",
        "utr" -> "123",
        "details" -> Json.obj(
          "firstName" -> "first",
          "lastName" -> "last"
        )
      )

      val result = json.as[IndividualWithUtr]
      result mustEqual IndividualWithUtr("123", Some(IndividualWithUtrDetails("first", "last")))
    }

    "must not read when type is not `individual`" in {

      val json = Json.obj(
        "type" -> "organisation",
        "utr" -> "123"
      )

      json.validate[IndividualWithUtr] mustBe a[JsError]
    }
    
  }
}
