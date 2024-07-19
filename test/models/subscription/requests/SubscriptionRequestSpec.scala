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

package models.subscription.requests

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

class SubscriptionRequestSpec extends AnyFreeSpec with Matchers {

  "subscription request" - {
    
    val organisationContact = OrganisationContact("name", "email", Some("phone"))
    val individualContact = IndividualContact("first", "last", "email", None)

    "must serialise with optional fields missing" in {

      val request = SubscriptionRequest("123", true, None, organisationContact, None)

      Json.toJson(request) mustEqual Json.obj(
        "idType" -> "SAFE",
        "idNumber" -> "123",
        "gbUser" -> true,
        "primaryContact" -> Json.obj(
          "organisation" -> Json.obj(
            "name" -> "name"
          ),
          "email" -> "email",
          "phone" -> "phone"
        )
      )
    }

    "must serialise with optional fields present" in {

      val request = SubscriptionRequest("123", true, Some("trading"), individualContact, Some(organisationContact))

      Json.toJson(request) mustEqual Json.obj(
        "idType" -> "SAFE",
        "idNumber" -> "123",
        "gbUser" -> true,
        "tradingName" -> "trading",
        "primaryContact" -> Json.obj(
          "individual" -> Json.obj(
            "firstName" -> "first",
            "lastName" -> "last"
          ),
          "email" -> "email"
        ),
        "secondaryContact" -> Json.obj(
          "organisation" -> Json.obj(
            "name" -> "name"
          ),
          "email" -> "email",
          "phone" -> "phone"
        )
      )
    }
  }
}
