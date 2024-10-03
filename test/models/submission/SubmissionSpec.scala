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
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
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
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
      state = Uploading,
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
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
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
      state = UploadFailed("some reason"),
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
      "operatorId" -> "operatorId",
      "operatorName" -> "operatorName",
      "state" -> Json.obj(
        "type" -> "UploadFailed",
        "reason" -> "some reason"
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
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
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
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
      state = Submitted(
        fileName = "test.xml",
        reportingPeriod = Year.of(2024)
      ),
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
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
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
      state = Approved(
        fileName = "test.xml",
        reportingPeriod = Year.of(2024)
      ),
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
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
      dprsId = "dprsId",
      operatorId = "operatorId",
      operatorName = "operatorName",
      state = Rejected(
        fileName = "test.xml",
        reportingPeriod = Year.of(2024)
      ),
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
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
}
