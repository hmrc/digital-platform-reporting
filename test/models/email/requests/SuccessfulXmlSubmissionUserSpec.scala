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
import models.submission.Submission.State
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

  private val underTest = SuccessfulXmlSubmissionUser

  ".apply(...)" - {
    "must create SuccessfulXmlSubmissionUser object" in {
      SuccessfulXmlSubmissionUser.apply("email@example.com", "first last", "some-business-name", "some-po-id", "09:30am on 17th November 2024", "2024", "test.xml") mustBe SuccessfulXmlSubmissionUser(
        to = List("email@example.com"),
        templateId = "dprs_successful_xml_submission_user",
        parameters = Map(
          "userPrimaryContactName" -> "first last",
          "poBusinessName" -> "some-business-name",
          "poId" -> "some-po-id",
          "checksCompletedDateTime" -> "09:30am on 17th November 2024",
          "reportingPeriod" -> "2024",
          "fileName" -> "test.xml"
        )
      )
    }
  }

  ".build(...)" - {

    val stateApproved = State.Approved(fileName = "test.xml", reportingPeriod = Year.of(2024))

    val platformOperator = PlatformOperator(
      operatorId = "operatorId",
      operatorName = "operatorName",
      tinDetails = Seq.empty,
      businessName = None,
      tradingName = None,
      primaryContactDetails = ContactDetails(None, "name", "email"),
      secondaryContactDetails = None,
      addressDetails = AddressDetails("line 1", None, None, None, None, None),
      notifications = Seq.empty
    )

    val expectedIndividual = IndividualContact(Individual("first", "last"), "email", None)
    val subscriptionInfo = SubscriptionInfo("DPRS123", true, None, expectedIndividual, None)

    val checksCompletedDateTime = "09:30am on 17th November 2024"

    "must return correct SuccessfulXmlSubmissionUser" in {

      underTest.build(stateApproved, checksCompletedDateTime, platformOperator, subscriptionInfo) mustBe SuccessfulXmlSubmissionUser(
        to = List(subscriptionInfo.primaryContact.email),
        templateId = "dprs_successful_xml_submission_user",
        parameters = Map(
          "userPrimaryContactName" -> "first last",
          "poBusinessName" -> "operatorName",
          "poId" -> "operatorId",
          "checksCompletedDateTime" -> checksCompletedDateTime,
          "reportingPeriod" -> stateApproved.reportingPeriod.toString,
          "fileName" -> stateApproved.fileName
        )
      )
    }

  }
}
