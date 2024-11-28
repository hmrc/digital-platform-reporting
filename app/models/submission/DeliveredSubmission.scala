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

package models.submission

import models.yearFormat
import play.api.libs.functional.syntax.*
import play.api.libs.json.*

import java.time.{Instant, Year}

final case class DeliveredSubmission(conversationId: String,
                                     fileName: String,
                                     operatorId: String,
                                     operatorName: String,
                                     reportingPeriod: Year,
                                     submissionCaseId: String,
                                     submissionDateTime: Instant,
                                     submissionStatus: SubmissionStatus,
                                     assumingReporterName: Option[String])

object DeliveredSubmission {
  
  implicit lazy val writes: OWrites[DeliveredSubmission] = Json.writes
  
  implicit lazy val reads: Reads[DeliveredSubmission] = (
    (__ \ "conversationId").read[String] and
    (__ \ "fileName").read[String] and
    (__ \ "pOId").read[String] and
    (__ \ "pOName").read[String] and
    (__ \ "reportingYear").read[Year] and
    (__ \ "submissionCaseId").read[String] and
    (__ \ "submissionDateTime").read[Instant] and
    (__ \ "submissionStatus").read[SubmissionStatus] and
    (__ \ "assumingReporterName").readNullable[String]
  )(DeliveredSubmission.apply)
}
