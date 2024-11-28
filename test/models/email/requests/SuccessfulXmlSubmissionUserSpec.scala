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
import models.submission.Submission.State.Approved
import models.subscription.responses.SubscriptionInfo
import models.subscription.{Individual, IndividualContact}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{EitherValues, OptionValues, TryValues}
import support.builders.PlatformOperatorBuilder.aPlatformOperator

import java.time.Year

class SuccessfulXmlSubmissionUserSpec extends AnyFreeSpec
  with Matchers
  with TryValues
  with OptionValues
  with EitherValues {

  val stateApproved: Approved = Approved("test.xml", Year.of(2024))
  val checksCompletedDateTime = "09:30am on 17th November 2024"
  val expectedIndividual: IndividualContact = IndividualContact(Individual("first", "last"), "user.email@example.com", None)
  val subscriptionInfo: SubscriptionInfo = SubscriptionInfo("DPRS123", gbUser = true, None, expectedIndividual, None)

  ".apply(...)" - {
    "must create SuccessfulXmlSubmissionUser object" in {
      SuccessfulXmlSubmissionUser.apply(stateApproved, checksCompletedDateTime, aPlatformOperator, subscriptionInfo) mustBe SuccessfulXmlSubmissionUser(
        to = List("user.email@example.com"),
        templateId = "dprs_successful_xml_submission_user",
        parameters = Map(
          "userPrimaryContactName" -> subscriptionInfo.primaryContactName,
          "poBusinessName" -> aPlatformOperator.operatorName,
          "poId" -> aPlatformOperator.operatorId,
          "checksCompletedDateTime" -> checksCompletedDateTime,
          "reportingPeriod" -> stateApproved.reportingPeriod.toString,
          "fileName" -> stateApproved.fileName
        )
      )
    }
  }

}
