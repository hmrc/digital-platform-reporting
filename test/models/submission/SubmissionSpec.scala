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

import java.time.Instant
import java.time.temporal.ChronoUnit

class SubmissionSpec extends AnyFreeSpec with Matchers {

  private val created = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  private val updated = created.plus(1, ChronoUnit.DAYS)

  "when state is Ready" - {

    val submission = Submission(
      _id = "id",
      dprsId = "dprsId",
      state = Ready,
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
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
      state = Uploading,
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
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
      state = UploadFailed("some reason"),
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
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
      state = Validated,
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
      "state" -> Json.obj(
        "type" -> "Validated"
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
      state = Submitted,
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
      "state" -> Json.obj(
        "type" -> "Submitted"
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
      state = Approved,
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
      "state" -> Json.obj(
        "type" -> "Approved"
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
      state = Rejected("some reason"),
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
      "state" -> Json.obj(
        "type" -> "Rejected",
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
}
