package models.operator

final case class TinDetails(
                             tin: String,
                             tinType: TinType,
                             issuedBy: String
                           )

object TinDetails {

}