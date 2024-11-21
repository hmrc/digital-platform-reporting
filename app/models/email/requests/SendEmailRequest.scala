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

import cats.data.EitherNec
import cats.implicits._
import models.operator.responses.PlatformOperator
import models.submission.Submission
import models.submission.Submission.State.Submitted
import models.subscription.responses.SubscriptionInfo
import play.api.libs.json.{Json, OFormat}

sealed trait ValidationError
case object MissingBusinessName extends ValidationError

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
  val SuccessfulXmlSubmissionUserTemplateId: String = "dprs_successful_xml_submission_user"
  implicit val format: OFormat[SuccessfulXmlSubmissionUser] = Json.format[SuccessfulXmlSubmissionUser]

  def apply(email: String,
            name: String,
            businessName: String,
            platformOperatorId: String,
            checksCompletedDateTime: String,
            reportingPeriod: String,
            fileName: String): SuccessfulXmlSubmissionUser = SuccessfulXmlSubmissionUser(
    to = List(email),
    templateId = SuccessfulXmlSubmissionUserTemplateId,
    parameters = Map(
      "userPrimaryContactName" -> name,
      "poBusinessName" -> businessName,
      "poId" -> platformOperatorId,
      "checksCompletedDateTime" -> checksCompletedDateTime,
      "reportingPeriod" -> reportingPeriod,
      "fileName" -> fileName
    )
  )

  def build(submission: Submission, state: Submitted, platformOperator: PlatformOperator, subscriptionInfo: SubscriptionInfo): EitherNec[ValidationError, SuccessfulXmlSubmissionUser] = (
    Right(subscriptionInfo.primaryContact.email),
    platformOperator.businessName.toRightNec(MissingBusinessName)
  ).parMapN(SuccessfulXmlSubmissionUser(_, subscriptionInfo.primaryContactName, _, platformOperator.operatorId, submission.updated.toString, state.reportingPeriod.toString, state.fileName))

}

final case class SuccessfulXmlSubmissionPlatformOperator(to: List[String],
                                                         templateId: String,
                                                         parameters: Map[String, String]) extends SendEmailRequest

object SuccessfulXmlSubmissionPlatformOperator {
  val SuccessfulXmlSubmissionPlatformOperatorTemplateId: String = "dprs_successful_xml_submission_platform_operator"
  implicit val format: OFormat[SuccessfulXmlSubmissionPlatformOperator] = Json.format[SuccessfulXmlSubmissionPlatformOperator]

  def apply(email: String,
            platformOperatorContactName: String,
            platformOperatorBusinessName: String,
            platformOperatorId: String,
            checksCompletedDateTime: String,
            reportingPeriod: String,
            fileName: String): SuccessfulXmlSubmissionPlatformOperator = SuccessfulXmlSubmissionPlatformOperator(
    to = List(email),
    templateId = SuccessfulXmlSubmissionPlatformOperatorTemplateId,
    parameters = Map(
      "poPrimaryContactName" -> platformOperatorContactName,
      "poBusinessName" -> platformOperatorBusinessName,
      "poId" -> platformOperatorId,
      "checksCompletedDateTime" -> checksCompletedDateTime,
      "reportingPeriod" -> reportingPeriod,
      "fileName" -> fileName
    )
  )

  def build(submission: Submission, state: Submitted, platformOperator: PlatformOperator): EitherNec[ValidationError, SuccessfulXmlSubmissionPlatformOperator] = (
    Right(platformOperator.primaryContactDetails.emailAddress),
    platformOperator.businessName.toRightNec(MissingBusinessName)
  ).parMapN(SuccessfulXmlSubmissionPlatformOperator(_, platformOperator.primaryContactDetails.contactName, _, platformOperator.operatorId, submission.updated.toString, state.reportingPeriod.toString, state.fileName))

}

