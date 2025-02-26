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
import models.submission.Submission.State.{Approved, Rejected}
import models.subscription.responses.SubscriptionInfo
import play.api.libs.json.{Json, OFormat}

sealed trait SendEmailRequest {
  def to: List[String]
  def templateId: String
  def parameters: Map[String, String]
}

object SendEmailRequest {
  implicit val format: OFormat[SendEmailRequest] = Json.format[SendEmailRequest]
}

final case class SuccessfulXmlSubmissionUser(to: List[String],
                                             templateId: String,
                                             parameters: Map[String, String]) extends SendEmailRequest

object SuccessfulXmlSubmissionUser {
  implicit val format: OFormat[SuccessfulXmlSubmissionUser] = Json.format[SuccessfulXmlSubmissionUser]
  private val templateId = "dprs_successful_xml_submission_user"

  def apply(state: Approved, checksCompletedDateTime: String, platformOperator: PlatformOperator, subscriptionInfo: SubscriptionInfo): SuccessfulXmlSubmissionUser = SuccessfulXmlSubmissionUser(
    to = List(subscriptionInfo.primaryContact.email),
    templateId = templateId,
    parameters = Map(
      "userPrimaryContactName" -> subscriptionInfo.primaryContactName,
      "poBusinessName" -> platformOperator.operatorName,
      "poId" -> platformOperator.operatorId,
      "checksCompletedDateTime" -> checksCompletedDateTime,
      "reportingPeriod" -> state.reportingPeriod.toString,
      "fileName" -> state.fileName
    )
  )

}

final case class SuccessfulXmlSubmissionPlatformOperator(to: List[String],
                                                         templateId: String,
                                                         parameters: Map[String, String]) extends SendEmailRequest

object SuccessfulXmlSubmissionPlatformOperator {
  implicit val format: OFormat[SuccessfulXmlSubmissionPlatformOperator] = Json.format[SuccessfulXmlSubmissionPlatformOperator]
  private val templateId = "dprs_successful_xml_submission_platform_operator"

  def apply(state: Approved, checksCompletedDateTime: String, platformOperator: PlatformOperator): SuccessfulXmlSubmissionPlatformOperator = SuccessfulXmlSubmissionPlatformOperator(
    to = List(platformOperator.primaryContactDetails.emailAddress),
    templateId = templateId,
    parameters = Map(
      "poPrimaryContactName" -> platformOperator.primaryContactDetails.contactName,
      "poBusinessName" -> platformOperator.operatorName,
      "poId" -> platformOperator.operatorId,
      "checksCompletedDateTime" -> checksCompletedDateTime,
      "reportingPeriod" -> state.reportingPeriod.toString,
      "fileName" -> state.fileName
    )
  )

}

final case class FailedXmlSubmissionUser(to: List[String],
                                         templateId: String,
                                         parameters: Map[String, String]) extends SendEmailRequest

object FailedXmlSubmissionUser {
  implicit val format: OFormat[FailedXmlSubmissionUser] = Json.format[FailedXmlSubmissionUser]
  private val templateId = "dprs_failed_xml_submission_user"

  def apply(state: Rejected,
            checksCompletedDateTime: String,
            platformOperator: PlatformOperator,
            subscriptionInfo: SubscriptionInfo): FailedXmlSubmissionUser = FailedXmlSubmissionUser(
    to = List(subscriptionInfo.primaryContact.email),
    templateId = templateId,
    parameters = Map(
      "userPrimaryContactName" -> subscriptionInfo.primaryContactName,
      "poBusinessName" -> platformOperator.operatorName,
      "checksCompletedDateTime" -> checksCompletedDateTime,
      "fileName" -> state.fileName
    )
  )
}

final case class FailedXmlSubmissionPlatformOperator(to: List[String],
                                                     templateId: String,
                                                     parameters: Map[String, String]) extends SendEmailRequest

object FailedXmlSubmissionPlatformOperator {
  implicit val format: OFormat[FailedXmlSubmissionPlatformOperator] = Json.format[FailedXmlSubmissionPlatformOperator]
  private val templateId = "dprs_failed_xml_submission_platform_operator"

  def apply(checksCompletedDateTime: String,
            platformOperator: PlatformOperator): FailedXmlSubmissionPlatformOperator = FailedXmlSubmissionPlatformOperator(
    to = List(platformOperator.primaryContactDetails.emailAddress),
    templateId = templateId,
    parameters = Map(
      "poPrimaryContactName" -> platformOperator.primaryContactDetails.contactName,
      "poBusinessName" -> platformOperator.operatorName,
      "checksCompletedDateTime" -> checksCompletedDateTime
    )
  )
}

