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

package models.registration.requests

import play.api.libs.functional.syntax.*
import play.api.libs.json.*

final case class IndividualWithUtr(
                                    utr: String,
                                    details: Option[IndividualWithUtrDetails]
                                  ) extends RequestDetailWithId

object IndividualWithUtr {

  implicit lazy val reads: Reads[IndividualWithUtr] =
    (__ \ "type")
      .read[String]
      .flatMap[String] { t =>
        if (t == "individual") {
          Reads(_ => JsSuccess(t))
        } else {
          Reads(_ => JsError("type must equal `individual`"))
        }
      }
      .andKeep(
        (
          (__ \ "utr").read[String] and
          (__ \ "details").readNullable[IndividualWithUtrDetails]
        )(IndividualWithUtr(_, _))
      )
  
  implicit lazy val writes: OWrites[IndividualWithUtr] = new OWrites[IndividualWithUtr]:
    override def writes(o: IndividualWithUtr): JsObject = {

      val detailsJson = o.details.map { details =>
        Json.obj("individual" -> Json.toJson(details))
      }.getOrElse(Json.obj())

      Json.obj(
        "IDType" -> "UTR",
        "IDNumber" -> o.utr,
        "requiresNameMatch" -> o.details.nonEmpty,
        "isAnAgent" -> false
      ) ++ detailsJson
    }
}

final case class IndividualWithUtrDetails(firstName: String, lastName: String)

object IndividualWithUtrDetails {
  
  implicit lazy val format: OFormat[IndividualWithUtrDetails] = Json.format
}
