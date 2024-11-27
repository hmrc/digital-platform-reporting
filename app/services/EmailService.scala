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

import com.google.inject.Inject
import connectors.EmailConnector
import models.email.requests.{FailedXmlSubmissionPlatformOperator, FailedXmlSubmissionUser, SendEmailRequest, SuccessfulXmlSubmissionPlatformOperator, SuccessfulXmlSubmissionUser}
import models.operator.responses.PlatformOperator
import models.submission.Submission.State.{Approved, Rejected}
import models.subscription.responses.SubscriptionInfo
import org.apache.pekko.Done
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class EmailService @Inject()(emailConnector: EmailConnector) {

  def sendSuccessfulBusinessRulesChecksEmails(state: Approved, checksCompletedDateTime: String, platformOperator: PlatformOperator, subscriptionInfo: SubscriptionInfo)
                                   (implicit hc: HeaderCarrier): Future[Done] = {
    if (!matchingEmails(subscriptionInfo.primaryContact.email, platformOperator.primaryContactDetails.emailAddress)) {
      sendEmail(SuccessfulXmlSubmissionPlatformOperator(state, checksCompletedDateTime, platformOperator))
    }
    sendEmail(SuccessfulXmlSubmissionUser(state, checksCompletedDateTime, platformOperator, subscriptionInfo))
  }

  def sendFailedBusinessRulesChecksEmails(state: Rejected, checksCompletedDateTime: String, platformOperator: PlatformOperator, subscriptionInfo: SubscriptionInfo)
                                             (implicit hc: HeaderCarrier): Future[Done] = {
    if (!matchingEmails(subscriptionInfo.primaryContact.email, platformOperator.primaryContactDetails.emailAddress)) {
      sendEmail(FailedXmlSubmissionPlatformOperator(checksCompletedDateTime, platformOperator))
    }
    sendEmail(FailedXmlSubmissionUser(state, checksCompletedDateTime, platformOperator, subscriptionInfo))
  }

  private def matchingEmails(primaryContactEmail: String, poEmail: String): Boolean =
    primaryContactEmail.trim.toLowerCase() == poEmail.trim.toLowerCase

  private def sendEmail(requestBuild: SendEmailRequest)(implicit hc: HeaderCarrier): Future[Done] =
    emailConnector.send(requestBuild)
}
