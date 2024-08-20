package models.operator

final case class AddressDetails(
                                 line1: String,
                                 line2: Option[String],
                                 line3: Option[String],
                                 line4: Option[String],
                                 postCode: Option[String],
                                 countryCode: Option[String]
                               )

object AddressDetails {

}
