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

class DeliveredSubmissionRequestSpec extends AnyFreeSpec with Matchers {

  ".reads" - {
    
    "must read json with all details" in {

      val json = Json.obj(
        "subscriptionId" -> "dprsId",
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

      val expectedRequest = DeliveredSubmissionRequest(
        subscriptionId = "dprsId",
        assumedReporting = true,
        pageNumber = 2,
        sortBy = DeliveredSubmissionSortBy.PlatformOperator,
        sortOrder = SortOrder.Ascending,
        reportingPeriod = Some(2025),
        operatorId = Some("operatorId"),
        fileName = Some("file.xml"),
        statuses = Seq(DeliveredSubmissionStatus.Rejected, DeliveredSubmissionStatus.Success)
      )

      json.as[DeliveredSubmissionRequest] mustEqual expectedRequest
    }
    
    "must read json details missing, using defaults instead" in {
      
      val json = Json.obj(
        "subscriptionId" -> "dprsId",
        "assumedReporting" -> true
      )
      
      val expectedRequest = DeliveredSubmissionRequest(
        subscriptionId = "dprsId",
        assumedReporting = true,
        pageNumber = 1,
        sortBy = DeliveredSubmissionSortBy.SubmissionDate,
        sortOrder = SortOrder.Descending,
        reportingPeriod = None,
        operatorId = None,
        fileName = None,
        statuses = Nil
      )
      
      json.as[DeliveredSubmissionRequest] mustEqual expectedRequest
    }
  }
  
  ".writes" - {

    "must write the correct json with all details" in {

      val request = DeliveredSubmissionRequest(
        subscriptionId = "dprsId",
        assumedReporting = false,
        pageNumber = 2,
        sortBy = DeliveredSubmissionSortBy.ReportingPeriod,
        sortOrder = SortOrder.Ascending,
        reportingPeriod = Some(2024),
        operatorId = Some("operatorId"),
        fileName = Some("fileName"),
        statuses = Seq(DeliveredSubmissionStatus.Success, DeliveredSubmissionStatus.Rejected)
      )

      val expectedJson = Json.obj(
        "submissionsListRequest" -> Json.obj(
          "requestCommon" -> Json.obj(
            "originatingSystem" -> "MDTP",
            "regime" -> "DPRS",
            "transmittingSystem" -> "EIS"
          ),
          "requestDetails" -> Json.obj(
            "subscriptionId" -> "dprsId",
            "isManual" -> false,
            "pageNumber" -> 2,
            "sortBy" -> "REPORTINGYEAR",
            "sortOrder" -> "ASC",
            "reportingYear" -> "2024",
            "pOId" -> "operatorId",
            "fileName" -> "fileName",
            "submissionStatus" -> "SUCCESS,REJECTED"
          )
        )
      )

      Json.toJsObject(request) mustEqual expectedJson
    }

    "must write the correct json with minimal details" in {

      val request = DeliveredSubmissionRequest(
        subscriptionId = "dprsId",
        assumedReporting = false
      )

      val expectedJson = Json.obj(
        "submissionsListRequest" -> Json.obj(
          "requestCommon" -> Json.obj(
            "originatingSystem" -> "MDTP",
            "regime" -> "DPRS",
            "transmittingSystem" -> "EIS"
          ),
          "requestDetails" -> Json.obj(
            "subscriptionId" -> "dprsId",
            "isManual" -> false,
            "pageNumber" -> 1,
            "sortBy" -> "SUBMISSIONDATE",
            "sortOrder" -> "DSC"
          )
        )
      )

      Json.toJsObject(request) mustEqual expectedJson
    }
  }
}
