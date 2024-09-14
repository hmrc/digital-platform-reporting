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

import connectors.{DownloadConnector, SubmissionConnector, SubscriptionConnector}
import models.submission.Submission
import models.submission.Submission.State.Validated
import models.subscription.responses.SubscriptionInfo
import models.subscription.{Contact, IndividualContact, OrganisationContact}
import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import services.SubmissionService.InvalidSubmissionStateException
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateTimeFormats

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.{Elem, NodeSeq}

@Singleton
class SubmissionService @Inject() (
                                    subscriptionConnector: SubscriptionConnector,
                                    submissionConnector: SubmissionConnector,
                                    downloadConnector: DownloadConnector,
                                    clock: Clock
                                  )(using Materializer, ExecutionContext) {

  def submit(submission: Submission)(using HeaderCarrier): Future[Done] =
    submission.state match {
      case state: Validated =>
        for {
          subscription      <- subscriptionConnector.get(submission.dprsId)
          source            <- downloadConnector.download(state.downloadUrl)
          bytes             <- source.runWith(Sink.fold(ByteString.empty)(_ ++ _))
          submissionBody    = addEnvelope(bytes, submission._id, state, subscription)
          submissionSource  = createSubmissionSource(submissionBody)
          _                 <- submissionConnector.submit(submission._id, submissionSource)
        } yield Done
      case _ =>
        Future.failed(InvalidSubmissionStateException(submission._id))
    }

  private def createSubmissionSource(body: Elem): Source[ByteString, _] =
    Source.single(ByteString.fromString(scala.xml.Utility.trim(body).toString))

  private def addEnvelope(
                           body: ByteString,
                           submissionId: String,
                           state: Validated,
                           subscription: SubscriptionInfo
                         ): Elem =
    <cadx:DPISubmissionRequest
      xmlns:dpi="urn:oecd:ties:dpi:v1"
      xmlns:stf="urn:oecd:ties:dpistf:v1"
      xmlns:cadx="http://www.hmrc.gsi.gov.uk/dpi/cadx"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://www.hmrc.gsi.gov.uk/dpi/cadx DPISubmissionRequest_v1.0.xsd">
        {requestCommon(submissionId)}
        {requestDetail(body)}
        {requestAdditionalDetail(state, subscription)}
    </cadx:DPISubmissionRequest>

  private def requestCommon(submissionId: String): Elem =
    <requestCommon>
      <receiptDate>{DateTimeFormats.ISO8601Formatter.format(clock.instant())}</receiptDate>
      <regime>DPI</regime>
      <conversationID>{submissionId}</conversationID>
      <schemaVersion>1.0</schemaVersion>
    </requestCommon>

  private def requestDetail(body: ByteString): Elem =
    <requestDetail>
      {scala.xml.XML.loadString(body.utf8String)}
    </requestDetail>

  private def requestAdditionalDetail(state: Validated, subscription: SubscriptionInfo): Elem =
    <requestAdditionalDetail>
      <fileName>{state.fileName}</fileName>
      <subscriptionID>{subscription.id}</subscriptionID>
      {subscription.tradingName.map { tradingName =>
        <tradingName>{tradingName}</tradingName>
      }.orNull}
      <isManual>false</isManual>
      <isGBUser>{subscription.gbUser.toString}</isGBUser>
      <primaryContact>{contact(subscription.primaryContact)}</primaryContact>
      {subscription.secondaryContact.map { secondaryContact =>
        <secondaryContact>{contact(secondaryContact)}</secondaryContact>
      }.orNull}
    </requestAdditionalDetail>

  private def contact(contact: Contact): NodeSeq =
    <root>
      {contact.phone.map { phone =>
        <phoneNumber>{phone}</phoneNumber>
      }.orNull}
      <emailAddress>{contact.email}</emailAddress>
      {contact match {
        case contact: IndividualContact =>
          <individualDetails>
            <firstName>
              {contact.individual.firstName}
            </firstName>
            <lastName>
              {contact.individual.lastName}
            </lastName>
          </individualDetails>
        case contact: OrganisationContact =>
          <organisationDetails>
            <organisationName>
              {contact.organisation.name}
            </organisationName>
          </organisationDetails>
      }}
    </root>.child
}

object SubmissionService {

  final case class InvalidSubmissionStateException(submissionId: String) extends Throwable
}
