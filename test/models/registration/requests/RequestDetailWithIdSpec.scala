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

import java.time.LocalDate

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.*

class RequestDetailWithIdSpec extends AnyFreeSpec with Matchers {
  
  "request detail with id" - {

    "must read an individual with UTR" in {

      val json = Json.obj(
        "type" -> "individual",
        "utr" -> "123"
      )

      json.as[RequestDetailWithId] mustEqual IndividualWithUtr("123", None)
    }

    "must read an organisation with UTR" in {

      val json = Json.obj(
        "type" -> "organisation",
        "utr" -> "123"
      )

      json.as[RequestDetailWithId] mustEqual OrganisationWithUtr("123", None)
    }

    "must read an individual with NINO" in {

      val json = Json.obj(
        "nino" -> "123",
        "firstName" -> "first",
        "lastName" -> "last",
        "dateOfBirth" -> "2000-01-02"
      )

      json.as[RequestDetailWithId] mustEqual IndividualWithNino("123", "first", "last", LocalDate.of(2000, 1, 2))
    }
  }
}
