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

import models.operator.responses.PlatformOperator
import models.operator.{AddressDetails, ContactDetails}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{EitherValues, OptionValues, TryValues}

class FailedXmlSubmissionPlatformOperatorSpec extends AnyFreeSpec
  with Matchers
  with TryValues
  with OptionValues
  with EitherValues {

  val platformOperator: PlatformOperator = PlatformOperator(
    operatorId = "operatorId",
    operatorName = "operatorName",
    tinDetails = Seq.empty,
    businessName = None,
    tradingName = None,
    primaryContactDetails = ContactDetails(None, "name", "po.email@example.com"),
    secondaryContactDetails = None,
    addressDetails = AddressDetails("line 1", None, None, None, None, None),
    notifications = Seq.empty
  )

  val checksCompletedDateTime = "09:30am on 17th November 2024"

  ".apply(...)" - {
    "must create FailedXmlSubmissionPlatformOperator object" in {
      FailedXmlSubmissionPlatformOperator.apply(checksCompletedDateTime, platformOperator) mustBe FailedXmlSubmissionPlatformOperator(
        to = List("po.email@example.com"),
        templateId = "dprs_failed_xml_submission_platform_operator",
        parameters = Map(
          "poPrimaryContactName" -> "name",
          "poBusinessName" -> "operatorName",
          "checksCompletedDateTime" -> checksCompletedDateTime
        )
      )
    }
  }

}
