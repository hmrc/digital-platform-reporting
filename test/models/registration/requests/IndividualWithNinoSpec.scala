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
import play.api.libs.json.Json

import java.time.LocalDate

class IndividualWithNinoSpec extends AnyFreeSpec with Matchers {

  "individual with NINO" - {
    
    "must serialise" in {
      
      val individual = IndividualWithNino("nino", "first", "last", LocalDate.of(2000, 1, 2))

      val expectedJson = Json.obj(
        "IDType" -> "NINO",
        "IDNumber" -> "nino",
        "requiresNameMatch" -> true,
        "isAnAgent" -> false,
        "individual" -> Json.obj(
          "firstName" -> "first",
          "lastName" -> "last",
          "dateOfBirth" -> "2000-01-02"
        )
      )

      Json.toJson(individual) mustEqual expectedJson
    }
  }
}
