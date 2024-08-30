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

import java.time.{Instant, LocalDate}

class RequestWithIdSpec extends AnyFreeSpec with Matchers {

  "request with Id" - {

    val instant = Instant.ofEpochSecond(1, 223345000)
    val requestCommon = RequestCommon(instant, "ack ref")

    "must serialise with an Individual with NINO" in {
      val individualWithNino = IndividualWithNino("nino", "first", "last", LocalDate.of(2000, 1, 2))
      val request = RequestWithId(requestCommon, individualWithNino)
      val expectedJson = Json.obj(
        "registerWithIDRequest" -> Json.obj(
          "requestCommon" -> Json.obj(
            "regime" -> "DPRS",
            "receiptDate" -> "1970-01-01T00:00:01Z",
            "acknowledgementReference" -> "ack ref",
            "requestParameters" -> Json.arr(
              Json.obj(
                "paramName" -> "REGIME",
                "paramValue" -> "DPRS"
              )
            )
          ),
          "requestDetail" -> Json.obj(
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
        )
      )

      Json.toJson(request) mustEqual expectedJson
    }

    "must serialise with an Individual with UTR" in {
      val individualWithUtr = IndividualWithUtr("K1234k", None)
      val request = RequestWithId(requestCommon, individualWithUtr)
      val expectedJson = Json.obj(
        "registerWithIDRequest" -> Json.obj(
          "requestCommon" -> Json.obj(
            "regime" -> "DPRS",
            "receiptDate" -> "1970-01-01T00:00:01Z",
            "acknowledgementReference" -> "ack ref",
            "requestParameters" -> Json.arr(
              Json.obj(
                "paramName" -> "REGIME",
                "paramValue" -> "DPRS"
              )
            )
          ),
          "requestDetail" -> Json.obj(
            "IDType" -> "UTR",
            "IDNumber" -> "1234",
            "requiresNameMatch" -> false,
            "isAnAgent" -> false
          )
        )
      )

      Json.toJson(request) mustEqual expectedJson
    }

    "must serialise with an Organisation with UTR" in {
      val organisationWithUtr = OrganisationWithUtr("123456", None)
      val request = RequestWithId(requestCommon, organisationWithUtr)
      val expectedJson = Json.obj(
        "registerWithIDRequest" -> Json.obj(
          "requestCommon" -> Json.obj(
            "regime" -> "DPRS",
            "receiptDate" -> "1970-01-01T00:00:01Z",
            "acknowledgementReference" -> "ack ref",
            "requestParameters" -> Json.arr(
              Json.obj(
                "paramName" -> "REGIME",
                "paramValue" -> "DPRS"
              )
            )
          ),
          "requestDetail" -> Json.obj(
            "IDType" -> "UTR",
            "IDNumber" -> "123456",
            "requiresNameMatch" -> false,
            "isAnAgent" -> false
          )
        )
      )

      Json.toJson(request) mustEqual expectedJson
    }
  }
}
