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
import play.api.libs.json.{Json, JsSuccess}

class ContactSpec extends AnyFreeSpec with Matchers {

  "contact" - {

    "must serialise an individual contact with optional fields missing" in {

      val contact: Contact = IndividualContact("first", "last", "email", None)

      val json = Json.toJson(contact)

      json mustEqual Json.obj(
        "individual" -> Json.obj(
          "firstName" -> "first",
          "lastName" -> "last"
        ),
        "email" -> "email"
      )
    }

    "must serialise contact with optional fields present" in {

      val contact: Contact = IndividualContact("first", "last", "email", Some("phone"))

      val json = Json.toJson(contact)

      json mustEqual Json.obj(
        "individual" -> Json.obj(
          "firstName" -> "first",
          "lastName" -> "last"
        ),
        "email" -> "email",
        "phone" -> "phone"
      )
    }

    "must read an individual" in {

      val json = Json.obj(
        "firstName" -> "first",
        "lastName" -> "last",
        "email" -> "email"
      )

      json.validate[Contact] mustEqual JsSuccess(IndividualContact("first", "last", "email", None))
    }

    "must serialise an organisation contact with optional fields missing" in {

      val contact: Contact = OrganisationContact("name", "email", None)

      val json = Json.toJson(contact)

      json mustEqual Json.obj(
        "organisation" -> Json.obj(
          "name" -> "name"
        ),
        "email" -> "email"
      )
    }

    "must serialise an organisation contact with optional fields present" in {

      val contact: Contact = OrganisationContact("name", "email", Some("phone"))

      val json = Json.toJson(contact)

      json mustEqual Json.obj(
        "organisation" -> Json.obj(
          "name" -> "name"
        ),
        "email" -> "email",
        "phone" -> "phone"
      )
    }

    "must read an organisation" in {

      val json = Json.obj(
        "name" -> "name",
        "email" -> "email"
      )

      json.validate[Contact] mustEqual JsSuccess(OrganisationContact("name", "email", None))
    }
  }
}
