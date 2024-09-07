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

package models.operator.responses

import models.operator.NotificationType
import play.api.libs.json.*
import play.api.libs.functional.syntax.*

import java.time.Instant

final case class NotificationDetails(notificationType: NotificationType,
                                     isActiveSeller: Boolean,
                                     isDueDiligence: Boolean,
                                     firstPeriod: String,
                                     receivedDateTime: Instant)

object NotificationDetails {

  lazy val defaultFormat: OFormat[NotificationDetails] = Json.format

  lazy val downstreamReads: Reads[NotificationDetails] = (
    (__ \ "NotificationType").read[NotificationType] and
    (__ \ "IsActiveSeller").read[Boolean] and
    (__ \ "IsDueDiligence").read[Boolean] and
    (__ \ "FirstNotifiedReportingPeriod").read[String] and
    (__ \ "ReceivedDateTime").read[Instant]
  )(NotificationDetails.apply _)
}
