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

import models.subscription.{Contact, IndividualContact, OrganisationContact}
import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

final case class SubscriptionInfo(id: String,
                                  gbUser: Boolean,
                                  tradingName: Option[String],
                                  primaryContact: Contact,
                                  secondaryContact: Option[Contact]) {

  lazy val primaryContactName: String = primaryContact match {
    case ic: IndividualContact => s"${ic.individual.firstName} ${ic.individual.lastName}"
    case oc: OrganisationContact => oc.organisation.name
  }
}

object SubscriptionInfo {

  lazy val mongoFormat: OFormat[SubscriptionInfo] = {
    import MongoJavatimeFormats.Implicits.*
    Json.format
  }
  
  given writes: OWrites[SubscriptionInfo] = Json.writes
  
  given reads: Reads[SubscriptionInfo] =
    (
      (__ \ "success" \ "customer" \ "id").read[String] and
      (__ \ "success" \ "customer" \ "gbUser").read[Boolean] and
      (__ \ "success" \ "customer" \ "tradingName").readNullable[String] and
      (__ \ "success" \ "customer" \ "primaryContact").read[Contact] and
      (__ \ "success" \ "customer" \ "secondaryContact").readNullable[Contact]
    )(SubscriptionInfo.apply)
}
