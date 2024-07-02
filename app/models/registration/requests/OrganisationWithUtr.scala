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

final case class OrganisationWithUtr(
                                      utr: String,
                                      details: Option[OrganisationDetails]
                                    ) extends RequestDetailWithId

object OrganisationWithUtr {

  implicit lazy val reads: Reads[OrganisationWithUtr] =
      (__ \ "type")
        .read[String]
        .flatMap[String] { t =>
          if (t == "organisation") {
            Reads(_ => JsSuccess(t))
          } else {
            Reads(_ => JsError("type must equal `organisation`"))
          }
        }
        .andKeep(
          (
            (__ \ "utr").read[String] and
            (__ \ "details").readNullable[OrganisationDetails]
          )(OrganisationWithUtr(_, _))
        )
  
  implicit lazy val writes: OWrites[OrganisationWithUtr] = new OWrites[OrganisationWithUtr]:
    override def writes(o: OrganisationWithUtr): JsObject = {
      
      val detailsJson = o.details.map { details =>
        Json.obj("organisation" -> Json.toJson(details))
      }.getOrElse(Json.obj())
      
      Json.obj(
        "IDType" -> "UTR",
        "IDNumber" -> o.utr,
        "requiresNameMatch" -> o.details.nonEmpty,
        "isAnAgent" -> false
      ) ++ detailsJson
    }
}

final case class OrganisationDetails(name: String, organisationType: OrganisationType)

object OrganisationDetails {

  implicit lazy val reads: Reads[OrganisationDetails] = Json.reads

  implicit lazy val writes: OWrites[OrganisationDetails] = new OWrites[OrganisationDetails]:
    override def writes(o: OrganisationDetails): JsObject =
      Json.obj(
        "organisationName" -> o.name,
        "organisationType" -> Json.toJson(o.organisationType)
      )
}
