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

import models.AuthenticatedPendingEnrolmentRequest
import models.enrolment.request.PendingEnrolmentRequest
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class PendingEnrolment(userId: String,
                                  providerId: String,
                                  groupIdentifier: String,
                                  verifierKey: String,
                                  verifierValue: String,
                                  dprsId: String,
                                  created: Instant)

object PendingEnrolment {

  lazy val mongoFormat: OFormat[PendingEnrolment] = {
    import MongoJavatimeFormats.Implicits.*
    Json.format
  }

  implicit val format: OFormat[PendingEnrolment] = Json.format[PendingEnrolment]

  def apply(request: AuthenticatedPendingEnrolmentRequest[PendingEnrolmentRequest],
            created: Instant): PendingEnrolment = {
    PendingEnrolment(
      userId = request.userId,
      providerId = request.providerId,
      groupIdentifier = request.groupIdentifier,
      verifierKey = request.body.verifierKey,
      verifierValue = request.body.verifierValue,
      dprsId = request.body.dprsId,
      created = created
    )
  }
}
