package support.builders.operator

import models.operator.{AddressDetails, ContactDetails, TinDetails}

object ContactDetailsBuilder {

  val aContactDetails: ContactDetails = ContactDetails(
    phoneNumber = None,
    contactName = "default-contact-name",
    emailAddress = "default.email@example.com"
  )
}
