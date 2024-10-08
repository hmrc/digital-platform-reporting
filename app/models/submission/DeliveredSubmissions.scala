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

import play.api.libs.functional.syntax.*
import play.api.libs.json.*

final case class DeliveredSubmissions(submissions: Seq[DeliveredSubmission],
                                     resultsCount: Int)

object DeliveredSubmissions {

  implicit lazy val writes: OWrites[DeliveredSubmissions] = Json.writes
  
  implicit lazy val reads: Reads[DeliveredSubmissions] = (
    (__ \ "submissionsListResponse" \ "responseDetails" \ "submissionsList").read[Seq[DeliveredSubmission]] and
    (__ \ "submissionsListResponse" \ "responseCommon" \ "resultsCount").read[Int]
  )(DeliveredSubmissions.apply)
}
