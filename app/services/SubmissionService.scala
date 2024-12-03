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

import cats.data.OptionT
import connectors.{DownloadConnector, PlatformOperatorConnector, SubmissionConnector, SubscriptionConnector}
import models.assumed.AssumingPlatformOperator
import models.audit.AddSubmissionEvent
import models.audit.AddSubmissionEvent.DeliveryRoute.{Dct52A, Dprs0502}
import models.submission.Submission
import models.submission.Submission.State.{Submitted, Validated}
import models.subscription.responses.SubscriptionInfo
import models.subscription.{Contact, IndividualContact, OrganisationContact}
import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.util.ByteString
import play.api.Configuration
import repository.SubmissionRepository
import services.SubmissionService.{InvalidSubmissionStateException, NoPlatformOperatorException}
import uk.gov.hmrc.http.HeaderCarrier
import utils.DateTimeFormats
import utils.FileUtils.stripExtension

import java.io.ByteArrayInputStream
import java.time.{Clock, Year}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.{Elem, NodeSeq}

@Singleton
class SubmissionService @Inject() (
                                    subscriptionConnector: SubscriptionConnector,
                                    submissionConnector: SubmissionConnector,
                                    downloadConnector: DownloadConnector,
                                    clock: Clock,
                                    configuration: Configuration,
                                    sdesService: SdesService,
                                    uuidService: UuidService,
                                    platformOperatorConnector: PlatformOperatorConnector,
                                    assumedReportingService: AssumedReportingService,
                                    submissionRepository: SubmissionRepository,
                                    auditService: AuditService
                                  )(using Materializer, ExecutionContext) {

  private val sdesSubmissionThreshold: Long = configuration.get[Long]("sdes.size-threshold")

  def submit(submission: Submission)(using HeaderCarrier): Future[Done] =
    submission.state match {
      case state: Validated =>
        subscriptionConnector.get(submission.dprsId).flatMap { subscription =>

          val auditEvent = AddSubmissionEvent(
            conversationId = submission._id,
            dprsId = submission.dprsId,
            operatorId = submission.operatorId,
            operatorName = submission.operatorName,
            reportingPeriod = state.reportingPeriod,
            fileName = state.fileName,
            fileSize = state.size,
            deliveryRoute = if (state.size <= sdesSubmissionThreshold) Dprs0502 else Dct52A,
            processedAt = clock.instant()
          )

          auditService.audit(auditEvent)

          if (state.size <= sdesSubmissionThreshold) {
            submitDirect(submission, state, subscription)
          } else {
            sdesService.enqueueSubmission(submission._id, state, subscription)
          }
        }
      case _ =>
        Future.failed(InvalidSubmissionStateException(submission._id, submission.state))
    }

  def submitAssumedReporting(dprsId: String, operatorId: String, assumingOperator: AssumingPlatformOperator, reportingPeriod: Year)(using HeaderCarrier): Future[Submission] =
    for {
      subscription <- subscriptionConnector.get(dprsId)
      operator     <- OptionT(platformOperatorConnector.get(dprsId, operatorId)).getOrElseF(Future.failed(NoPlatformOperatorException(dprsId, operatorId)))
      payload      <- assumedReportingService.createSubmission(dprsId, operator, assumingOperator, reportingPeriod)
      fileName     =  s"${payload.messageRef}.xml"
      submission   =  Submission(
        _id = uuidService.generate(),
        submissionType = Submission.SubmissionType.ManualAssumedReport,
        dprsId = dprsId,
        operatorId = operatorId,
        operatorName = operator.operatorName,
        assumingOperatorName = Some(assumingOperator.name),
        state = Submitted(
          fileName = fileName,
          reportingPeriod = reportingPeriod
        ),
        created = clock.instant(),
        updated = clock.instant()
      )
      _                 <- submissionRepository.save(submission)
      submissionBody    =  addEnvelope(ByteString.fromString(payload.body.toString), submission._id, fileName, subscription, isManual = true)
      submissionSource  =  createSubmissionSource(submissionBody)
      _                 <- submissionConnector.submit(submission._id, submissionSource)
    } yield submission

  def submitAssumedReportingDeletion(dprsId: String, operatorId: String, reportingPeriod: Year)(using HeaderCarrier): Future[Submission] =
    for {
      subscription <- subscriptionConnector.get(dprsId)
      operator <- OptionT(platformOperatorConnector.get(dprsId, operatorId)).getOrElseF(Future.failed(NoPlatformOperatorException(dprsId, operatorId)))
      payload <- assumedReportingService.createDeleteSubmission(dprsId, operator.operatorId, reportingPeriod)
      fileName = s"${payload.messageRef}.xml"
      submission = Submission(
        _id = uuidService.generate(),
        submissionType = Submission.SubmissionType.ManualAssumedReport,
        dprsId = dprsId,
        operatorId = operatorId,
        operatorName = operator.operatorName,
        assumingOperatorName = None,
        state = Submitted(
          fileName = fileName,
          reportingPeriod = reportingPeriod
        ),
        created = clock.instant(),
        updated = clock.instant()
      )
      _                 <- submissionRepository.save(submission)
      submissionBody    =  addEnvelope(ByteString.fromString(payload.body.toString), submission._id, fileName, subscription, isManual = true)
      submissionSource  =  createSubmissionSource(submissionBody)
      _                 <- submissionConnector.submit(submission._id, submissionSource)
    } yield submission

  private def submitDirect(submission: Submission, state: Validated, subscription: SubscriptionInfo)(using HeaderCarrier): Future[Done] =
    for {
      source <- downloadConnector.download(state.downloadUrl)
      bytes <- source.runWith(Sink.fold(ByteString.empty)(_ ++ _))
      submissionBody = addEnvelope(bytes, submission._id, state.fileName, subscription, isManual = false)
      submissionSource = createSubmissionSource(submissionBody)
      _ <- submissionConnector.submit(submission._id, submissionSource)
    } yield Done

  private def createSubmissionSource(body: Elem): Source[ByteString, ?] =
    Source.single(ByteString.fromString(scala.xml.Utility.trim(body).toString))

  private def addEnvelope(
                           body: ByteString,
                           submissionId: String,
                           fileName: String,
                           subscription: SubscriptionInfo,
                           isManual: Boolean
                         ): Elem =
    <cadx:DPISubmissionRequest
      xmlns:dpi="urn:oecd:ties:dpi:v1"
      xmlns:stf="urn:oecd:ties:dpistf:v1"
      xmlns:cadx="http://www.hmrc.gsi.gov.uk/dpi/cadx"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://www.hmrc.gsi.gov.uk/dpi/cadx DPISubmissionRequest_v1.0.xsd">
        {requestCommon(submissionId)}
        {requestDetail(body)}
        {requestAdditionalDetail(fileName, subscription, isManual)}
    </cadx:DPISubmissionRequest>


  private def requestCommon(submissionId: String): Elem =
    <requestCommon>
      <receiptDate>{DateTimeFormats.ISO8601Formatter.format(clock.instant())}</receiptDate>
      <regime>DPI</regime>
      <conversationID>{submissionId}</conversationID>
      <schemaVersion>1.0.0</schemaVersion>
    </requestCommon>

  private def requestDetail(body: ByteString): Elem =
    <requestDetail>
      {scala.xml.XML.load(new ByteArrayInputStream(body.toArray))}
    </requestDetail>

  private def requestAdditionalDetail(fileName: String, subscription: SubscriptionInfo, isManual: Boolean): Elem =
    <requestAdditionalDetail>
      <fileName>{stripExtension(fileName)}</fileName>
      <subscriptionID>{subscription.id}</subscriptionID>
      {subscription.tradingName.map { tradingName =>
        <tradingName>{tradingName}</tradingName>
      }.orNull}
      <isManual>{isManual}</isManual>
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

  final case class InvalidSubmissionStateException(submissionId: String, state: Submission.State) extends Throwable {
    override def getMessage: String = s"Submission state was invalid for submission: $submissionId, expected Validated was: ${state.getClass.getSimpleName}"
  }

  final case class NoPlatformOperatorException(dprsId: String, operatorId: String) extends Throwable {
    override def getMessage: String = s"No operator found for operator id: $operatorId, on behalf of DPRS ID: $dprsId"
  }
}
