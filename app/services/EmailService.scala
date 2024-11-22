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

import cats.data.EitherNec
import com.google.inject.Inject
import connectors.EmailConnector
import models.email.requests.FailedXmlSubmissionPlatformOperator.FailedXmlSubmissionPlatformOperatorTemplateId
import models.email.requests.FailedXmlSubmissionUser.FailedXmlSubmissionUserTemplateId
import models.email.requests.SuccessfulXmlSubmissionPlatformOperator.SuccessfulXmlSubmissionPlatformOperatorTemplateId
import models.email.requests.SuccessfulXmlSubmissionUser.SuccessfulXmlSubmissionUserTemplateId
import models.email.requests.{FailedXmlSubmissionPlatformOperator, FailedXmlSubmissionUser, SendEmailRequest, SuccessfulXmlSubmissionPlatformOperator, SuccessfulXmlSubmissionUser, ValidationError}
import models.operator.responses.PlatformOperator
import models.submission.Submission.State.{Approved, Rejected}
import models.subscription.responses.SubscriptionInfo
import org.apache.pekko.Done
import play.api.i18n.Lang.logger
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class EmailService @Inject()(emailConnector: EmailConnector) {

  def sendSuccessfulBusinessRulesChecksEmails(state: Approved, checksCompletedDateTime: String, platformOperator: PlatformOperator, subscriptionInfo: SubscriptionInfo)
                                   (implicit hc: HeaderCarrier): Future[Done] = {
    sendEmail(SuccessfulXmlSubmissionUser.build(state, checksCompletedDateTime, platformOperator, subscriptionInfo), SuccessfulXmlSubmissionUserTemplateId)
    sendEmail(SuccessfulXmlSubmissionPlatformOperator.build(state, checksCompletedDateTime, platformOperator), SuccessfulXmlSubmissionPlatformOperatorTemplateId)
  }

  def sendFailedBusinessRulesChecksEmails(state: Rejected, checksCompletedDateTime: String, platformOperator: PlatformOperator, subscriptionInfo: SubscriptionInfo)
                                             (implicit hc: HeaderCarrier): Future[Done] = {
    sendEmail(FailedXmlSubmissionUser.build(state, checksCompletedDateTime, platformOperator, subscriptionInfo), FailedXmlSubmissionUserTemplateId)
    sendEmail(FailedXmlSubmissionPlatformOperator.build(checksCompletedDateTime, platformOperator), FailedXmlSubmissionPlatformOperatorTemplateId)
  }

  private def sendEmail(requestBuild: EitherNec[ValidationError, SendEmailRequest], templateName: String)
                       (implicit hc: HeaderCarrier): Future[Done] = requestBuild.fold(
    errors => {
      logger.warn(s"Unable to send email ($templateName) :" +
        s"${errors.toChain.toList.mkString(", ")}")
      Future.successful(Done)
    },
    request => emailConnector.send(request)
  )
}
