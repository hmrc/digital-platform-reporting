package models.operator

final case class ContactDetails(
                                 phoneNumber: Option[String],
                                 contactName: String,
                                 emailAddress: String
                               )

object ContactDetails {

}