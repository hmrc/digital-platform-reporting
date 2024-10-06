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

package models.assumed

import models.operator.TinDetails
import play.api.libs.json.{Json, OFormat}

final case class AssumingPlatformOperator(
                                           name: String,
                                           residentCountry: String,
                                           tinDetails: Seq[TinDetails],
                                           address: AssumingOperatorAddress
                                         )

object AssumingPlatformOperator {

  given OFormat[AssumingPlatformOperator] = {
    given OFormat[TinDetails] = TinDetails.defaultFormat
    Json.format
  }
}

final case class AssumingOperatorAddress(
                                          line1: String,
                                          line2: Option[String],
                                          city: String,
                                          region: Option[String],
                                          postCode: String,
                                          country: String
                                        )

object AssumingOperatorAddress {

  given OFormat[AssumingOperatorAddress] = Json.format
}