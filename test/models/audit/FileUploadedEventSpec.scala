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

package models.audit

import models.audit.FileUploadedEvent.FileUploadOutcome
import models.submission.Submission.UploadFailureReason.*
import models.submission.UpscanFailureReason.{Duplicate, Quarantine, Rejected, Unknown}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

class FileUploadedEventSpec extends AnyFreeSpec with Matchers {

  "must write to correct json structure" - {

    "when the outcome is Approved" in {

      val expectedJson = Json.obj(
        "platformOperatorId" -> "poId",
        "digitalPlatformReportingId" -> "dprsId",
        "platformOperator" -> "po",
        "conversationId" -> "submissionId",
        "fileName" -> "test.xml",
        "outcome" -> Json.obj(
          "status" -> "Accepted"
        )
      )

      val event = FileUploadedEvent(
        conversationId = "submissionId",
        dprsId = "dprsId",
        operatorId = "poId",
        operatorName = "po",
        fileName = Some("test.xml"),
        outcome = FileUploadOutcome.Accepted
      )

      Json.toJson(event) mustEqual expectedJson
    }

    "when the outcome is Rejected" - {

      "when the error is NotXml" in {

        val expectedJson = Json.obj(
          "platformOperatorId" -> "poId",
          "digitalPlatformReportingId" -> "dprsId",
          "platformOperator" -> "po",
          "conversationId" -> "submissionId",
          "fileName" -> "test.xml",
          "outcome" -> Json.obj(
            "status" -> "Rejected",
            "fileErrorCode" -> "NotXml",
            "fileErrorReason" -> "NotXml"
          )
        )

        val event = FileUploadedEvent(
          conversationId = "submissionId",
          dprsId = "dprsId",
          operatorId = "poId",
          operatorName = "po",
          fileName = Some("test.xml"),
          outcome = FileUploadOutcome.Rejected(NotXml)
        )

        Json.toJson(event) mustEqual expectedJson
      }

      "when the error is SchemaValidationError" in {

        val expectedJson = Json.obj(
          "platformOperatorId" -> "poId",
          "digitalPlatformReportingId" -> "dprsId",
          "platformOperator" -> "po",
          "conversationId" -> "submissionId",
          "fileName" -> "test.xml",
          "outcome" -> Json.obj(
            "status" -> "Rejected",
            "fileErrorCode" -> "SchemaValidationError",
            "fileErrorReason" -> "SchemaValidationError"
          )
        )

        val event = FileUploadedEvent(
          conversationId = "submissionId",
          dprsId = "dprsId",
          operatorId = "poId",
          operatorName = "po",
          fileName = Some("test.xml"),
          outcome = FileUploadOutcome.Rejected(SchemaValidationError(Seq.empty))
        )

        Json.toJson(event) mustEqual expectedJson
      }

      "when the error is ManualAssumedReportExists" in {

        val expectedJson = Json.obj(
          "platformOperatorId" -> "poId",
          "digitalPlatformReportingId" -> "dprsId",
          "platformOperator" -> "po",
          "conversationId" -> "submissionId",
          "fileName" -> "test.xml",
          "outcome" -> Json.obj(
            "status" -> "Rejected",
            "fileErrorCode" -> "ManualAssumedReportExists",
            "fileErrorReason" -> "ManualAssumedReportExists"
          )
        )

        val event = FileUploadedEvent(
          conversationId = "submissionId",
          dprsId = "dprsId",
          operatorId = "poId",
          operatorName = "po",
          fileName = Some("test.xml"),
          outcome = FileUploadOutcome.Rejected(ManualAssumedReportExists)
        )

        Json.toJson(event) mustEqual expectedJson
      }

      "when the error is PlatformOperatorIdMissing" in {

        val expectedJson = Json.obj(
          "platformOperatorId" -> "poId",
          "digitalPlatformReportingId" -> "dprsId",
          "platformOperator" -> "po",
          "conversationId" -> "submissionId",
          "fileName" -> "test.xml",
          "outcome" -> Json.obj(
            "status" -> "Rejected",
            "fileErrorCode" -> "PlatformOperatorIdMissing",
            "fileErrorReason" -> "PlatformOperatorIdMissing"
          )
        )

        val event = FileUploadedEvent(
          conversationId = "submissionId",
          dprsId = "dprsId",
          operatorId = "poId",
          operatorName = "po",
          fileName = Some("test.xml"),
          outcome = FileUploadOutcome.Rejected(PlatformOperatorIdMissing)
        )

        Json.toJson(event) mustEqual expectedJson
      }

      "when the error is PlatformOperatorIdMismatch" in {

        val expectedJson = Json.obj(
          "platformOperatorId" -> "poId",
          "digitalPlatformReportingId" -> "dprsId",
          "platformOperator" -> "po",
          "conversationId" -> "submissionId",
          "fileName" -> "test.xml",
          "outcome" -> Json.obj(
            "status" -> "Rejected",
            "fileErrorCode" -> "PlatformOperatorIdMismatch",
            "fileErrorReason" -> "PlatformOperatorIdMismatch"
          )
        )

        val event = FileUploadedEvent(
          conversationId = "submissionId",
          dprsId = "dprsId",
          operatorId = "poId",
          operatorName = "po",
          fileName = Some("test.xml"),
          outcome = FileUploadOutcome.Rejected(PlatformOperatorIdMismatch("id", "id2"))
        )

        Json.toJson(event) mustEqual expectedJson
      }

      "when the error is ReportingPeriodInvalid" in {

        val expectedJson = Json.obj(
          "platformOperatorId" -> "poId",
          "digitalPlatformReportingId" -> "dprsId",
          "platformOperator" -> "po",
          "conversationId" -> "submissionId",
          "fileName" -> "test.xml",
          "outcome" -> Json.obj(
            "status" -> "Rejected",
            "fileErrorCode" -> "ReportingPeriodInvalid",
            "fileErrorReason" -> "ReportingPeriodInvalid"
          )
        )

        val event = FileUploadedEvent(
          conversationId = "submissionId",
          dprsId = "dprsId",
          operatorId = "poId",
          operatorName = "po",
          fileName = Some("test.xml"),
          outcome = FileUploadOutcome.Rejected(ReportingPeriodInvalid)
        )

        Json.toJson(event) mustEqual expectedJson
      }

      "when the error is UpscanError" - {

        "when the reason is Quarantine" in {

          val expectedJson = Json.obj(
            "platformOperatorId" -> "poId",
            "digitalPlatformReportingId" -> "dprsId",
            "platformOperator" -> "po",
            "conversationId" -> "submissionId",
            "fileName" -> "test.xml",
            "outcome" -> Json.obj(
              "status" -> "Rejected",
              "fileErrorCode" -> "UpscanError",
              "fileErrorReason" -> "Quarantine"
            )
          )

          val event = FileUploadedEvent(
            conversationId = "submissionId",
            dprsId = "dprsId",
            operatorId = "poId",
            operatorName = "po",
            fileName = Some("test.xml"),
            outcome = FileUploadOutcome.Rejected(UpscanError(Quarantine))
          )

          Json.toJson(event) mustEqual expectedJson
        }

        "when the reason is Rejected" in {

          val expectedJson = Json.obj(
            "platformOperatorId" -> "poId",
            "digitalPlatformReportingId" -> "dprsId",
            "platformOperator" -> "po",
            "conversationId" -> "submissionId",
            "fileName" -> "test.xml",
            "outcome" -> Json.obj(
              "status" -> "Rejected",
              "fileErrorCode" -> "UpscanError",
              "fileErrorReason" -> "Rejected"
            )
          )

          val event = FileUploadedEvent(
            conversationId = "submissionId",
            dprsId = "dprsId",
            operatorId = "poId",
            operatorName = "po",
            fileName = Some("test.xml"),
            outcome = FileUploadOutcome.Rejected(UpscanError(Rejected))
          )

          Json.toJson(event) mustEqual expectedJson
        }

        "when the reason is Unknown" in {

          val expectedJson = Json.obj(
            "platformOperatorId" -> "poId",
            "digitalPlatformReportingId" -> "dprsId",
            "platformOperator" -> "po",
            "conversationId" -> "submissionId",
            "fileName" -> "test.xml",
            "outcome" -> Json.obj(
              "status" -> "Rejected",
              "fileErrorCode" -> "UpscanError",
              "fileErrorReason" -> "Unknown"
            )
          )

          val event = FileUploadedEvent(
            conversationId = "submissionId",
            dprsId = "dprsId",
            operatorId = "poId",
            operatorName = "po",
            fileName = Some("test.xml"),
            outcome = FileUploadOutcome.Rejected(UpscanError(Unknown))
          )

          Json.toJson(event) mustEqual expectedJson
        }

        "when the reason is Duplicate" in {

          val expectedJson = Json.obj(
            "platformOperatorId" -> "poId",
            "digitalPlatformReportingId" -> "dprsId",
            "platformOperator" -> "po",
            "conversationId" -> "submissionId",
            "fileName" -> "test.xml",
            "outcome" -> Json.obj(
              "status" -> "Rejected",
              "fileErrorCode" -> "UpscanError",
              "fileErrorReason" -> "Duplicate"
            )
          )

          val event = FileUploadedEvent(
            conversationId = "submissionId",
            dprsId = "dprsId",
            operatorId = "poId",
            operatorName = "po",
            fileName = Some("test.xml"),
            outcome = FileUploadOutcome.Rejected(UpscanError(Duplicate))
          )

          Json.toJson(event) mustEqual expectedJson
        }
      }

      "when the error is EntityTooLarge" in {

        val expectedJson = Json.obj(
          "platformOperatorId" -> "poId",
          "digitalPlatformReportingId" -> "dprsId",
          "platformOperator" -> "po",
          "conversationId" -> "submissionId",
          "fileName" -> "test.xml",
          "outcome" -> Json.obj(
            "status" -> "Rejected",
            "fileErrorCode" -> "EntityTooLarge",
            "fileErrorReason" -> "EntityTooLarge"
          )
        )

        val event = FileUploadedEvent(
          conversationId = "submissionId",
          dprsId = "dprsId",
          operatorId = "poId",
          operatorName = "po",
          fileName = Some("test.xml"),
          outcome = FileUploadOutcome.Rejected(EntityTooLarge)
        )

        Json.toJson(event) mustEqual expectedJson
      }

      "when the error is EntityTooSmall" in {

        val expectedJson = Json.obj(
          "platformOperatorId" -> "poId",
          "digitalPlatformReportingId" -> "dprsId",
          "platformOperator" -> "po",
          "conversationId" -> "submissionId",
          "fileName" -> "test.xml",
          "outcome" -> Json.obj(
            "status" -> "Rejected",
            "fileErrorCode" -> "EntityTooSmall",
            "fileErrorReason" -> "EntityTooSmall"
          )
        )

        val event = FileUploadedEvent(
          conversationId = "submissionId",
          dprsId = "dprsId",
          operatorId = "poId",
          operatorName = "po",
          fileName = Some("test.xml"),
          outcome = FileUploadOutcome.Rejected(EntityTooSmall)
        )

        Json.toJson(event) mustEqual expectedJson
      }

      "when the error is InvalidArgument" in {

        val expectedJson = Json.obj(
          "platformOperatorId" -> "poId",
          "digitalPlatformReportingId" -> "dprsId",
          "platformOperator" -> "po",
          "conversationId" -> "submissionId",
          "fileName" -> "test.xml",
          "outcome" -> Json.obj(
            "status" -> "Rejected",
            "fileErrorCode" -> "InvalidArgument",
            "fileErrorReason" -> "InvalidArgument"
          )
        )

        val event = FileUploadedEvent(
          conversationId = "submissionId",
          dprsId = "dprsId",
          operatorId = "poId",
          operatorName = "po",
          fileName = Some("test.xml"),
          outcome = FileUploadOutcome.Rejected(InvalidArgument)
        )

        Json.toJson(event) mustEqual expectedJson
      }

      "when the error is UnknownFailure" in {

        val expectedJson = Json.obj(
          "platformOperatorId" -> "poId",
          "digitalPlatformReportingId" -> "dprsId",
          "platformOperator" -> "po",
          "conversationId" -> "submissionId",
          "fileName" -> "test.xml",
          "outcome" -> Json.obj(
            "status" -> "Rejected",
            "fileErrorCode" -> "UnknownFailure",
            "fileErrorReason" -> "UnknownFailure"
          )
        )

        val event = FileUploadedEvent(
          conversationId = "submissionId",
          dprsId = "dprsId",
          operatorId = "poId",
          operatorName = "po",
          fileName = Some("test.xml"),
          outcome = FileUploadOutcome.Rejected(UnknownFailure)
        )

        Json.toJson(event) mustEqual expectedJson
      }
    }
  }
}
