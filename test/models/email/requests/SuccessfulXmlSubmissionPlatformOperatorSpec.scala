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
}
