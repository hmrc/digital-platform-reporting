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
import support.builders.StateBuilder.aStateRejected
import support.builders.SubscriptionInfoBuilder.aSubscriptionInfo

class FailedXmlSubmissionUserSpec extends SpecBase {
  private val anyString = "any-date-time-string"

  ".apply(...)" - {
    "must create FailedXmlSubmissionUser object" in {
      FailedXmlSubmissionUser.apply(aStateRejected, anyString, aPlatformOperator, aSubscriptionInfo) mustBe FailedXmlSubmissionUser(
        to = List(aSubscriptionInfo.primaryContact.email),
        templateId = "dprs_failed_xml_submission_user",
        parameters = Map(
          "userPrimaryContactName" -> aSubscriptionInfo.primaryContactName,
          "poBusinessName" -> aPlatformOperator.operatorName,
          "checksCompletedDateTime" -> anyString,
          "fileName" -> aStateRejected.fileName
        )
      )
    }
  }

  "format" - {
    val parameters = Map(
      "userPrimaryContactName" -> aSubscriptionInfo.primaryContactName,
      "poBusinessName" -> aPlatformOperator.operatorName,
      "checksCompletedDateTime" -> anyString,
      "fileName" -> aStateRejected.fileName
    )

    "must serialize to JSON correctly" in {
      val request = FailedXmlSubmissionUser(
        to = List("user@example.com"),
        templateId = "dprs_failed_xml_submission_user",
        parameters = parameters
      )

      Json.toJson(request) mustBe Json.obj(
        "to" -> Json.arr("user@example.com"),
        "templateId" -> "dprs_failed_xml_submission_user",
        "parameters" -> Json.obj(
          "userPrimaryContactName" -> aSubscriptionInfo.primaryContactName,
          "poBusinessName" -> aPlatformOperator.operatorName,
          "checksCompletedDateTime" -> anyString,
          "fileName" -> aStateRejected.fileName
        )
      )
    }

    "must deserialize from JSON correctly" in {
      val json = Json.obj(
        "to" -> Json.arr("user@example.com"),
        "templateId" -> "dprs_failed_xml_submission_user",
        "parameters" -> Json.obj(
          "userPrimaryContactName" -> aSubscriptionInfo.primaryContactName,
          "poBusinessName" -> aPlatformOperator.operatorName,
          "checksCompletedDateTime" -> anyString,
          "fileName" -> aStateRejected.fileName
        )
      )

      val result = Json.fromJson[FailedXmlSubmissionUser](json)

      result.isSuccess mustBe true
      result.get mustBe FailedXmlSubmissionUser(
        to = List("user@example.com"),
        templateId = "dprs_failed_xml_submission_user",
        parameters = parameters
      )
    }
  }
}
