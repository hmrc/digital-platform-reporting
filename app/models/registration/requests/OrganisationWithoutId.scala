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

import models.registration.Address
import play.api.libs.json.*

final case class OrganisationWithoutId(name: String,
                                       address: Address,
                                       contactDetails: ContactDetails) extends RequestDetailWithoutId

object OrganisationWithoutId {

  implicit lazy val reads: Reads[OrganisationWithoutId] = Json.reads

  implicit lazy val writes: OWrites[OrganisationWithoutId] = new OWrites[OrganisationWithoutId] {

    def writes(o: OrganisationWithoutId): JsObject =
      Json.obj(
        "organisation" -> Json.obj(
          "organisationName" -> o.name
        ),
        "address" -> o.address,
        "IsAnAgent" -> false,
        "IsAGroup" -> false,
        "contactDetails" -> o.contactDetails
      )
  }
}
