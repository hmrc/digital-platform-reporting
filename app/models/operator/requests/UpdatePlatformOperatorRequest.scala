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

package models.operator.requests

import models.operator.{AddressDetails, ContactDetails, Notification, RequestType, TinDetails}
import play.api.libs.json.*
import play.api.libs.functional.syntax.*

final case class UpdatePlatformOperatorRequest(
                                                subscriptionId: String,
                                                operatorId: String,
                                                operatorName: String,
                                                tinDetails: Seq[TinDetails],
                                                businessName: Option[String],
                                                tradingName: Option[String],
                                                primaryContactDetails: ContactDetails,
                                                secondaryContactDetails: Option[ContactDetails],
                                                addressDetails: AddressDetails,
                                                notification: Option[Notification]
                                              )

object UpdatePlatformOperatorRequest {

  lazy val defaultFormat: OFormat[UpdatePlatformOperatorRequest] = {

    given OFormat[TinDetails] = TinDetails.defaultFormat
    given OFormat[ContactDetails] = ContactDetails.defaultFormat
    given OFormat[AddressDetails] = AddressDetails.defaultFormat
    given OFormat[Notification] = Notification.defaultFormat

    Json.format
  }
  
  lazy val downstreamWrites: OWrites[UpdatePlatformOperatorRequest] = {

    given OWrites[TinDetails] = TinDetails.downstreamWrites
    given OWrites[ContactDetails] = ContactDetails.downstreamWrites
    given OWrites[AddressDetails] = AddressDetails.downstreamWrites
    given OWrites[Notification] = Notification.downstreamWrites

    given OWrites[UpdatePlatformOperatorRequest] = (
      (__ \ "SubscriptionID").write[String] and
      (__ \ "POID").write[String] and
      (__ \ "POName").write[String] and
      (__ \ "TINDetails").write[Seq[TinDetails]] and
      (__ \ "BusinessName").writeNullable[String] and
      (__ \ "TradingName").writeNullable[String] and
      (__ \ "PrimaryContactDetails").write[ContactDetails] and
      (__ \ "SecondaryContactDetails").writeNullable[ContactDetails] and
      (__ \ "AddressDetails").write[AddressDetails] and
      (__ \ "NotificationDetails").writeNullable[Notification]
    )(o => Tuple.fromProductTyped(o))

    OWrites { request =>
      Json.obj(
        "POManagement" -> Json.obj(
          "RequestCommon" -> Json.obj(
            "OriginatingSystem" -> "MDTP",
            "TransmittingSystem" -> "EIS",
            "RequestType" -> RequestType.Update,
            "Regime" -> "DPI"
          ),
          "RequestDetails" -> request
        )
      )
    }
  }
}