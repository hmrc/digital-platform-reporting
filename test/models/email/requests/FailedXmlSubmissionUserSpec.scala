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
import support.builders.StateBuilder.aStateRejected
import support.builders.SubscriptionInfoBuilder.aSubscriptionInfo

class FailedXmlSubmissionUserSpec extends AnyFreeSpec
  with Matchers
  with TryValues
  with OptionValues
  with EitherValues {

  ".apply(...)" - {
    "must create FailedXmlSubmissionUser object" in {
      FailedXmlSubmissionUser.apply(aStateRejected, aCompletedDateTime, aPlatformOperator, aSubscriptionInfo) mustBe FailedXmlSubmissionUser(
        to = List(aSubscriptionInfo.primaryContact.email),
        templateId = "dprs_failed_xml_submission_user",
        parameters = Map(
          "userPrimaryContactName" -> aSubscriptionInfo.primaryContactName,
          "poBusinessName" -> aPlatformOperator.operatorName,
          "checksCompletedDateTime" -> aCompletedDateTime,
          "fileName" -> aStateRejected.fileName
        )
      )
    }
  }

}
