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

package models.enrolment

import play.api.libs.json.Json
import support.SpecBase
import support.builders.AuthenticatedPendingEnrolmentRequestBuilder.anAuthenticatedPendingEnrolmentRequest

import java.time.{Instant, LocalDate, ZoneOffset}

class PendingEnrolmentSpec extends SpecBase {

  private val validJson = Json.obj(
    "userId" -> "some-user-id",
    "providerId" -> "some-provider-id",
    "groupIdentifier" -> "some-group-identifier",
    "verifierKey" -> "some-verifier-key",
    "verifierValue" -> "some-verifier-value",
    "dprsId" -> "some-dprs-id",
    "created" -> LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant
  )

  private val validModel = PendingEnrolment(
    userId = "some-user-id",
    providerId = "some-provider-id",
    groupIdentifier = "some-group-identifier",
    verifierKey = "some-verifier-key",
    verifierValue = "some-verifier-value",
    dprsId = "some-dprs-id",
    created = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant
  )

  "PendingEnrolment.format" - {
    "parse from json" in {
      validJson.as[PendingEnrolment] mustBe validModel
    }

    "parse to json" in {
      Json.toJson(validModel) mustBe validJson
    }
  }

  ".apply(...)" - {
    "must create PendingEnrolment from request specific data" in {
      val created = Instant.now()

      PendingEnrolment.apply(
        request = anAuthenticatedPendingEnrolmentRequest,
        created = created,
      ) mustBe PendingEnrolment(
        userId = anAuthenticatedPendingEnrolmentRequest.userId,
        providerId = anAuthenticatedPendingEnrolmentRequest.providerId,
        groupIdentifier = anAuthenticatedPendingEnrolmentRequest.groupIdentifier,
        verifierKey = anAuthenticatedPendingEnrolmentRequest.body.verifierKey,
        verifierValue = anAuthenticatedPendingEnrolmentRequest.body.verifierValue,
        dprsId = anAuthenticatedPendingEnrolmentRequest.body.dprsId,
        created = created,
      )
    }
  }
}
