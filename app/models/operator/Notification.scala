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

package models.operator

import play.api.libs.json._
import play.api.libs.functional.syntax._

final case class Notification(notificationType: NotificationType,
                              isActiveSeller: Boolean,
                              isDueDiligence: Boolean,
                              firstPeriod: String)

object Notification {
  
  lazy val defaultFormat: OFormat[Notification] = Json.format
  
  lazy val downstreamWrites: OWrites[Notification] = (
    (__ \ "NotificationType").write[NotificationType] and
    (__ \ "IsActiveSeller").write[Boolean] and
    (__ \ "IsDueDiligence").write[Boolean] and
    (__ \ "FirstNotifiedReportingPeriod").write[String]
  )(o => Tuple.fromProductTyped(o))
}