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
import connectors.{DeliveredSubmissionConnector, SubmissionConnector}
import generated.*
import models.assumed.AssumingPlatformOperator
import models.operator.responses.PlatformOperator
import models.operator.{AddressDetails, TinDetails}
import models.submission.DeliveredSubmissionSortBy.SubmissionDate
import models.submission.SortOrder.Descending
import models.submission.SubmissionStatus.{Pending, Rejected, Success}
import models.submission.ViewSubmissionsRequest
import scalaxb.DataRecord
import services.AssumedReportingService.UnableToRetrieveExistingSubmissionException
import uk.gov.hmrc.http.HeaderCarrier

import java.time.format.DateTimeFormatter
import java.time.{Clock, LocalDateTime, Month, Year}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.{NodeSeq, Utility}

@Singleton
class AssumedReportingService @Inject()(
                                         uuidService: UuidService,
                                         clock: Clock,
                                         deliveredSubmissionConnector: DeliveredSubmissionConnector,
                                         submissionConnector: SubmissionConnector
                                       )(using ExecutionContext) {

  def createSubmission(dprsId: String, operator: PlatformOperator, assumingOperator: AssumingPlatformOperator, reportingPeriod: Year)(using HeaderCarrier): Future[AssumedReportingPayload] =
    getPreviousSubmission(dprsId, operator.operatorId, reportingPeriod).map { previousSubmission =>
      createSubmissionPayload(operator, assumingOperator, reportingPeriod, previousSubmission)
    }

  private def getPreviousSubmission(dprsId: String, operatorId: String, reportingPeriod: Year)(using HeaderCarrier): Future[Option[DPI_OECD]] = {
    for {
      caseId     <- OptionT(getPreviousCaseId(dprsId, operatorId, reportingPeriod))
      submission <- OptionT.liftF(submissionConnector.getManualAssumedReportingSubmission(caseId))
    } yield submission
  }.value

  private def getPreviousCaseId(dprsId: String, operatorId: String, reportingPeriod: Year)(using HeaderCarrier): Future[Option[String]] =
    deliveredSubmissionConnector.get(ViewSubmissionsRequest(
      subscriptionId = dprsId,
      assumedReporting = true,
      pageNumber = 1,
      sortBy = SubmissionDate,
      sortOrder = Descending,
      reportingPeriod = Some(reportingPeriod.getValue),
      operatorId = Some(operatorId),
      fileName = None,
      statuses = Seq(Pending, Rejected, Success) // TODO should we be ignoring certain statuses?
    )).map { deliveredSubmissions =>
      for {
        submissions <- deliveredSubmissions
        submission  <- submissions.submissions.headOption
      } yield submission.submissionCaseId
    }

  private def createSubmissionPayload(operator: PlatformOperator, assumingOperator: AssumingPlatformOperator, reportingPeriod: Year, previousSubmission: Option[DPI_OECD]): AssumedReportingPayload = {

    val messageRef = createMessageRefId(reportingPeriod, operator.operatorId)

    val submission = DPI_OECD(
      MessageSpec = MessageSpec_Type(
        SendingEntityIN = Some(operator.operatorId),
        TransmittingCountry = GB,
        ReceivingCountry = GB,
        MessageType = DPI,
        Warning = None,
        Contact = None,
        MessageRefId = messageRef,
        MessageTypeIndic = if (previousSubmission.isDefined) DPI402 else DPI401,
        ReportingPeriod = scalaxb.Helper.toCalendar(DateTimeFormatter.ISO_LOCAL_DATE.format(reportingPeriod.atMonth(Month.DECEMBER).atEndOfMonth())),
        Timestamp = scalaxb.Helper.toCalendar(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now(clock)))
      ),
      DPIBody = Seq(DPIBody_Type(
        PlatformOperator = createPlatformOperator(operator, messageRef, previousSubmission),
        OtherPlatformOperators = Some(createAssumingPlatformOperator(assumingOperator, messageRef, previousSubmission)),
        ReportableSeller = Seq.empty
      )),
      attributes = Map("@version" -> DataRecord("1.0"))
    )

    val body = Utility.trim(scalaxb.toXML(submission, Some("urn:oecd:ties:dpi:v1"), Some("DPI_OECD"), generated.defaultScope).head)

    AssumedReportingPayload(messageRef, body)
  }

  private def createPlatformOperator(operator: PlatformOperator, messageRef: String, previousSubmission: Option[DPI_OECD]): CorrectablePlatformOperator_Type = {

    val previousDocRefId = for {
      submission <- previousSubmission
      body       <- submission.DPIBody.headOption
    } yield body.PlatformOperator.DocSpec.DocRefId

    CorrectablePlatformOperator_Type(
      ResCountryCode = operator.addressDetails.countryCode.map(CountryCode_Type.fromString(_, generated.defaultScope)).toSeq,
      TIN = createTinDetails(operator.tinDetails),
      IN = Seq.empty, // Should this be the CRN / CHRN if it exists?
      VAT = None, // Should this be the VRN from the tin details if it exists?
      Name = Seq(NameOrganisation_Type(operator.operatorName)),
      PlatformBusinessName = Seq(Some(operator.operatorName), operator.businessName, operator.tradingName).flatten,
      Address = Seq(createOperatorAddress(operator.addressDetails)),
      Nexus = None,
      AssumedReporting = Some(true),
      DocSpec = DocSpec_Type(
        DocTypeIndic = createDocTypeIndicator(previousSubmission),
        DocRefId = createDocRefId(messageRef),
        CorrMessageRefId = None,
        CorrDocRefId = previousDocRefId
      )
    )
  }

  private def createAssumingPlatformOperator(assumingOperator: AssumingPlatformOperator, messageRef: String, previousSubmission: Option[DPI_OECD]): OtherPlatformOperators_Type = {

    val previousDocRefId = for {
      submission      <- previousSubmission
      body            <- submission.DPIBody.headOption
      otherOperators  <- body.OtherPlatformOperators

      if otherOperators.otherplatformoperators_typeoption.value.isInstanceOf[OtherPlatformOperators_TypeSequence1]

      otherOperator    = otherOperators.otherplatformoperators_typeoption.as[OtherPlatformOperators_TypeSequence1]
      assumingOperator = otherOperator.AssumingPlatformOperator
    } yield assumingOperator.DocSpec.DocRefId

    OtherPlatformOperators_Type(
      otherplatformoperators_typeoption = DataRecord(OtherPlatformOperators_TypeSequence1(
        AssumingPlatformOperator = CorrectableOtherRPO_Type(
          ResCountryCode = Seq(CountryCode_Type.fromString(assumingOperator.residentCountry, generated.defaultScope)),
          TIN = createTinDetails(assumingOperator.tinDetails),
          Name = NameOrganisation_Type(assumingOperator.name),
          Address = createAssumingOperatorAddress(assumingOperator.address, assumingOperator.registeredCountry),
          DocSpec = DocSpec_Type(
            DocTypeIndic = createDocTypeIndicator(previousSubmission),
            DocRefId = createDocRefId(messageRef),
            CorrMessageRefId = None,
            CorrDocRefId = previousDocRefId
          )
        )
      ))
    )
  }

  private def createDocTypeIndicator[A](previousSubmission: Option[A]): OECDDocTypeIndic_EnumType =
    if (previousSubmission.isDefined) OECD2 else OECD1

  private def createMessageRefId(reportingPeriod: Year, operatorId: String): String =
    s"GB${reportingPeriod}GB-$operatorId-${uuidService.generate().replaceAll("-", "")}"

  private def createDocRefId(messageRefId: String): String =
    s"${messageRefId}_${uuidService.generate().replaceAll("-", "")}"

  private def createTinDetails(tinDetails: Seq[TinDetails]): Seq[TIN_Type] = {
    if (tinDetails.nonEmpty) {
      tinDetails.map { details =>
        TIN_Type(
          value = details.tin,
          attributes = Map(
            "@issuedBy" -> DataRecord(CountryCode_Type.fromString(details.issuedBy, generated.defaultScope))
          )
        )
      }
    } else {
      Seq(TIN_Type("NOTIN", Map("@unknown" -> DataRecord(true))))
    }
  }

  private def createOperatorAddress(addressDetails: AddressDetails): Address_Type = {

    val addressFree = Seq(
      Some(addressDetails.line1),
      addressDetails.line2,
      addressDetails.line3,
      addressDetails.line4,
      addressDetails.postCode,
      addressDetails.countryCode
    ).flatten.mkString(", ")

    val innerAddress = addressDetails.line3.map { city =>
      DataRecord(Address_TypeSequence1(
        AddressFix = AddressFix_Type(
          Street = None, // Can't use line1 as that would be street + number in most cases
          BuildingIdentifier = None,
          SuiteIdentifier = None,
          FloorIdentifier = None,
          DistrictName = None,
          POB = None,
          PostCode = addressDetails.postCode,
          City = city,
          CountrySubentity = None,
        ),
        AddressFree = Some(addressFree)
      ))
    }.getOrElse {
      DataRecord(Some("urn:oecd:ties:dpi:v1"), Some("AddressFree"), addressFree)
    }

    Address_Type(
      CountryCode = addressDetails.countryCode.map(CountryCode_Type.fromString(_, generated.defaultScope)).get, // This is required but we don't require this in the address details
      address_typeoption = innerAddress,
      attributes = Map("@legalAddressType" -> DataRecord[OECDLegalAddressType_EnumType](OECD304))
    )
  }

  private def createAssumingOperatorAddress(address: String, registeredCountry: String): Address_Type =
    Address_Type(
      CountryCode = CountryCode_Type.fromString(registeredCountry, generated.defaultScope),
      address_typeoption = DataRecord(Some("urn:oecd:ties:dpi:v1"), Some("AddressFree"), address),
      attributes = Map("@legalAddressType" -> DataRecord[OECDLegalAddressType_EnumType](OECD304))
    )
}

object AssumedReportingService {

  final case class UnableToRetrieveExistingSubmissionException(dprsId: String, submissionCaseId: String) extends Throwable {
    override def getMessage: String = s"Unable to fetch submission for DPRS ID: $dprsId,  case ID: $submissionCaseId"
  }
}

final case class AssumedReportingPayload(messageRef: String, body: NodeSeq)
