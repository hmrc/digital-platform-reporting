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

package models.subscription.responses

import models.subscription.{Individual, IndividualContact, Organisation, OrganisationContact}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

class SubscriptionInfoSpec extends AnyFreeSpec with Matchers {

  "Subscription Info" - {

    "must deserialise for a read when optional are missing" in {
      val json = Json.obj(
        "success" -> Json.obj(
          "customer" -> Json.obj(
            "id" -> "dprsId",
            "gbUser" -> true,
            "primaryContact" -> Json.obj(
              "individual" -> Json.obj(
                "firstName" -> "first",
                "lastName" -> "last"
              ),
              "email" -> "individual email",
              "phone" -> "0777777"
            )
          )
        )
      )

      val individualContact = IndividualContact(Individual("first", "last"), "individual email", Some("0777777"))

      json.as[SubscriptionInfo] mustEqual  SubscriptionInfo(
        id = "dprsId",
        gbUser = true,
        tradingName = None,
        primaryContact = individualContact,
        secondaryContact = None
      )
    }

    "must deserialise for a read when optional are present" in {
      val json = Json.obj(
        "success" -> Json.obj(
          "customer" -> Json.obj(
            "id" -> "dprsId",
            "gbUser" -> true,
            "tradingName" -> "tradingName",
            "primaryContact" -> Json.obj(
              "individual" -> Json.obj(
                "firstName" -> "first",
                "lastName" -> "last"
              ),
              "email" -> "individual email",
              "phone" -> "0777777"
            ),
            "secondaryContact" -> Json.obj(
              "organisation" -> Json.obj(
                "name" -> "org name"
              ),
              "email" -> "org email",
              "phone" -> "0787777"
            )
          )
        )
      )

      val individualContact = IndividualContact(Individual("first", "last"), "individual email", Some("0777777"))
      val organisationContact = OrganisationContact(Organisation("org name"), "org email", Some("0787777"))

      json.as[SubscriptionInfo] mustEqual  SubscriptionInfo(
        id = "dprsId",
        gbUser = true,
        tradingName = Some("tradingName"),
        primaryContact = individualContact,
        secondaryContact = Some(organisationContact)
      )
    }

    "must return the correctly formatted primary contact name" - {

      val individualContact = IndividualContact(Individual("first", "last"), "individual email", Some("0777777"))
      val organisationContact = OrganisationContact(Organisation("org name"), "org email", Some("0787777"))

      "when individual contact" in {
        val subscriptionInfo = SubscriptionInfo(id = "dprsId",
          gbUser = true,
          tradingName = None,
          primaryContact = individualContact,
          secondaryContact = None
        )
        subscriptionInfo.primaryContactName mustEqual "first last"
      }

      "when organistation contact" in {
        val subscriptionInfo = SubscriptionInfo(id = "dprsId",
          gbUser = true,
          tradingName = None,
          primaryContact = organisationContact,
          secondaryContact = None
        )
        subscriptionInfo.primaryContactName mustEqual "org name"
      }

    }

  }
}
