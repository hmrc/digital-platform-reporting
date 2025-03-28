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
import play.api.libs.json.{Json, OWrites}

import java.time.{Instant, Year}

final case class SubmissionSummary(submissionId: String,
                                   fileName: String,
                                   operatorId: Option[String],
                                   operatorName: Option[String],
                                   reportingPeriod: Option[Year],
                                   submissionDateTime: Instant,
                                   submissionStatus: SubmissionStatus,
                                   assumingReporterName: Option[String],
                                   submissionCaseId: Option[String],
                                   isDeleted: Boolean,
                                   localDataExists: Boolean)

object SubmissionSummary {
  
  implicit lazy val writes: OWrites[SubmissionSummary] = Json.writes
  
  def apply(submission: DeliveredSubmission, isDeleted: Boolean, localDataExists: Boolean): SubmissionSummary =
    SubmissionSummary(
      submission.conversationId,
      submission.fileName,
      submission.operatorId,
      submission.operatorName,
      submission.reportingPeriod,
      submission.submissionDateTime,
      submission.submissionStatus,
      submission.assumingReporterName,
      Some(submission.submissionCaseId),
      isDeleted,
      localDataExists
    )
    
  def apply(submission: Submission): Option[SubmissionSummary] =
    submission.state match {
      case state: Submission.State.Submitted =>
        Some(SubmissionSummary(
          submission._id,
          state.fileName,
          Some(submission.operatorId),
          Some(submission.operatorName),
          Some(state.reportingPeriod),
          submission.created,
          SubmissionStatus.Pending,
          submission.assumingOperatorName,
          None,
          isDeleted = false,
          localDataExists = true
        ))

      case _ => None
    }
}