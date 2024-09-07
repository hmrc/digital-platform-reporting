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

package models.operator

import play.api.libs.json._
import play.api.libs.functional.syntax._

final case class AddressDetails(
                                 line1: String,
                                 line2: Option[String],
                                 line3: Option[String],
                                 line4: Option[String],
                                 postCode: Option[String],
                                 countryCode: Option[String]
                               )

object AddressDetails {

  lazy val defaultFormat: OFormat[AddressDetails] = Json.format
  
  lazy val downstreamWrites: OWrites[AddressDetails] = (
    (__ \ "AddressLine1").write[String] and
    (__ \ "AddressLine2").writeNullable[String] and
    (__ \ "AddressLine3").writeNullable[String] and
    (__ \ "AddressLine4").writeNullable[String] and
    (__ \ "PostalCode").writeNullable[String] and
    (__ \ "CountryCode").writeNullable[String]
  )(o => Tuple.fromProductTyped(o))
  
  lazy val downstreamReads: Reads[AddressDetails] = (
    (__ \ "AddressLine1").read[String] and
    (__ \ "AddressLine2").readNullable[String] and
    (__ \ "AddressLine3").readNullable[String] and
    (__ \ "AddressLine4").readNullable[String] and
    (__ \ "PostalCode").readNullable[String] and
    (__ \ "CountryCode").readNullable[String]
  )(AddressDetails.apply _)
}
