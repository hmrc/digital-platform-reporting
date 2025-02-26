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
import support.builders.SubscriptionInfoBuilder.aSubscriptionInfo

class SuccessfulXmlSubmissionUserSpec extends SpecBase {

  ".apply(...)" - {
    "must create SuccessfulXmlSubmissionUser object" in {
      val anyString = "any-date-time-string"
      SuccessfulXmlSubmissionUser.apply(aStateApproved, anyString, aPlatformOperator, aSubscriptionInfo) mustBe
        SuccessfulXmlSubmissionUser(
          to = List(aSubscriptionInfo.primaryContact.email),
          templateId = "dprs_successful_xml_submission_user",
          parameters = Map(
            "userPrimaryContactName" -> aSubscriptionInfo.primaryContactName,
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
      "userPrimaryContactName" -> "User Contact",
      "poBusinessName" -> "PO Business",
      "poId" -> "po123",
      "checksCompletedDateTime" -> "2024-10-15T12:00:00Z",
      "reportingPeriod" -> "2024",
      "fileName" -> "file.xml"
    )

    "must serialize to JSON" in {
      val request = SuccessfulXmlSubmissionUser(
        to = List("user@example.com"),
        templateId = "any-template",
        parameters = parameters
      )

      Json.toJson(request) mustBe Json.obj(
        "to" -> Json.arr("user@example.com"),
        "templateId" -> "any-template",
        "parameters" -> Json.obj(
          "userPrimaryContactName" -> "User Contact",
          "poBusinessName" -> "PO Business",
          "poId" -> "po123",
          "checksCompletedDateTime" -> "2024-10-15T12:00:00Z",
          "reportingPeriod" -> "2024",
          "fileName" -> "file.xml"
        )
      )
    }

    "must deserialize from JSON" in {
      val json = Json.obj(
        "to" -> Json.arr("user@example.com"),
        "templateId" -> "dprs_successful_xml_submission_user",
        "parameters" -> Json.obj(
          "userPrimaryContactName" -> "User Contact",
          "poBusinessName" -> "PO Business",
          "poId" -> "po123",
          "checksCompletedDateTime" -> "2024-10-15T12:00:00Z",
          "reportingPeriod" -> "2024",
          "fileName" -> "file.xml"
        )
      )
      val result = Json.fromJson[SuccessfulXmlSubmissionUser](json)
      result.isSuccess mustBe true
      result.get mustBe SuccessfulXmlSubmissionUser(
        to = List("user@example.com"),
        templateId = "dprs_successful_xml_submission_user",
        parameters = parameters
      )
    }
  }
}
