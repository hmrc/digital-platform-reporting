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
import play.api.libs.json.{JsString, Json}

class DeliveredSubmissionInboundRequestSpec extends AnyFreeSpec with Matchers {

  ".reads" - {

    "must read json with all details" in {

      val json = Json.obj(
        "assumedReporting" -> true,
        "pageNumber" -> 2,
        "sortBy" -> "PONAME",
        "sortOrder" -> "ASC",
        "reportingPeriod" -> 2025,
        "operatorId" -> "operatorId",
        "fileName" -> "file.xml",
        "statuses" -> Json.arr(
          JsString("REJECTED"),
          JsString("SUCCESS")
        )
      )

      val expectedRequest = DeliveredSubmissionInboundRequest(
        assumedReporting = true,
        pageNumber = 2,
        sortBy = DeliveredSubmissionSortBy.PlatformOperator,
        sortOrder = SortOrder.Ascending,
        reportingPeriod = Some(2025),
        operatorId = Some("operatorId"),
        fileName = Some("file.xml"),
        statuses = Seq(DeliveredSubmissionStatus.Rejected, DeliveredSubmissionStatus.Success)
      )

      json.as[DeliveredSubmissionInboundRequest] mustEqual expectedRequest
    }

    "must read json details missing, using defaults instead" in {

      val json = Json.obj(
        "assumedReporting" -> true
      )

      val expectedRequest = DeliveredSubmissionInboundRequest(
        assumedReporting = true,
        pageNumber = 1,
        sortBy = DeliveredSubmissionSortBy.SubmissionDate,
        sortOrder = SortOrder.Descending,
        reportingPeriod = None,
        operatorId = None,
        fileName = None,
        statuses = Nil
      )

      json.as[DeliveredSubmissionInboundRequest] mustEqual expectedRequest
    }
  }

}
