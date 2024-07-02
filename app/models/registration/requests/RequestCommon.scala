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

import play.api.libs.json.{JsObject, Json, OWrites}

import java.time.Instant

final case class RequestCommon(
                                receiptDate: Instant,
                                acknowledgementReference: String
                              )

object RequestCommon {

  implicit lazy val writes: OWrites[RequestCommon] = new OWrites[RequestCommon]:
    override def writes(o: RequestCommon): JsObject =
      Json.obj(
        "regime" -> "DPRS",
        "receiptDate" -> o.receiptDate,
        "acknowledgementReference" -> o.acknowledgementReference,
        "requestParameters" -> Json.arr(
          Json.obj(
            "paramName" -> "REGIME",
            "paramValue" -> "DPRS"
          )
        )
      )
}

