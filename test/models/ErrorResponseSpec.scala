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

package models

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.{JsString, Json}

class ErrorResponseSpec extends AnyFreeSpec with Matchers {

  "error detail" - {
    "must deserialise" in {
      val json = Json.obj(
        "errorDetail" -> Json.obj(
          "errorCode" -> ErrorResponse.HasActiveSubscriptionCode,
          "errorMessage" -> "Business partner already has active subscription for this regime",
          "source" -> "ETMP",
          "sourceFaultDetail" -> Json.obj(
            "detail" -> Json.arr(JsString("Duplicate Submission"))
          ),
          "timestamp" -> "2023-08-31T13:00:21.655Z",
          "correlationId" -> "d60de98c-f499-47f5-b2d6-e80966e8d19e"
        )
      )
      
      json.as[ErrorResponse] mustEqual ErrorResponse(ErrorDetail(ErrorResponse.HasActiveSubscriptionCode))
    }
  }
}
