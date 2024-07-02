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
import play.api.libs.json.{Json, JsObject, OWrites, Reads}

trait RequestDetailWithoutId extends RequestDetail

object RequestDetailWithoutId {

  implicit lazy val reads: Reads[RequestDetailWithoutId] =
    IndividualWithoutId.reads.widen or OrganisationWithoutId.reads.widen
  
  implicit lazy val writes: OWrites[RequestDetailWithoutId] = new OWrites[RequestDetailWithoutId]:
    override def writes(o: RequestDetailWithoutId): JsObject =
      o match {
        case x: IndividualWithoutId   => Json.toJsObject(x)(IndividualWithoutId.writes)
        case x: OrganisationWithoutId => Json.toJsObject(x)(OrganisationWithoutId.writes)
      }
}
