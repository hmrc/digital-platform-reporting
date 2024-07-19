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

import play.api.libs.functional.syntax.*
import play.api.libs.json.*

sealed trait Contact

object Contact {
  
  implicit lazy val reads: Reads[Contact] =
    IndividualContact.reads.widen or OrganisationContact.reads.widen
    
  implicit lazy val writes: OWrites[Contact] = new OWrites[Contact] {
    override def writes(o: Contact): JsObject =o match {
      case ic: IndividualContact => Json.toJsObject(ic)(IndividualContact.writes)
      case oc: OrganisationContact => Json.toJsObject(oc)(OrganisationContact.writes)
    }
  }
}

final case class IndividualContact(firstName: String,
                                   lastName: String,
                                   email: String,
                                   phone: Option[String]) extends Contact

object IndividualContact {
  
  implicit lazy val reads: Reads[IndividualContact] = Json.reads
  
  implicit lazy val writes: OWrites[IndividualContact] =
    (
      (__ \ "individual" \ "firstName").write[String] and
      (__ \ "individual" \ "lastName").write[String] and
      (__ \ "email").write[String] and
      (__ \ "phone").writeNullable[String]
    )(c => Tuple.fromProductTyped(c))
}

final case class OrganisationContact(name: String,
                                     email: String,
                                     phone: Option[String]) extends Contact

object OrganisationContact {
  
  implicit lazy val reads: Reads[OrganisationContact] = Json.reads
  
  implicit lazy val writes: OWrites[OrganisationContact] =
    (
      (__ \ "organisation" \ "name").write[String] and
      (__ \ "email").write[String] and
      (__ \ "phone").writeNullable[String] 
    )(c => Tuple.fromProductTyped(c))
}
