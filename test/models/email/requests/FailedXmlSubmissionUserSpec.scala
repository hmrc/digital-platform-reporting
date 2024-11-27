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
import models.submission.Submission.State
import models.subscription.responses.SubscriptionInfo
import models.subscription.{Individual, IndividualContact}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{EitherValues, OptionValues, TryValues}

import java.time.Year

class FailedXmlSubmissionUserSpec extends AnyFreeSpec
  with Matchers
  with TryValues
  with OptionValues
  with EitherValues {

  val stateRejected: State.Rejected = State.Rejected(fileName = "test.xml", reportingPeriod = Year.of(2024))

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

  val expectedIndividual: IndividualContact = IndividualContact(Individual("first", "last"), "user.email@example.com", None)
  val subscriptionInfo: SubscriptionInfo = SubscriptionInfo("DPRS123", true, None, expectedIndividual, None)

  val checksCompletedDateTime = "09:30am on 17th November 2024"

  ".apply(...)" - {
    "must create FailedXmlSubmissionUser object" in {
      FailedXmlSubmissionUser.apply(stateRejected, checksCompletedDateTime, platformOperator, subscriptionInfo) mustBe FailedXmlSubmissionUser(
        to = List("user.email@example.com"),
        templateId = "dprs_failed_xml_submission_user",
        parameters = Map(
          "userPrimaryContactName" -> "first last",
          "poBusinessName" -> "operatorName",
          "checksCompletedDateTime" -> checksCompletedDateTime,
          "fileName" -> "test.xml"
        )
      )
    }
  }

}
