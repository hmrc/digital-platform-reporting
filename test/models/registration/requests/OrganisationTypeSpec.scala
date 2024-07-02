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

import models.registration.requests.OrganisationType.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{Json, JsString}

class OrganisationTypeSpec extends AnyFreeSpec with Matchers {
  
  "organisation type" - {

    "must deserialise from json" - {

      "for a limited company" in {
        JsString("limitedCompany").as[OrganisationType] mustEqual LimitedCompany
      }

      "for a partnership" in {
        JsString("partnership").as[OrganisationType] mustEqual Partnership
      }

      "for a LLP" in {
        JsString("llp").as[OrganisationType] mustEqual Llp
      }

      "for an association or trust" in {
        JsString("associationOrTrust").as[OrganisationType] mustEqual AssociationOrTrust
      }

      "for a sole trader" in {
        JsString("soleTrader").as[OrganisationType] mustEqual SoleTrader
      }

      "for an individual" in {
        JsString("individual").as[OrganisationType] mustEqual Individual
      }
    }

    "must serialise to json" - {

      "for a limited company" in {
        Json.toJson[OrganisationType](LimitedCompany) mustEqual JsString("0003")
      }

      "for a partnership" in {
        Json.toJson[OrganisationType](Partnership) mustEqual JsString("0001")
      }

      "for a LLP" in {
        Json.toJson[OrganisationType](Llp) mustEqual JsString("0002")
      }

      "for an association or trust" in {
        Json.toJson[OrganisationType](AssociationOrTrust) mustEqual JsString("0004")
      }

      "for a sole trader" in {
        Json.toJson[OrganisationType](SoleTrader) mustEqual JsString("0000")
      }

      "for an individual" in {
        Json.toJson[OrganisationType](Individual) mustEqual JsString("0000")
      }
    }
  }
}
