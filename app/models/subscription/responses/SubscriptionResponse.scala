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

sealed trait SubscriptionResponse

final case class SubscribedResponse(dprsId: String) extends SubscriptionResponse

object SubscribedResponse {
  
  implicit lazy val reads: Reads[SubscribedResponse] =
    (__ \ "success" \ "dprsReference").read[String].map(SubscribedResponse.apply)
    
  implicit lazy val writes: OWrites[SubscribedResponse] = Json.writes
}

case object AlreadySubscribedResponse extends SubscriptionResponse

final case class UnexpectedResponse(errorCode: String) extends SubscriptionResponse

object UnexpectedResponse {
  implicit lazy val format: OFormat[UnexpectedResponse] = Json.format[UnexpectedResponse]
}