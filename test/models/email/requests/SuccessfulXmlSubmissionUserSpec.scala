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

import models.operator.{AddressDetails, ContactDetails}
import models.operator.responses.PlatformOperator
import models.submission.Submission.State.Approved
import models.subscription.responses.SubscriptionInfo
import models.subscription.{Individual, IndividualContact}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{EitherValues, OptionValues, TryValues}

import java.time.Year

class SuccessfulXmlSubmissionUserSpec extends AnyFreeSpec
  with Matchers
  with TryValues
  with OptionValues
  with EitherValues {

  val stateApproved: Approved = Approved("test.xml", Year.of(2024))
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
  val expectedIndividual: IndividualContact = IndividualContact(Individual("first", "last"), "user.email@example.com", None)
  val subscriptionInfo: SubscriptionInfo = SubscriptionInfo("DPRS123", gbUser = true, None, expectedIndividual, None)

  ".apply(...)" - {
    "must create SuccessfulXmlSubmissionUser object" in {
      SuccessfulXmlSubmissionUser.apply(stateApproved, checksCompletedDateTime, platformOperator, subscriptionInfo) mustBe SuccessfulXmlSubmissionUser(
        to = List("user.email@example.com"),
        templateId = "dprs_successful_xml_submission_user",
        parameters = Map(
          "userPrimaryContactName" -> "first last",
          "poBusinessName" -> "operatorName",
          "poId" -> "operatorId",
          "checksCompletedDateTime" -> checksCompletedDateTime,
          "reportingPeriod" -> "2024",
          "fileName" -> "test.xml"
        )
      )
    }
  }

}
