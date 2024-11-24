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

  def apply(email: String,
            primaryContactName: String,
            platformOperatorName: String,
            platformOperatorId: String,
            checksCompletedDateTime: String,
            reportingPeriod: String,
            fileName: String): SuccessfulXmlSubmissionUser = SuccessfulXmlSubmissionUser(
    to = List(email),
    templateId = "dprs_successful_xml_submission_user",
    parameters = Map(
      "userPrimaryContactName" -> primaryContactName,
      "poBusinessName" -> platformOperatorName,
      "poId" -> platformOperatorId,
      "checksCompletedDateTime" -> checksCompletedDateTime,
      "reportingPeriod" -> reportingPeriod,
      "fileName" -> fileName
    )
  )

  def build(state: Approved, checksCompletedDateTime: String, platformOperator: PlatformOperator, subscriptionInfo: SubscriptionInfo): SuccessfulXmlSubmissionUser =
    SuccessfulXmlSubmissionUser(
      subscriptionInfo.primaryContact.email,
      subscriptionInfo.primaryContactName,
      platformOperator.operatorName,
      platformOperator.operatorId,
      checksCompletedDateTime,
      state.reportingPeriod.toString,
      state.fileName)

}

final case class SuccessfulXmlSubmissionPlatformOperator(to: List[String],
                                                         templateId: String,
                                                         parameters: Map[String, String]) extends SendEmailRequest

object SuccessfulXmlSubmissionPlatformOperator {
  implicit val format: OFormat[SuccessfulXmlSubmissionPlatformOperator] = Json.format[SuccessfulXmlSubmissionPlatformOperator]

  def apply(email: String,
            platformOperatorContactName: String,
            platformOperatorName: String,
            platformOperatorId: String,
            checksCompletedDateTime: String,
            reportingPeriod: String,
            fileName: String): SuccessfulXmlSubmissionPlatformOperator = SuccessfulXmlSubmissionPlatformOperator(
    to = List(email),
    templateId = "dprs_successful_xml_submission_platform_operator",
    parameters = Map(
      "poPrimaryContactName" -> platformOperatorContactName,
      "poBusinessName" -> platformOperatorName,
      "poId" -> platformOperatorId,
      "checksCompletedDateTime" -> checksCompletedDateTime,
      "reportingPeriod" -> reportingPeriod,
      "fileName" -> fileName
    )
  )

  def build(state: Approved, checksCompletedDateTime: String, platformOperator: PlatformOperator): SuccessfulXmlSubmissionPlatformOperator =
  SuccessfulXmlSubmissionPlatformOperator(
    platformOperator.primaryContactDetails.emailAddress,
    platformOperator.primaryContactDetails.contactName,
    platformOperator.operatorName,
    platformOperator.operatorId,
    checksCompletedDateTime,
    state.reportingPeriod.toString,
    state.fileName)

}

final case class FailedXmlSubmissionUser(to: List[String],
                                             templateId: String,
                                             parameters: Map[String, String]) extends SendEmailRequest

object FailedXmlSubmissionUser {
  implicit val format: OFormat[FailedXmlSubmissionUser] = Json.format[FailedXmlSubmissionUser]

  def apply(email: String,
            primaryContactName: String,
            platformOperatorName: String,
            checksCompletedDateTime: String,
            fileName: String): FailedXmlSubmissionUser = FailedXmlSubmissionUser(
    to = List(email),
    templateId = "dprs_failed_xml_submission_user",
    parameters = Map(
      "userPrimaryContactName" -> primaryContactName,
      "poBusinessName" -> platformOperatorName,
      "checksCompletedDateTime" -> checksCompletedDateTime,
      "fileName" -> fileName
    )
  )

  def build(state: Rejected,
            checksCompletedDateTime: String,
            platformOperator: PlatformOperator,
            subscriptionInfo: SubscriptionInfo): FailedXmlSubmissionUser = {
    FailedXmlSubmissionUser(
      subscriptionInfo.primaryContact.email,
      subscriptionInfo.primaryContactName,
      platformOperator.operatorName,
      checksCompletedDateTime,
      state.fileName)
  }

}

final case class FailedXmlSubmissionPlatformOperator(to: List[String],
                                                         templateId: String,
                                                         parameters: Map[String, String]) extends SendEmailRequest

object FailedXmlSubmissionPlatformOperator {
  implicit val format: OFormat[FailedXmlSubmissionPlatformOperator] = Json.format[FailedXmlSubmissionPlatformOperator]

  def apply(email: String,
            platformOperatorContactName: String,
            platformOperatorName: String,
            checksCompletedDateTime: String): FailedXmlSubmissionPlatformOperator = FailedXmlSubmissionPlatformOperator(
    to = List(email),
    templateId = "dprs_failed_xml_submission_platform_operator",
    parameters = Map(
      "poPrimaryContactName" -> platformOperatorContactName,
      "poBusinessName" -> platformOperatorName,
      "checksCompletedDateTime" -> checksCompletedDateTime
    )
  )

  def build(checksCompletedDateTime: String,
            platformOperator: PlatformOperator): FailedXmlSubmissionPlatformOperator =
  FailedXmlSubmissionPlatformOperator(
      platformOperator.primaryContactDetails.emailAddress,
      platformOperator.primaryContactDetails.contactName,
      platformOperator.operatorName,
      checksCompletedDateTime)

}

