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

import play.api.libs.json.*

sealed trait OrganisationType

object OrganisationType {

  case object LimitedCompany extends OrganisationType
  case object Partnership extends OrganisationType
  case object Llp extends OrganisationType
  case object AssociationOrTrust extends OrganisationType
  case object SoleTrader extends OrganisationType
  case object Individual extends OrganisationType

  implicit lazy val reads: Reads[OrganisationType] = Reads[OrganisationType] {
    case JsString("limitedCompany")     => JsSuccess(LimitedCompany)
    case JsString("partnership")        => JsSuccess(Partnership)
    case JsString("llp")                => JsSuccess(Llp)
    case JsString("associationOrTrust") => JsSuccess(AssociationOrTrust)
    case JsString("soleTrader")         => JsSuccess(SoleTrader)
    case JsString("individual")         => JsSuccess(Individual)
    case _                              => JsError("Unable to read organisation type")
  }

  implicit lazy val writes: Writes[OrganisationType] = new Writes[OrganisationType] {
    
    override def writes(o: OrganisationType): JsValue =
      o match {
        case LimitedCompany     => JsString("0003")
        case Partnership        => JsString("0001")
        case Llp                => JsString("0002")
        case AssociationOrTrust => JsString("0004")
        case _                  => JsString("0000")
      }
  }
}
