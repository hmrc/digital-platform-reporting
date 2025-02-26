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

class FailedXmlSubmissionPlatformOperatorSpec extends SpecBase {

  private val anyString = "any-date-time-string"

  ".apply(...)" - {
    "must create FailedXmlSubmissionPlatformOperator object" in {
      FailedXmlSubmissionPlatformOperator.apply(anyString, aPlatformOperator) mustBe FailedXmlSubmissionPlatformOperator(
        to = List(aPlatformOperator.primaryContactDetails.emailAddress),
        templateId = "dprs_failed_xml_submission_platform_operator",
        parameters = Map(
          "poPrimaryContactName" -> aPlatformOperator.primaryContactDetails.contactName,
          "poBusinessName" -> aPlatformOperator.operatorName,
          "checksCompletedDateTime" -> anyString
        )
      )
    }
  }

  "format" - {
    val parameters = Map(
      "poPrimaryContactName" -> aPlatformOperator.primaryContactDetails.contactName,
      "poBusinessName" -> aPlatformOperator.operatorName,
      "checksCompletedDateTime" -> anyString
    )

    "must serialize to JSON correctly" in {
      val request = FailedXmlSubmissionPlatformOperator(
        to = List("po@example.com"),
        templateId = "dprs_failed_xml_submission_platform_operator",
        parameters = parameters
      )

      Json.toJson(request) mustBe Json.obj(
        "to" -> Json.arr("po@example.com"),
        "templateId" -> "dprs_failed_xml_submission_platform_operator",
        "parameters" -> Json.obj(
          "poPrimaryContactName" -> aPlatformOperator.primaryContactDetails.contactName,
          "poBusinessName" -> aPlatformOperator.operatorName,
          "checksCompletedDateTime" -> anyString
        )
      )
    }

    "must deserialize from JSON correctly" in {
      val json = Json.obj(
        "to" -> Json.arr("po@example.com"),
        "templateId" -> "dprs_failed_xml_submission_platform_operator",
        "parameters" -> Json.obj(
          "poPrimaryContactName" -> aPlatformOperator.primaryContactDetails.contactName,
          "poBusinessName" -> aPlatformOperator.operatorName,
          "checksCompletedDateTime" -> anyString
        )
      )

      val result = Json.fromJson[FailedXmlSubmissionPlatformOperator](json)

      result.isSuccess mustBe true
      result.get mustBe FailedXmlSubmissionPlatformOperator(
        to = List("po@example.com"),
        templateId = "dprs_failed_xml_submission_platform_operator",
        parameters = parameters
      )
    }
  }
}
