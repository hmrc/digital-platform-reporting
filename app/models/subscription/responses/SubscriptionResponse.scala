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

package models.subscription.responses

import play.api.libs.json.*

final case class SubscriptionResponse(dprsId: String)

object SubscriptionResponse {
  
  implicit lazy val reads: Reads[SubscriptionResponse] =
    (__ \ "success" \ "dprsReference").read[String].map(SubscriptionResponse.apply)
    
  implicit lazy val writes: OWrites[SubscriptionResponse] = Json.writes
}