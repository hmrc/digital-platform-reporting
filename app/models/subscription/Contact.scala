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

package models.subscription

import play.api.libs.functional.syntax.*
import play.api.libs.json.*

sealed trait Contact

object Contact {
  
  implicit lazy val reads: Reads[Contact] =
    IndividualContact.format.widen or OrganisationContact.format.widen
    
  implicit lazy val writes: OWrites[Contact] = new OWrites[Contact] {
    override def writes(o: Contact): JsObject = o match {
      case ic: IndividualContact => Json.toJsObject(ic)(IndividualContact.format)
      case oc: OrganisationContact => Json.toJsObject(oc)(OrganisationContact.format)
    }
  }
}

final case class IndividualContact(individual: Individual,
                                   email: String,
                                   phone: Option[String]) extends Contact

object IndividualContact {
  implicit lazy val format: OFormat[IndividualContact] = Json.format
}

final case class Individual(firstName: String, lastName: String)

object Individual {
  implicit lazy val format: OFormat[Individual] = Json.format
}

final case class OrganisationContact(organisation: Organisation,
                                     email: String,
                                     phone: Option[String]) extends Contact

object OrganisationContact {
  implicit lazy val format: OFormat[OrganisationContact] = Json.format
}

final case class Organisation(name: String)

object Organisation {
  implicit lazy val format: OFormat[Organisation] = Json.format
}
