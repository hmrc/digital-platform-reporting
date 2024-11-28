package support.builders

import models.operator.AddressDetails

object AddressDetailsBuilder {

  val anAddressDetails: AddressDetails = AddressDetails(
    line1 = "default-line-1",
    line2 = None,
    line3 = None,
    line4 = None,
    postCode = None,
    countryCode = None
  )
}
