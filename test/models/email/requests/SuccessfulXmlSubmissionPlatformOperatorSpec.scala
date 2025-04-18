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

package models.email.requests

import org.scalatest.freespec.AnyFreeSpec
import play.api.libs.json.Json
import support.SpecBase
import support.builders.PlatformOperatorBuilder.aPlatformOperator
import support.builders.StateBuilder.aStateApproved

class SuccessfulXmlSubmissionPlatformOperatorSpec extends SpecBase {

  ".apply(...)" - {
    "must create SuccessfulXmlSubmissionPlatformOperator object" in {
      val anyString = "any-date-time-string"
      SuccessfulXmlSubmissionPlatformOperator.apply(aStateApproved, anyString, aPlatformOperator) mustBe SuccessfulXmlSubmissionPlatformOperator(
        to = List(aPlatformOperator.primaryContactDetails.emailAddress),
        templateId = "dprs_successful_xml_submission_platform_operator",
        parameters = Map(
          "poPrimaryContactName" -> aPlatformOperator.primaryContactDetails.contactName,
          "poBusinessName" -> aPlatformOperator.operatorName,
          "poId" -> aPlatformOperator.operatorId,
          "checksCompletedDateTime" -> anyString,
          "reportingPeriod" -> aStateApproved.reportingPeriod.toString,
          "fileName" -> aStateApproved.fileName
        )
      )
    }
  }

  "format" - {
    val parameters = Map(
      "poPrimaryContactName" -> "PO Contact",
      "poBusinessName" -> "PO Business",
      "poId" -> "po123",
      "checksCompletedDateTime" -> "2024-10-15T12:00:00Z",
      "reportingPeriod" -> "2024",
      "fileName" -> "file.xml"
    )

    "must serialize to JSON correctly" in {
      val request = SuccessfulXmlSubmissionPlatformOperator(
        to = List("po@example.com"),
        templateId = "dprs_successful_xml_submission_platform_operator",
        parameters = parameters
      )

      Json.toJson(request) mustBe Json.obj(
        "to" -> Json.arr("po@example.com"),
        "templateId" -> "dprs_successful_xml_submission_platform_operator",
        "parameters" -> Json.obj(
          "poPrimaryContactName" -> "PO Contact",
          "poBusinessName" -> "PO Business",
          "poId" -> "po123",
          "checksCompletedDateTime" -> "2024-10-15T12:00:00Z",
          "reportingPeriod" -> "2024",
          "fileName" -> "file.xml"
        )
      )
    }

    "must deserialize from JSON correctly" in {
      val json = Json.obj(
        "to" -> Json.arr("po@example.com"),
        "templateId" -> "dprs_successful_xml_submission_platform_operator",
        "parameters" -> Json.obj(
          "poPrimaryContactName" -> "PO Contact",
          "poBusinessName" -> "PO Business",
          "poId" -> "po123",
          "checksCompletedDateTime" -> "2024-10-15T12:00:00Z",
          "reportingPeriod" -> "2024",
          "fileName" -> "file.xml"
        )
      )

      val result = Json.fromJson[SuccessfulXmlSubmissionPlatformOperator](json)

      result.isSuccess mustBe true
      result.get mustBe SuccessfulXmlSubmissionPlatformOperator(
        to = List("po@example.com"),
        templateId = "dprs_successful_xml_submission_platform_operator",
        parameters = parameters
      )
    }
  }
}
