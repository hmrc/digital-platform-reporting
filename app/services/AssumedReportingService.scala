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

import generated.*
import models.assumed.{AssumingOperatorAddress, AssumingPlatformOperator}
import models.operator.responses.PlatformOperator
import models.operator.{AddressDetails, TinDetails}
import scalaxb.DataRecord

import java.time.format.DateTimeFormatter
import java.time.{Clock, LocalDateTime, Month, Year}
import javax.inject.{Inject, Singleton}
import scala.xml.NodeSeq

@Singleton
class AssumedReportingService @Inject() (
                                          uuidService: UuidService,
                                          clock: Clock
                                        ) {

  def createSubmission(operator: PlatformOperator, assumingOperator: AssumingPlatformOperator, reportingPeriod: Year): AssumedReportingPayload = {
    
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
        MessageTypeIndic = DPI401, // TODO should this be DPI403?
        ReportingPeriod = scalaxb.Helper.toCalendar(DateTimeFormatter.ISO_LOCAL_DATE.format(reportingPeriod.atMonth(Month.DECEMBER).atEndOfMonth())),
        Timestamp = scalaxb.Helper.toCalendar(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now(clock)))
      ),
      DPIBody = Seq(DPIBody_Type(
        PlatformOperator = CorrectablePlatformOperator_Type(
          ResCountryCode = operator.addressDetails.countryCode.map(CountryCode_Type.fromString(_, generated.defaultScope)).toSeq,
          TIN = createTinDetails(operator.tinDetails),
          IN = Seq.empty, // Should this be the CRN / CHRN if it exists?
          VAT = None, // Should this be the VRN from the tin details if it exists?
          Name = Seq(NameOrganisation_Type(operator.operatorName)),
          PlatformBusinessName = operator.businessName.toSeq,
          Address = Seq(createOperatorAddress(operator.addressDetails)),
          Nexus = None,
          AssumedReporting = Some(true),
          DocSpec = DocSpec_Type(
            DocTypeIndic = OECD1,
            DocRefId = uuidService.generate(),
            CorrMessageRefId = None,
            CorrDocRefId = None
          )
        ),
        OtherPlatformOperators = Some(OtherPlatformOperators_Type(
          otherplatformoperators_typeoption = DataRecord(OtherPlatformOperators_TypeSequence1(
            AssumingPlatformOperator = CorrectableOtherRPO_Type(
              ResCountryCode = Seq(CountryCode_Type.fromString(assumingOperator.residentCountry, generated.defaultScope)),
              TIN = createTinDetails(assumingOperator.tinDetails),
              Name = NameOrganisation_Type(assumingOperator.name),
              Address = createAssumingOperatorAddress(assumingOperator.address),
              DocSpec = DocSpec_Type(
                DocTypeIndic = OECD1,
                DocRefId = uuidService.generate(),
                CorrMessageRefId = None,
                CorrDocRefId = None
              )
            )
          ))
        )),
        ReportableSeller = Seq.empty
      )),
      attributes = Map("@version" -> DataRecord("1"))
    )

    val body = scalaxb.toXML(submission, Some("urn:oecd:ties:dpi:v1"), Some("DPI_OECD"), generated.defaultScope)
    
    AssumedReportingPayload(messageRef, body)
  }

  private def createMessageRefId(reportingPeriod: Year, operatorId: String): String =
    s"GB${reportingPeriod}GB-$operatorId-${uuidService.generate()}"

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
      Seq(TIN_Type("", Map("@unknown" -> DataRecord(true))))
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
      attributes = Map.empty
    )
  }

  private def createAssumingOperatorAddress(address: AssumingOperatorAddress): Address_Type = {

    val addressFree = Seq(
      Some(address.line1),
      address.line2,
      Some(address.city),
      address.region,
      Some(address.postCode),
      Some(address.country)
    ).flatten.mkString(", ")

    Address_Type(
      CountryCode = CountryCode_Type.fromString(address.country, generated.defaultScope),
      address_typeoption = DataRecord(Address_TypeSequence1(
        AddressFix = AddressFix_Type(
          Street = None, // Can't use line 1 as that would be street + number in most cases
          BuildingIdentifier = None,
          SuiteIdentifier = None,
          FloorIdentifier = None,
          DistrictName = None,
          POB = None,
          PostCode = Some(address.postCode),
          City = address.city,
          CountrySubentity = address.region
        ),
        AddressFree = Some(addressFree)
      )),
      attributes = Map.empty
    )
  }
}

final case class AssumedReportingPayload(messageRef: String, body: NodeSeq)
