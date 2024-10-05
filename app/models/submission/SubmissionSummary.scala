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

import java.time.Instant
import play.api.libs.json.{Json, OWrites}

final case class SubmissionSummary(submissionId: String,
                                   fileName: String,
                                   operatorId: String,
                                   operatorName: String,
                                   reportingPeriod: String,
                                   submissionDateTime: Instant,
                                   status: DeliveredSubmissionStatus)

object SubmissionSummary {
  
  implicit lazy val writes: OWrites[SubmissionSummary] = Json.writes
  
  def apply(deliveredSubmission: DeliveredSubmission): SubmissionSummary =
    SubmissionSummary(
      submissionId = deliveredSubmission.conversationId,
      fileName = deliveredSubmission.fileName,
      operatorId = deliveredSubmission.operatorId,
      operatorName = deliveredSubmission.operatorName,
      reportingPeriod = deliveredSubmission.reportingPeriod,
      submissionDateTime = deliveredSubmission.submissionDateTime,
      status = deliveredSubmission.submissionStatus
    )
    
  def apply(submission: Submission, fileName: String, reportingPeriod: String): SubmissionSummary =
    SubmissionSummary(
      submissionId = submission._id,
      fileName = fileName,
      operatorId = submission.operatorId,
      operatorName = submission.operatorName,
      reportingPeriod = reportingPeriod,
      submissionDateTime = submission.created,
      status = DeliveredSubmissionStatus.Pending
    )
}
