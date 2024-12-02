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

package services

import connectors.EmailConnector
import models.operator.{AddressDetails, ContactDetails}
import models.operator.responses.PlatformOperator
import models.submission.Submission.State
import models.subscription.responses.SubscriptionInfo
import models.subscription._
import models.email.requests._
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Year
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture

import scala.concurrent.Future

class EmailServiceSpec extends AnyFreeSpec
  with Matchers
  with MockitoSugar
  with FutureAwaits
  with TryValues
  with DefaultAwaitTimeout
  with BeforeAndAfterEach {

  private implicit val headerCarrier: HeaderCarrier = HeaderCarrier()

  private val mockEmailConnector = mock[EmailConnector]

  override def beforeEach(): Unit = {
    Mockito.reset(mockEmailConnector)
    super.beforeEach()
  }

  private val underTest = new EmailService(mockEmailConnector)

  ".sendSuccessfulBusinessRulesChecksEmails(...)" - {

    val stateApproved = State.Approved(fileName = "test.xml", reportingPeriod = Year.of(2024))

    val platformOperator = PlatformOperator(
      operatorId = "operatorId",
      operatorName = "operatorName",
      tinDetails = Seq.empty,
      businessName = None,
      tradingName = None,
      primaryContactDetails = ContactDetails(None, "name", "po.email"),
      secondaryContactDetails = None,
      addressDetails = AddressDetails("line 1", None, None, None, None, None),
      notifications = Seq.empty
    )

    val checksCompletedDateTime = "09:30am on 17th November 2024"

    "non-matching emails must send both SuccessfulXmlSubmissionUser SuccessfulXmlSubmissionPlatformOperator" in {

      val expectedIndividual = IndividualContact(Individual("first", "last"), "user.email", None)
      val subscriptionInfo = SubscriptionInfo("DPRS123", true, None, expectedIndividual, None)

      val expectedSuccessfulXmlSubmissionUser = SuccessfulXmlSubmissionUser(stateApproved, checksCompletedDateTime, platformOperator, subscriptionInfo)
      val expectedSuccessfulXmlSubmissionPlatformOperator = SuccessfulXmlSubmissionPlatformOperator(stateApproved, checksCompletedDateTime, platformOperator)

      when(mockEmailConnector.send(any())(any())).thenReturn(Future.successful(Done))

      underTest.sendSuccessfulBusinessRulesChecksEmails(stateApproved, checksCompletedDateTime, platformOperator, subscriptionInfo).futureValue

      verify(mockEmailConnector, times(1)).send(eqTo(expectedSuccessfulXmlSubmissionUser))(any())
      verify(mockEmailConnector, times(1)).send(eqTo(expectedSuccessfulXmlSubmissionPlatformOperator))(any())
    }

    "matching emails must send only SuccessfulXmlSubmissionUser" in {

      val expectedIndividual = IndividualContact(Individual("first", "last"), "po.email", None)
      val subscriptionInfo = SubscriptionInfo("DPRS123", true, None, expectedIndividual, None)

      val expectedSuccessfulXmlSubmissionUser = SuccessfulXmlSubmissionUser(stateApproved, checksCompletedDateTime, platformOperator, subscriptionInfo)
      val expectedSuccessfulXmlSubmissionPlatformOperator = SuccessfulXmlSubmissionPlatformOperator(stateApproved, checksCompletedDateTime, platformOperator)

      when(mockEmailConnector.send(any())(any())).thenReturn(Future.successful(Done))

      underTest.sendSuccessfulBusinessRulesChecksEmails(stateApproved, checksCompletedDateTime, platformOperator, subscriptionInfo).futureValue

      verify(mockEmailConnector, times(1)).send(eqTo(expectedSuccessfulXmlSubmissionUser))(any())
      verify(mockEmailConnector, never()).send(eqTo(expectedSuccessfulXmlSubmissionPlatformOperator))(any())
    }


  }

  ".sendFailedBusinessRulesChecksEmails(...)" - {

    val stateRejected = State.Rejected(fileName = "test.xml", reportingPeriod = Year.of(2024))

    val platformOperator = PlatformOperator(
      operatorId = "operatorId",
      operatorName = "operatorName",
      tinDetails = Seq.empty,
      businessName = Some("businessName"),
      tradingName = None,
      primaryContactDetails = ContactDetails(None, "name", "po.email"),
      secondaryContactDetails = None,
      addressDetails = AddressDetails("line 1", None, None, None, None, None),
      notifications = Seq.empty
    )

    val checksCompletedDateTime = "09:30am on 17th November 2024"


    "non-matching emails must send both FailedXmlSubmissionUser FailedXmlSubmissionPlatformOperator" in {

      val expectedIndividual = IndividualContact(Individual("first", "last"), "user.email", None)
      val subscriptionInfo = SubscriptionInfo("DPRS123", true, None, expectedIndividual, None)

      val expectedFailedXmlSubmissionUser = FailedXmlSubmissionUser(stateRejected, checksCompletedDateTime, platformOperator, subscriptionInfo)
      val expectedFailedXmlSubmissionPlatformOperator = FailedXmlSubmissionPlatformOperator(checksCompletedDateTime, platformOperator)

      when(mockEmailConnector.send(any())(any())).thenReturn(Future.successful(Done))

      underTest.sendFailedBusinessRulesChecksEmails(stateRejected, checksCompletedDateTime, platformOperator, subscriptionInfo).futureValue

      verify(mockEmailConnector, times(1)).send(eqTo(expectedFailedXmlSubmissionUser))(any())
      verify(mockEmailConnector, times(1)).send(eqTo(expectedFailedXmlSubmissionPlatformOperator))(any())
    }

    "matching emails must send only FailedXmlSubmissionUser" in {

      val expectedIndividual = IndividualContact(Individual("first", "last"), "po.email", None)
      val subscriptionInfo = SubscriptionInfo("DPRS123", true, None, expectedIndividual, None)

      val expectedFailedXmlSubmissionUser = FailedXmlSubmissionUser(stateRejected, checksCompletedDateTime, platformOperator, subscriptionInfo)
      val expectedFailedXmlSubmissionPlatformOperator = FailedXmlSubmissionPlatformOperator(checksCompletedDateTime, platformOperator)

      when(mockEmailConnector.send(any())(any())).thenReturn(Future.successful(Done))

      underTest.sendFailedBusinessRulesChecksEmails(stateRejected, checksCompletedDateTime, platformOperator, subscriptionInfo).futureValue

      verify(mockEmailConnector, times(1)).send(eqTo(expectedFailedXmlSubmissionUser))(any())
      verify(mockEmailConnector, never()).send(eqTo(expectedFailedXmlSubmissionPlatformOperator))(any())
    }

  }


}