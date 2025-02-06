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

import models.audit.AddSubmissionEvent.DeliveryRoute
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

import java.time.{Instant, Year}

class AddSubmissionEventSpec extends AnyFreeSpec with Matchers {

  "must write to correct json structure" in {

    val now = Instant.now()

    val expectedJson = Json.obj(
      "platformOperatorId" -> "poId",
      "digitalPlatformReportingId" -> "dprsId",
      "platformOperator" -> "po",
      "fileSizeInBytes" -> 1337,
      "reportingPeriod" -> "2024",
      "conversationId" -> "submissionId",
      "fileName" -> "test.xml",
      "outcome" -> Json.obj(
        "deliveryRoute" -> DeliveryRoute.Dct52A,
        "processedAt" -> now,
        "isSent" -> true
      )
    )

    val event = AddSubmissionEvent(
      conversationId = "submissionId",
      dprsId = "dprsId",
      operatorId = "poId",
      operatorName = "po",
      reportingPeriod = Year.of(2024),
      fileName = "test.xml",
      fileSize = 1337,
      deliveryRoute = DeliveryRoute.Dct52A,
      processedAt = now,
      isSent = true
    )

    Json.toJson(event) mustEqual expectedJson
  }
}
