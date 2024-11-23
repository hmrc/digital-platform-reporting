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

import models.submission.Submission.State.*
import models.submission.Submission.UploadFailureReason.{NotXml, PlatformOperatorIdMissing, ReportingPeriodInvalid, SchemaValidationError}
import models.submission.Submission.{SubmissionType, UploadFailureReason}
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.http.StringContextOps

import java.time.{Instant, Year}

class SubmissionSummarySpec extends AnyFreeSpec with Matchers with OptionValues {

  private val now = Instant.now
  
  "apply(DeliveredSubmission)" - {
    
    "must return a SubmissionSummary with the correct values" in {
      
      val deliveredSubmission = DeliveredSubmission(
        conversationId = "conversationId",
        fileName = "filename",
        operatorId = "operatorId",
        operatorName = "operatorName",
        reportingPeriod = Year.of(2024),
        submissionCaseId = "submissionCaseId",
        submissionDateTime = now,
        submissionStatus = SubmissionStatus.Pending,
        assumingReporterName = Some("assumingReporter")
      )
      
      SubmissionSummary(deliveredSubmission, true, false) mustEqual SubmissionSummary(
        submissionId = "conversationId",
        fileName = "filename",
        operatorId = "operatorId",
        operatorName = "operatorName",
        reportingPeriod = Year.of(2024),
        submissionDateTime = now,
        submissionStatus = SubmissionStatus.Pending,
        assumingReporterName = Some("assumingReporter"),
        submissionCaseId = Some("submissionCaseId"),
        isDeleted = true,
        localDataExists = false
      )
    }
  }
  
  "apply(Submission)" - {
    
    "must return a SubmissionSummary with the correct values when the submission state is Submitted" in {

      val submission = Submission(
        _id = "id",
        submissionType = SubmissionType.Xml,
        dprsId = "dprsId",
        operatorId = "operatorId",
        operatorName = "operatorName",
        assumingOperatorName = Some("assumingReporter"),
        state = Submitted("filename", Year.of(2024)),
        created = now,
        updated = now
      )
      
      SubmissionSummary(submission).value  mustEqual SubmissionSummary(
        submissionId = "id",
        fileName = "filename",
        operatorId = "operatorId",
        operatorName = "operatorName",
        reportingPeriod = Year.of(2024),
        submissionDateTime = now,
        submissionStatus = SubmissionStatus.Pending,
        assumingReporterName = Some("assumingReporter"),
        submissionCaseId = None,
        isDeleted = false,
        localDataExists = true
      )
    }
    
    "must return None when the submission state is anything other than Submitted" in {

      val readyGen: Gen[Ready.type] = Gen.const(Ready)
      val uploadingGen: Gen[Uploading.type] = Gen.const(Uploading)
      val uploadFailureReasonGen: Gen[UploadFailureReason] = Gen.oneOf(NotXml, SchemaValidationError(Seq.empty), PlatformOperatorIdMissing, ReportingPeriodInvalid)
      val uploadFailedGen: Gen[UploadFailed] = uploadFailureReasonGen.map(reason => UploadFailed(reason, None))
      val validatedGen: Gen[Validated] = Gen.const(Validated(url"http://example.com", Year.of(2024), "test.xml", "checksum", 1337L))
      val approvedGen: Gen[Approved] = Gen.const(Approved("test.xml", Year.of(2024)))
      val rejectedGen: Gen[Rejected] = Gen.const(Rejected("test.xml", Year.of(2024)))
      
      val nonSubmittedStateGen: Gen[Submission.State] = Gen.oneOf(readyGen, uploadingGen, uploadFailedGen, validatedGen, approvedGen, rejectedGen)
      
      val submission = Submission(
        _id = "id",
        submissionType = SubmissionType.Xml,
        dprsId = "dprsId",
        operatorId = "operatorId",
        operatorName = "operatorName",
        assumingOperatorName = None,
        state = nonSubmittedStateGen.sample.value,
        created = now,
        updated = now
      )
      
      SubmissionSummary(submission) must not be defined
    }
  }
}
