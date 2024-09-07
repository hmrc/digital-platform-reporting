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

package models.operator.responses

import models.operator.{AddressDetails, ContactDetails, TinDetails}
import play.api.libs.functional.syntax.*
import play.api.libs.json.*

final case class PlatformOperator(operatorId: String,
                                  operatorName: String,
                                  tinDetails: Seq[TinDetails],
                                  businessName: Option[String],
                                  tradingName: Option[String],
                                  primaryContactDetails: ContactDetails,
                                  secondaryContactDetails: Option[ContactDetails],
                                  addressDetails: AddressDetails,
                                  notifications: Seq[NotificationDetails])

object PlatformOperator {
  
  lazy val defaultWrites: OWrites[PlatformOperator] = {
    
    given OWrites[TinDetails] = TinDetails.defaultFormat
    given OWrites[ContactDetails] = ContactDetails.defaultFormat
    given OWrites[AddressDetails] = AddressDetails.defaultFormat
    given OWrites[NotificationDetails] = NotificationDetails.defaultFormat
    
    Json.writes
  }
  
  lazy val downstreamReads: Reads[PlatformOperator] = {

    given Reads[TinDetails] = TinDetails.downstreamReads
    given Reads[ContactDetails] = ContactDetails.downStreamReads
    given Reads[AddressDetails] = AddressDetails.downstreamReads
    given Reads[NotificationDetails] = NotificationDetails.downstreamReads
    
    (
      (__ \ "POID").read[String] and
      (__ \ "POName").read[String] and
      (__ \ "TINDetails").readNullable[Seq[TinDetails]] and
      (__ \ "TradingName").readNullable[String] and
      (__ \ "PrimaryContactDetails").read[ContactDetails] and
      (__ \ "SecondaryContactDetails").readNullable[ContactDetails] and
      (__ \ "AddressDetails").read[AddressDetails] and
      (__ \ "NotificationsList").readNullable[Seq[NotificationDetails]]
    )((poId, operatorName, tinDetails, tradingName, primaryContact, secondaryContact, address, notifications) =>
      PlatformOperator(poId, operatorName, tinDetails.getOrElse(Seq.empty), None, tradingName, primaryContact, secondaryContact, address, notifications.getOrElse(Seq.empty)))
  }
}
