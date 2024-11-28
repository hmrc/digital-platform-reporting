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
import org.scalatest.matchers.must.Matchers
import org.scalatest.{EitherValues, OptionValues, TryValues}
import support.builders.DateTime.aCompletedDateTime
import support.builders.PlatformOperatorBuilder.aPlatformOperator
import support.builders.StateBuilder.aStateApproved

class SuccessfulXmlSubmissionPlatformOperatorSpec extends AnyFreeSpec
  with Matchers
  with TryValues
  with OptionValues
  with EitherValues {

  ".apply(...)" - {
    "must create SuccessfulXmlSubmissionPlatformOperator object" in {
      SuccessfulXmlSubmissionPlatformOperator.apply(aStateApproved, aCompletedDateTime, aPlatformOperator) mustBe SuccessfulXmlSubmissionPlatformOperator(
        to = List(aPlatformOperator.primaryContactDetails.emailAddress),
        templateId = "dprs_successful_xml_submission_platform_operator",
        parameters = Map(
          "poPrimaryContactName" -> aPlatformOperator.primaryContactDetails.contactName,
          "poBusinessName" -> aPlatformOperator.operatorName,
          "poId" -> aPlatformOperator.operatorId,
          "checksCompletedDateTime" -> aCompletedDateTime,
          "reportingPeriod" -> aStateApproved.reportingPeriod.toString,
          "fileName" -> aStateApproved.fileName
        )
      )
    }
  }

}
