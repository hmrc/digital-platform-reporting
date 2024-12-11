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
import models.submission.Submission.{SubmissionType, UploadFailureReason}
import models.submission.Submission.UploadFailureReason.*
import models.submission.UpscanFailureReason.Quarantine
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json
import uk.gov.hmrc.http.StringContextOps

import java.time.{Instant, Year}
import java.time.temporal.ChronoUnit

class SubmissionSpec extends AnyFreeSpec with Matchers {

  private val created = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  private val updated = created.plus(1, ChronoUnit.DAYS)

  "when state is Ready" - {

    val submission = Submission(
      _id = "id",
      submissionType = SubmissionType.Xml,
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
      assumingOperatorName = None,
      state = Ready,
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "submissionType" -> "Xml",
      "dprsId" -> "dprsId",
      "operatorId" -> "operatorId",
      "operatorName" -> "operatorName",
      "state" -> Json.obj(
        "type" -> "Ready"
      ),
      "created" -> created,
      "updated" -> updated
    )

    "must read from json" in {
      Json.fromJson[Submission](json).get mustEqual submission
    }

    "must write to json" in {
      Json.toJsObject(submission) mustEqual json
    }
  }

  "when state is Uploading" - {

    val submission = Submission(
      _id = "id",
      submissionType = SubmissionType.ManualAssumedReport,
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
      assumingOperatorName = None,
      state = Uploading,
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "submissionType" -> "ManualAssumedReport",
      "dprsId" -> "dprsId",
      "operatorId" -> "operatorId",
      "operatorName" -> "operatorName",
      "state" -> Json.obj(
        "type" -> "Uploading"
      ),
      "created" -> created,
      "updated" -> updated
    )

    "must read from json" in {
      Json.fromJson[Submission](json).get mustEqual submission
    }

    "must write to json" in {
      Json.toJsObject(submission) mustEqual json
    }
  }

  "when state is UploadFailed" - {

    val submission = Submission(
      _id = "id",
      submissionType = SubmissionType.Xml,
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
      assumingOperatorName = None,
      state = UploadFailed(SchemaValidationError(Seq.empty, false), Some("some-file-name")),
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "submissionType" -> "Xml",
      "dprsId" -> "dprsId",
      "operatorId" -> "operatorId",
      "operatorName" -> "operatorName",
      "state" -> Json.obj(
        "type" -> "UploadFailed",
        "reason" -> Json.obj(
          "type" -> "SchemaValidationError",
          "errors" -> Json.arr(),
          "moreErrors" -> false
        ),
        "fileName" -> "some-file-name"
      ),
      "created" -> created,
      "updated" -> updated
    )

    "must read from json" in {
      Json.fromJson[Submission](json).get mustEqual submission
    }

    "must write to json" in {
      Json.toJsObject(submission) mustEqual json
    }
  }

  "when state is Validated" - {

    val submission = Submission(
      _id = "id",
      submissionType = SubmissionType.Xml,
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
      assumingOperatorName = None,
      state = Validated(
        downloadUrl = url"http://example.com",
        reportingPeriod = Year.of(2024),
        fileName = "test.xml",
        checksum = "checksum",
        size = 1337
      ),
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "submissionType" -> "Xml",
      "dprsId" -> "dprsId",
      "operatorId" -> "operatorId",
      "operatorName" -> "operatorName",
      "state" -> Json.obj(
        "type" -> "Validated",
        "downloadUrl" -> "http://example.com",
        "reportingPeriod" -> 2024,
        "fileName" -> "test.xml",
        "checksum" -> "checksum",
        "size" -> 1337
      ),
      "created" -> created,
      "updated" -> updated
    )

    "must read from json" in {
      Json.fromJson[Submission](json).get mustEqual submission
    }

    "must write to json" in {
      Json.toJsObject(submission) mustEqual json
    }
  }

  "when state is Submitted" - {

    val submission = Submission(
      _id = "id",
      submissionType = SubmissionType.Xml,
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
      assumingOperatorName = None,
      state = Submitted(
        fileName = "test.xml",
        reportingPeriod = Year.of(2024)
      ),
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "submissionType" -> "Xml",
      "dprsId" -> "dprsId",
      "operatorId" -> "operatorId",
      "operatorName" -> "operatorName",
      "state" -> Json.obj(
        "type" -> "Submitted",
        "fileName" -> "test.xml",
        "reportingPeriod" -> 2024
      ),
      "created" -> created,
      "updated" -> updated
    )

    "must read from json" in {
      Json.fromJson[Submission](json).get mustEqual submission
    }

    "must write to json" in {
      Json.toJsObject(submission) mustEqual json
    }
  }

