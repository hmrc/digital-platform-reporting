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

package models.recentsubmissions

import models.AuthenticatedRequest
import models.recentsubmissions.requests.RecentSubmissionRequest
import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class RecentSubmissionDetails(userId: String,
                                         operatorDetails: Map[String, OperatorSubmissionDetails],
                                         yourContactDetailsCorrect: Option[Boolean],
                                         created: Instant)

object RecentSubmissionDetails extends MongoJavatimeFormats.Implicits {

  given mongoFormat: OFormat[RecentSubmissionDetails] = OFormat(reads, writes)

  implicit lazy val reads: Reads[RecentSubmissionDetails] = (
    (__ \ "userId").read[String] and
      (__ \ "operatorDetails").read[Map[String, OperatorSubmissionDetails]] and
      (__ \ "yourContactDetailsCorrect").readNullable[Boolean] and
      (__ \ "created").read[Instant]
    )(RecentSubmissionDetails.apply)

  implicit lazy val writes: OWrites[RecentSubmissionDetails] = (
    (__ \ "userId").write[String] and
      (__ \ "operatorDetails").write[Map[String, OperatorSubmissionDetails]] and
      (__ \ "yourContactDetailsCorrect").writeNullable[Boolean] and
      (__ \ "created").write[Instant]
    )(o => Tuple.fromProductTyped(o))
}