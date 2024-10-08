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

import models.operator.{AddressDetails, ContactDetails, RequestType, TinDetails}
import play.api.libs.json._
import play.api.libs.functional.syntax._

final case class CreatePlatformOperatorRequest(
                                                subscriptionId: String,
                                                operatorName: String,
                                                tinDetails: Seq[TinDetails],
                                                businessName: Option[String],
                                                tradingName: Option[String],
                                                primaryContactDetails: ContactDetails,
                                                secondaryContactDetails: Option[ContactDetails],
                                                addressDetails: AddressDetails
                                              )

object CreatePlatformOperatorRequest {

  lazy val defaultFormat: OFormat[CreatePlatformOperatorRequest] = {

    given OFormat[TinDetails] = TinDetails.defaultFormat
    given OFormat[ContactDetails] = ContactDetails.defaultFormat
    given OFormat[AddressDetails] = AddressDetails.defaultFormat

    Json.format
  }

  lazy val downstreamWrites: OWrites[CreatePlatformOperatorRequest] = {

    given OWrites[TinDetails] = TinDetails.downstreamWrites
    given OWrites[ContactDetails] = ContactDetails.downstreamWrites
    given OWrites[AddressDetails] = AddressDetails.downstreamWrites

    given OWrites[CreatePlatformOperatorRequest] = (
      (__ \ "SubscriptionID").write[String] and
      (__ \ "POName").write[String] and
      (__ \ "TINDetails").write[Seq[TinDetails]] and
      (__ \ "BusinessName").writeNullable[String] and
      (__ \ "TradingName").writeNullable[String] and
      (__ \ "PrimaryContactDetails").write[ContactDetails] and
      (__ \ "SecondaryContactDetails").writeNullable[ContactDetails] and
      (__ \ "AddressDetails").write[AddressDetails]
    )(o => Tuple.fromProductTyped(o))

    OWrites { request =>
      Json.obj(
        "POManagement" -> Json.obj(
          "RequestCommon" -> Json.obj(
            "OriginatingSystem" -> "MDTP",
            "TransmittingSystem" -> "EIS",
            "RequestType" -> RequestType.Create,
            "Regime" -> "DPI"
          ),
          "RequestDetails" -> request
        )
      )
    }
  }
}
