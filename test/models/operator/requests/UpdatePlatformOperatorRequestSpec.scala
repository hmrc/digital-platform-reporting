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

package models.operator.requests

import models.operator.*
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json

class UpdatePlatformOperatorRequestSpec extends AnyFreeSpec with Matchers {

  ".downstreamWrites" - {

    "must write the correct json with all details" in {

      val model = UpdatePlatformOperatorRequest(
        subscriptionId = "subscriptionId",
        operatorId = "operatorId",
        operatorName = "operatorName",
        tinDetails = Seq(
          TinDetails(
            tin = "tin",
            tinType = TinType.Crn,
            issuedBy = "issuedBy"
          )
        ),
        businessName = Some("businessName"),
        tradingName = Some("tradingName"),
        primaryContactDetails = ContactDetails(
          phoneNumber = Some("primaryPhoneNumber"),
          contactName = "primaryContactName",
          emailAddress = "primaryEmail"
        ),
        secondaryContactDetails = Some(
          ContactDetails(
            phoneNumber = Some("secondaryPhoneNumber"),
            contactName = "secondaryContactName",
            emailAddress = "secondaryEmail"
          )
        ),
        addressDetails = AddressDetails(
          line1 = "line1",
          line2 = None,
          line3 = None,
          line4 = None,
          postCode = Some("postCode"),
          countryCode = None
        ),
        notification = Some(Notification(
          notificationType = NotificationType.Epo,
          isActiveSeller = true,
          isDueDiligence = false,
          firstPeriod = "2024"
        ))
      )

      val expectedJson = Json.obj(
        "POManagement" -> Json.obj(
          "RequestCommon" -> Json.obj(
            "OriginatingSystem" -> "MDTP",
            "TransmittingSystem" -> "EIS",
            "RequestType" -> RequestType.Update,
            "Regime" -> "DPI"
          ),
          "RequestDetails" -> Json.obj(
            "SubscriptionID" -> "subscriptionId",
            "POID" -> "operatorId",
            "POName" -> "operatorName",
            "TINDetails" -> Json.arr(Json.obj(
              "TIN" -> "tin",
              "TINType" -> "CRN",
              "IssuedBy" -> "issuedBy"
            )),
            "BusinessName" -> "businessName",
            "TradingName" -> "tradingName",
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
            "AddressDetails" -> Json.obj(
              "AddressLine1" -> "line1",
              "PostalCode" -> "postCode"
            ),
            "NotificationDetails" -> Json.obj(
              "NotificationType" -> "EPO",
              "IsActiveSeller" -> true,
              "IsDueDiligence" -> false,
              "FirstNotifiedReportingPeriod" -> "2024"
            )
          )
        )
      )

      Json.toJsObject(model)(UpdatePlatformOperatorRequest.downstreamWrites) mustEqual expectedJson
    }

    "must write the correct json with minimal details" in {

      val model = UpdatePlatformOperatorRequest(
        subscriptionId = "subscriptionId",
        operatorId = "operatorId",
        operatorName = "operatorName",
        tinDetails = Seq.empty,
        businessName = None,
        tradingName = None,
        primaryContactDetails = ContactDetails(
          phoneNumber = None,
          contactName = "primaryContactName",
          emailAddress = "primaryEmail"
        ),
        secondaryContactDetails = None,
        addressDetails = AddressDetails(
          line1 = "line1",
          line2 = None,
          line3 = None,
          line4 = None,
          postCode = None,
          countryCode = None
        ),
        notification = None
      )

      val expectedJson = Json.obj(
        "POManagement" -> Json.obj(
          "RequestCommon" -> Json.obj(
            "OriginatingSystem" -> "MDTP",
            "TransmittingSystem" -> "EIS",
            "RequestType" -> RequestType.Update,
            "Regime" -> "DPI"
          ),
          "RequestDetails" -> Json.obj(
            "SubscriptionID" -> "subscriptionId",
            "POID" -> "operatorId",
            "POName" -> "operatorName",
            "TINDetails" -> Json.arr(),
            "PrimaryContactDetails" -> Json.obj(
              "ContactName" -> "primaryContactName",
              "EmailAddress" -> "primaryEmail"
            ),
            "AddressDetails" -> Json.obj(
              "AddressLine1" -> "line1"
            )
          )
        )
      )

      Json.toJsObject(model)(UpdatePlatformOperatorRequest.downstreamWrites) mustEqual expectedJson
    }
  }
}
