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

import models.subscription.Contact
import play.api.libs.functional.syntax.*
import play.api.libs.json.*

final case class SubscriptionRequest(id: String,
                                     gbUser: Boolean,
                                     tradingName: Option[String],
                                     primaryContact: Contact,
                                     secondaryContact: Option[Contact])

object SubscriptionRequest {

  implicit lazy val reads: Reads[SubscriptionRequest] = Json.reads

  implicit lazy val writes: OWrites[SubscriptionRequest] =
    (
      (__ \ "idType").write[String] and
      (__ \ "idNumber").write[String] and
      (__ \ "gbUser").write[Boolean] and
      (__ \ "tradingName").writeNullable[String] and
      (__ \ "primaryContact").write[Contact] and
      (__ \ "secondaryContact").writeNullable[Contact]
    )(sr => ("SAFE", sr.id, sr.gbUser, sr.tradingName, sr.primaryContact, sr.secondaryContact))
}
