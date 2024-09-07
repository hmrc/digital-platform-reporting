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

package models.operator.responses

import models.operator.{AddressDetails, ContactDetails, NotificationType, TinDetails, TinType}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

import java.time.Instant
import java.time.format.DateTimeFormatter

class ViewPlatformOperatorResponseSpec extends AnyFreeSpec with Matchers {

  ".downstreamReads" - {
    
    "must read a response with minimal details" in {
      
      val json = Json.obj(
        "ViewPODetails" -> Json.obj(
          "ResponseCommon" -> Json.obj(
            "OriginatingSystem" -> "CADX",
            "TransmittingSystem" -> "EIS",
            "RequestType" -> "VIEW",
            "Regime" -> "DPRS"
          ),
          "ResponseDetails" -> Json.obj(
            "PlatformOperatorDetails" -> Json.arr(
              Json.obj(
                "SubscriptionId" -> "subscriptionId",
                "POID" -> "operatorId",
                "POName" -> "operatorName",
                "BusinessName" -> "foo",
                "AddressDetails" -> Json.obj(
                  "AddressLine1" -> "line1",
                  "PostalCode" -> "postCode"
                ),
                "PrimaryContactDetails" -> Json.obj(
                  "ContactName" -> "primaryContactName",
                  "EmailAddress" -> "primaryEmail"
                )
              )
            )
          )
        )
      )
      
      val expectedModel = ViewPlatformOperatorsResponse(platformOperators = Seq(
        PlatformOperator(
          operatorId = "operatorId",
          operatorName = "operatorName",
          tinDetails = Seq.empty,
          businessName = None,
          tradingName = None,
          primaryContactDetails = ContactDetails(None, "primaryContactName", "primaryEmail"),
          secondaryContactDetails = None,
          addressDetails = AddressDetails("line1", None, None, None, Some("postCode"), None),
          notifications = Seq.empty
        )
      ))
      
      json.as[ViewPlatformOperatorsResponse](ViewPlatformOperatorsResponse.downstreamReads) mustEqual expectedModel
    }
    
    "must read a response with full details" in {

      val json = Json.obj(
        "ViewPODetails" -> Json.obj(
          "ResponseCommon" -> Json.obj(
            "OriginatingSystem" -> "CADX",
            "TransmittingSystem" -> "EIS",
            "RequestType" -> "VIEW",
            "Regime" -> "DPRS"
          ),
          "ResponseDetails" -> Json.obj(
            "PlatformOperatorDetails" -> Json.arr(
              Json.obj(
                "SubscriptionId" -> "subscriptionId",
                "POID" -> "operatorId",
                "POName" -> "operatorName",
                "BusinessName" -> "foo",
                "TradingName" -> "tradingName",
                "AddressDetails" -> Json.obj(
                  "AddressLine1" -> "line1",
                  "AddressLine2" -> "line2",
                  "AddressLine3" -> "line3",
                  "AddressLine4" -> "line4",
                  "PostalCode" -> "postCode",
                  "CountryCode" -> "GB"
                ),
                "PrimaryContactDetails" -> Json.obj(
                  "PhoneNumber" -> "primaryPhoneNumber",
                  "ContactName" -> "primaryContactName",
                  "EmailAddress" -> "primaryEmail"
                ),
                "SecondaryContactDetails" -> Json.obj(
                  "PhoneNumber" -> "secondaryPhoneNumber",
                  "ContactName" -> "secondaryContactName",
                  "EmailAddress" -> "secondaryEmail"
                ),
                "NotificationsList" -> Json.arr(
                  Json.obj(
                    "NotificationType" -> "EPO",
                    "IsActiveSeller" -> true,
                    "IsDueDiligence" -> false,
                    "FirstNotifiedReportingPeriod" -> "2024",
                    "ReceivedDateTime" -> "2024-01-02T03:05:05.678Z"
                  ),
                  Json.obj(
                    "NotificationType" -> "RPO",
                    "IsActiveSeller" -> true,
                    "IsDueDiligence" -> false,
                    "FirstNotifiedReportingPeriod" -> "2024",
                    "ReceivedDateTime" -> "2024-01-03T03:05:05.678Z"
                  )
                ),
                "TINDetails" -> Json.arr(
                  Json.obj(
                    "TIN" -> "tin1",
                    "TINType" -> "CRN",
                    "IssuedBy" -> "issuedBy1"
                  ),
                  Json.obj(
                    "TIN" -> "tin2",
                    "TINType" -> "VRN",
                    "IssuedBy" -> "issuedBy2"
                  )
                )
              )
            )
          )
        )
      )

      val expectedNotification1Instant: Instant = DateTimeFormatter.ISO_INSTANT.parse("2024-01-02T03:05:05.678Z", Instant.from)
      val expectedNotification2Instant: Instant = DateTimeFormatter.ISO_INSTANT.parse("2024-01-03T03:05:05.678Z", Instant.from)

      val expectedModel = ViewPlatformOperatorsResponse(platformOperators = Seq(
        PlatformOperator(
          operatorId = "operatorId",
          operatorName = "operatorName",
          tinDetails = Seq(
            TinDetails("tin1", TinType.Crn, "issuedBy1"),
            TinDetails("tin2", TinType.Vrn, "issuedBy2")
          ),
          businessName = None,
          tradingName = Some("tradingName"),
          primaryContactDetails = ContactDetails(Some("primaryPhoneNumber"), "primaryContactName", "primaryEmail"),
          secondaryContactDetails = Some(ContactDetails(Some("secondaryPhoneNumber"), "secondaryContactName", "secondaryEmail")),
          addressDetails = AddressDetails("line1", Some("line2"), Some("line3"), Some("line4"), Some("postCode"), Some("GB")),
          notifications = Seq(
            NotificationDetails(NotificationType.Epo, true, false, "2024", expectedNotification1Instant),
            NotificationDetails(NotificationType.Rpo, true, false, "2024", expectedNotification2Instant)
          )
        )
      ))

      json.as[ViewPlatformOperatorsResponse](ViewPlatformOperatorsResponse.downstreamReads) mustEqual expectedModel
    }
  }
}
