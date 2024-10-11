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

package support.builders

import models.enrolment.PendingEnrolment

import java.time.Instant
import java.time.temporal.ChronoUnit

object PendingEnrolmentBuilder {

  val aPendingEnrolment: PendingEnrolment = PendingEnrolment(
    userId = "default-user-id",
    providerId = "default-provider-id",
    groupIdentifier = "default-group-identifier",
    verifierKey = "default-verifier-key",
    verifierValue = "default-verifier-value",
    dprsId = "default-dprs-id",
    created = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  )
}