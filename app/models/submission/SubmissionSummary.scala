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

import play.api.libs.json.{Json, OWrites}

import java.time.Instant

final case class SubmissionSummary(submissionId: String,
                                   fileName: String,
                                   operatorId: String,
                                   operatorName: String,
                                   reportingPeriod: String,
                                   submissionDateTime: Instant,
                                   submissionStatus: SubmissionStatus,
                                   assumingReporterName: Option[String],
                                   submissionCaseId: Option[String])

object SubmissionSummary {
  
  implicit lazy val writes: OWrites[SubmissionSummary] = Json.writes
  
  def apply(submission: DeliveredSubmission): SubmissionSummary =
    SubmissionSummary(
      submission.conversationId,
      submission.fileName,
      submission.operatorId,
      submission.operatorName,
      submission.reportingPeriod,
      submission.submissionDateTime,
      submission.submissionStatus,
      submission.assumingReporterName,
      Some(submission.submissionCaseId)
    )
    
  def apply(submission: Submission): Option[SubmissionSummary] =
    submission.state match {
      case state: Submission.State.Submitted =>
        Some(SubmissionSummary(
          submission._id,
          state.fileName,
          submission.operatorId,
          submission.operatorName,
          state.reportingPeriod.toString,
          submission.updated,
          SubmissionStatus.Pending,
          submission.assumingOperatorName,
          None
        ))

      case _ => None
    }
}