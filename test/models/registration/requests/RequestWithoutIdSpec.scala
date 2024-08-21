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

import java.time.{Instant, LocalDate}
import models.registration.Address
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

class RequestWithoutIdSpec extends AnyFreeSpec with Matchers {

  "request without Id" - {
    
    val instant = Instant.ofEpochSecond(1)
    val requestCommon = RequestCommon(instant, "ack ref")
    val address = Address("line 1", None, None, None, Some("postcode"), "GB")

    "must serialise with an Individual" in {

      val individual = IndividualWithoutId("first", "last", LocalDate.of(2000, 1, 2), address)

      val request = RequestWithoutId(requestCommon, individual)

      val expectedJson = Json.obj(
        "registerWithoutIDRequest" -> Json.obj(
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
            "IsAnAgent" -> false,
            "IsAGroup" -> false,
            "contactDetails" -> Json.obj(),
            "individual" -> Json.obj(
              "firstName" -> "first",
              "lastName" -> "last",
              "dateOfBirth" -> "2000-01-02"
            ),
            "address" -> Json.obj(
              "addressLine1" -> "line 1",
              "postalCode" -> "postcode",
              "countryCode" -> "GB"
            )
          )
        )
      )

      Json.toJson(request) mustEqual expectedJson
    }

    "must serialise with an Organisation" in {

      val organisation = OrganisationWithoutId("name", address)

      val request = RequestWithoutId(requestCommon, organisation)

      val expectedJson = Json.obj(
        "registerWithoutIDRequest" -> Json.obj(
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
            "IsAnAgent" -> false,
            "IsAGroup" -> false,
            "contactDetails" -> Json.obj(),
            "organisation" -> Json.obj(
              "organisationName" -> "name"
            ),
            "address" -> Json.obj(
              "addressLine1" -> "line 1",
              "postalCode" -> "postcode",
              "countryCode" -> "GB"
            )
          )
        )
      )

      Json.toJson(request) mustEqual expectedJson
    }
  }
}