  "when state is Approved" - {

    val submission = Submission(
      _id = "id",
      submissionType = SubmissionType.Xml,
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
      assumingOperatorName = None,
      state = Approved(
        fileName = "test.xml",
        reportingPeriod = Year.of(2024)
      ),
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "submissionType" -> "Xml",
      "dprsId" -> "dprsId",
      "operatorId" -> "operatorId",
      "operatorName" -> "operatorName",
      "state" -> Json.obj(
        "type" -> "Approved",
        "fileName" -> "test.xml",
        "reportingPeriod" -> 2024
      ),
      "created" -> created,
      "updated" -> updated
    )

    "must read from json" in {
      Json.fromJson[Submission](json).get mustEqual submission
    }

    "must write to json" in {
      Json.toJsObject(submission) mustEqual json
    }
  }

  "when state is Rejected" - {

    val submission = Submission(
      _id = "id",
      submissionType = SubmissionType.Xml,
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
      assumingOperatorName = None,
      state = Rejected(
        fileName = "test.xml",
        reportingPeriod = Year.of(2024)
      ),
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "submissionType" -> "Xml",
      "dprsId" -> "dprsId",
      "operatorId" -> "operatorId",
      "operatorName" -> "operatorName",
      "state" -> Json.obj(
        "type" -> "Rejected",
        "fileName" -> "test.xml",
        "reportingPeriod" -> 2024
      ),
      "created" -> created,
      "updated" -> updated
    )

    "must read from json" in {
      Json.fromJson[Submission](json).get mustEqual submission
    }

    "must write to json" in {
      Json.toJsObject(submission) mustEqual json
    }
  }

  "when there is an assuming operator name" - {

    val submission = Submission(
      _id = "id",
      submissionType = SubmissionType.ManualAssumedReport,
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
      assumingOperatorName = Some("assumingOperator"),
      state = Submitted(
        fileName = "test.xml",
        reportingPeriod = Year.of(2024)
      ),
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "submissionType" -> "ManualAssumedReport",
      "dprsId" -> "dprsId",
      "operatorId" -> "operatorId",
      "operatorName" -> "operatorName",
      "assumingOperatorName" -> "assumingOperator",
      "state" -> Json.obj(
        "type" -> "Submitted",
        "fileName" -> "test.xml",
        "reportingPeriod" -> 2024
      ),
      "created" -> created,
      "updated" -> updated
    )

    "must read from json" in {
      Json.fromJson[Submission](json).get mustEqual submission
    }

    "must write to json" in {
      Json.toJsObject(submission) mustEqual json
    }
  }

  "when the submission type is invalid" - {

    val json = Json.obj(
      "_id" -> "id",
      "submissionType" -> "invalid",
      "dprsId" -> "dprsId",
      "operatorId" -> "operatorId",
      "operatorName" -> "operatorName",
      "assumingOperatorName" -> "assumingOperator",
      "state" -> Json.obj(
        "type" -> "Submitted",
        "fileName" -> "test.xml",
        "reportingPeriod" -> 2024
      ),
      "created" -> created,
      "updated" -> updated
    )

    "must fail to read from json" in {
      Json.fromJson[Submission](json).isError mustBe true
    }
  }

  "when there is no submission type" - {

    "when there is no assuming operator name" - {

      val submission = Submission(
        _id = "id",
        submissionType = SubmissionType.Xml,
        dprsId = "dprsId",
        operatorId = "operatorId",
        operatorName = "operatorName",
        assumingOperatorName = None,
        state = Ready,
        created = created,
        updated = updated
      )

      val json = Json.obj(
        "_id" -> "id",
        "dprsId" -> "dprsId",
        "operatorId" -> "operatorId",
        "operatorName" -> "operatorName",
        "state" -> Json.obj(
          "type" -> "Ready"
        ),
        "created" -> created,
        "updated" -> updated
      )

      "must read from json and set submissionType to Xml" in {
        Json.fromJson[Submission](json).get mustEqual submission
      }
    }

    "when there is an assuming operator name" - {

      val submission = Submission(
        _id = "id",
        submissionType = SubmissionType.ManualAssumedReport,
        dprsId = "dprsId",
        operatorId = "operatorId",
        operatorName = "operatorName",
        assumingOperatorName = Some("assumingOperatorName"),
        state = Ready,
        created = created,
        updated = updated
      )

      val json = Json.obj(
        "_id" -> "id",
        "dprsId" -> "dprsId",
        "operatorId" -> "operatorId",
        "operatorName" -> "operatorName",
        "assumingOperatorName" -> "assumingOperatorName",
        "state" -> Json.obj(
          "type" -> "Ready"
        ),
        "created" -> created,
        "updated" -> updated
      )

      "must read from json and set submissionType to ManualAssumedReport" in {
        Json.fromJson[Submission](json).get mustEqual submission
      }
    }
  }

