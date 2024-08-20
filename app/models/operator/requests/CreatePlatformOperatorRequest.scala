package models.operator.requests

import models.operator.{AddressDetails, ContactDetails, TinDetails}

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

}
