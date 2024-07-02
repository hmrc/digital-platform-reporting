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

package models.registration.responses

import models.registration.Address
import play.api.libs.functional.syntax.*
import play.api.libs.json.*

final case class ResponseDetailWithId(
                                       safeId: String,
                                       address: Address,
                                       organisationName: Option[String]
                                     )

object ResponseDetailWithId {

  implicit lazy val reads: Reads[ResponseDetailWithId] =
    (
      (__ \ "SAFEID").read[String] and
      (__ \ "address").read[Address] and
      (__ \ "organisation" \ "organisationName").readNullable[String]
    )(ResponseDetailWithId(_, _, _))

  implicit lazy val writes: OWrites[ResponseDetailWithId] = Json.writes
}
