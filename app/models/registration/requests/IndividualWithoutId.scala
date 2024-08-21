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
import play.api.libs.json.*
import models.registration.Address

final case class IndividualWithoutId(
                                      firstName: String,
                                      lastName: String,
                                      dateOfBirth: LocalDate,
                                      address: Address
                                    ) extends RequestDetailWithoutId

object IndividualWithoutId {

  implicit lazy val reads: Reads[IndividualWithoutId] = Json.reads

  implicit lazy val writes: OWrites[IndividualWithoutId] = new OWrites[IndividualWithoutId] {

    def writes(o: IndividualWithoutId): JsObject =
      Json.obj(
        "individual" -> Json.obj(
          "firstName" -> o.firstName,
          "lastName" -> o.lastName,
          "dateOfBirth" -> o.dateOfBirth
        ),
        "address" -> o.address,
        "IsAnAgent" -> false,
        "IsAGroup" -> false,
        "contactDetails" -> Json.obj()
      )
  }
}