  "upload failure reason" - {

    "when the reason is NotXml" - {

      val json = Json.obj(
        "type" -> "NotXml"
      )

      "must read from json" in {
        Json.fromJson[UploadFailureReason](json).get mustEqual NotXml
      }

      "must write to json" in {
        Json.toJsObject[UploadFailureReason](NotXml) mustEqual json
      }
    }

    "when the reason is SchemaValidationError" - {

      "when there are is no errors element" - {

        val json = Json.obj(
          "type" -> "SchemaValidationError"
        )

        "must read from json" in {
          Json.fromJson[UploadFailureReason](json).get mustEqual SchemaValidationError(Seq.empty, false)
        }
      }

      "when there is an errors element" - {

        val json = Json.obj(
          "type" -> "SchemaValidationError",
          "errors" -> Json.arr(
            Json.obj("line" -> 1, "col" -> 2, "message" -> "message")
          ),
          "moreErrors" -> true
        )

        val model = SchemaValidationError(Seq(SchemaValidationError.Error(1, 2, "message")), true)

        "must read from json" in {
          Json.fromJson[UploadFailureReason](json).get mustEqual model
        }

        "must write to json" in {
          Json.toJsObject[UploadFailureReason](model) mustEqual json
        }
      }

      "must write to json" in {

        val json = Json.obj(
          "type" -> "SchemaValidationError",
          "errors" -> Json.arr(),
          "moreErrors" -> false
        )

        Json.toJsObject[UploadFailureReason](SchemaValidationError(Seq.empty, false)) mustEqual json
      }
    }

    "when the reason is ManualAssumedReportExists" - {

      val json = Json.obj(
        "type" -> "ManualAssumedReportExists"
      )

      "must read from json" in {
        Json.fromJson[UploadFailureReason](json).get mustEqual ManualAssumedReportExists
      }

      "must write to json" in {
        Json.toJsObject[UploadFailureReason](ManualAssumedReportExists) mustEqual json
      }
    }

    "when the reason is PlatformOperatorIdMissing" - {

      val json = Json.obj(
        "type" -> "PlatformOperatorIdMissing"
      )

      "must read from json" in {
        Json.fromJson[UploadFailureReason](json).get mustEqual PlatformOperatorIdMissing
      }

      "must write to json" in {
        Json.toJsObject[UploadFailureReason](PlatformOperatorIdMissing) mustEqual json
      }
    }

    "when the reason is PlatformOperatorIdMismatch" - {

      val failureReason = PlatformOperatorIdMismatch("expected", "actual")
      val json = Json.obj(
        "type" -> "PlatformOperatorIdMismatch",
        "expectedId" -> "expected",
        "actualId" -> "actual"
      )

      "must read from json" in {
        Json.fromJson[UploadFailureReason](json).get mustEqual failureReason
      }

      "must write to json" in {
        Json.toJsObject[UploadFailureReason](failureReason) mustEqual json
      }
    }

    "when the reason is ReportingPeriodInvalid" - {

      val json = Json.obj(
        "type" -> "ReportingPeriodInvalid"
      )

      "must read from json" in {
        Json.fromJson[UploadFailureReason](json).get mustEqual ReportingPeriodInvalid
      }

      "must write to json" in {
        Json.toJsObject[UploadFailureReason](ReportingPeriodInvalid) mustEqual json
      }
    }

    "when the reason is UpscanError" - {

      val failureReason = UpscanError(Quarantine)
      val json = Json.obj(
        "type" -> "UpscanError",
        "failureReason" -> Quarantine.entryName
      )

      "must read from json" in {
        Json.fromJson[UploadFailureReason](json).get mustEqual failureReason
      }

      "must write to json" in {
        Json.toJsObject[UploadFailureReason](failureReason) mustEqual json
      }
    }

    "when the reason is EntityTooSmall" - {

      val json = Json.obj(
        "type" -> "EntityTooSmall"
      )

      "must read from json" in {
        Json.fromJson[UploadFailureReason](json).get mustEqual EntityTooSmall
      }

      "must write to json" in {
        Json.toJsObject[UploadFailureReason](EntityTooSmall) mustEqual json
      }
    }

    "when the reason is EntityTooLarge" - {

      val json = Json.obj(
        "type" -> "EntityTooLarge"
      )

      "must read from json" in {
        Json.fromJson[UploadFailureReason](json).get mustEqual EntityTooLarge
      }

      "must write to json" in {
        Json.toJsObject[UploadFailureReason](EntityTooLarge) mustEqual json
      }
    }

    "when the reason is InvalidArgument" - {

      val json = Json.obj(
        "type" -> "InvalidArgument"
      )

      "must read from json" in {
        Json.fromJson[UploadFailureReason](json).get mustEqual InvalidArgument
      }

      "must write to json" in {
        Json.toJsObject[UploadFailureReason](InvalidArgument) mustEqual json
      }
    }
    
    "when the reason is UnknownFailure" - {

      val json = Json.obj(
        "type" -> "UnknownFailure"
      )

      "must read from json" in {
        Json.fromJson[UploadFailureReason](json).get mustEqual UnknownFailure
      }

      "must write to json" in {
        Json.toJsObject[UploadFailureReason](UnknownFailure) mustEqual json
      }
    }
  }
}
