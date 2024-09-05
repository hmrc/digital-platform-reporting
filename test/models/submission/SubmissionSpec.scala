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
      platformOperatorId = "poid",
      state = Ready,
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
      "platformOperatorId" -> "poid",
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
      platformOperatorId = "poid",
      state = Uploading,
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
      "platformOperatorId" -> "poid",
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
      platformOperatorId = "poid",
      state = UploadFailed("some reason"),
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
      "platformOperatorId" -> "poid",
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
      platformOperatorId = "poid",
      state = Validated,
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
      "platformOperatorId" -> "poid",
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
      platformOperatorId = "poid",
      state = Submitted,
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
      "platformOperatorId" -> "poid",
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
      platformOperatorId = "poid",
      state = Approved,
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
      "platformOperatorId" -> "poid",
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
      platformOperatorId = "poid",
      state = Rejected("some reason"),
      created = created,
      updated = updated
    )

    val json = Json.obj(
      "_id" -> "id",
      "dprsId" -> "dprsId",
      "platformOperatorId" -> "poid",
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
