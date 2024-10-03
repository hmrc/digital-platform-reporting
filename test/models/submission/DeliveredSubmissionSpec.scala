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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.*

import java.time.Instant
import java.util.UUID

class DeliveredSubmissionSpec extends AnyFreeSpec with Matchers {

  ".reads" - {
    
    val conversationId = UUID.randomUUID().toString
    
    "must read json with all fields present" in {
      
      val json = Json.obj(
        "conversationId" -> conversationId,
        "fileName" -> "file.xml",
        "pOId" -> "operatorId",
        "pOName" -> "operatorName",
        "reportingYear" -> "2024",
        "submissionDateTime" -> "2024-12-31T01:02:03Z",
        "submissionStatus" -> "PENDING",
        "assumingReporterName" -> "assumingName"
      )
      
      val expectedSubmission = DeliveredSubmission(
        conversationId = conversationId,
        fileName = "file.xml",
        operatorId = "operatorId",
        operatorName = "operatorName",
        reportingPeriod = "2024",
        submissionDateTime = Instant.parse("2024-12-31T01:02:03Z"),
        submissionStatus = DeliveredSubmissionStatus.Pending,
        assumingReporterName = Some("assumingName")
      )
      
      json.as[DeliveredSubmission] mustEqual expectedSubmission
    }
    
    "must read json with optional fields missing" in {

      val json = Json.obj(
        "conversationId" -> conversationId,
        "fileName" -> "file.xml",
        "pOId" -> "operatorId",
        "pOName" -> "operatorName",
        "reportingYear" -> "2024",
        "submissionDateTime" -> "2024-12-31T01:02:03Z",
        "submissionStatus" -> "PENDING"
      )

      val expectedSubmission = DeliveredSubmission(
        conversationId = conversationId,
        fileName = "file.xml",
        operatorId = "operatorId",
        operatorName = "operatorName",
        reportingPeriod = "2024",
        submissionDateTime = Instant.parse("2024-12-31T01:02:03Z"),
        submissionStatus = DeliveredSubmissionStatus.Pending,
        assumingReporterName = None
      )

      json.as[DeliveredSubmission] mustEqual expectedSubmission
    }
  }
}
