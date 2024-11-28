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

import models.audit.CadxSubmissionResponseEvent.FileStatus
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

class CadxSubmissionResponseEventSpec extends AnyFreeSpec with Matchers {

  "must write to correct json structure when the file status is Passed" in {

    val expectedJson = Json.obj(
      "platformOperatorId" -> "poId",
      "digitalPlatformReportingId" -> "dprsId",
      "platformOperator" -> "po",
      "conversationId" -> "submissionId",
      "fileName" -> "test.xml",
      "fileStatus" -> "passed"
    )

    val event = CadxSubmissionResponseEvent(
      conversationId = "submissionId",
      dprsId = "dprsId",
      operatorId = "poId",
      operatorName = "po",
      fileName = "test.xml",
      fileStatus = FileStatus.Passed
    )

    Json.toJson(event) mustEqual expectedJson
  }

  "must write to correct json structure when the file status is Failed" in {

    val expectedJson = Json.obj(
      "platformOperatorId" -> "poId",
      "digitalPlatformReportingId" -> "dprsId",
      "platformOperator" -> "po",
      "conversationId" -> "submissionId",
      "fileName" -> "test.xml",
      "fileStatus" -> "failed"
    )

    val event = CadxSubmissionResponseEvent(
      conversationId = "submissionId",
      dprsId = "dprsId",
      operatorId = "poId",
      operatorName = "po",
      fileName = "test.xml",
      fileStatus = FileStatus.Failed
    )

    Json.toJson(event) mustEqual expectedJson
  }
}
