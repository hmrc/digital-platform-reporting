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

package models.audit

import enumeratum.{EnumEntry, PlayEnum}
import models.audit.AddSubmissionEvent.DeliveryRoute
import play.api.libs.functional.syntax.*
import play.api.libs.json.{Json, OFormat, OWrites, __}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.{Instant, Year}
import models.yearFormat

final case class AddSubmissionEvent(
                                     conversationId: String,
                                     dprsId: String,
                                     operatorId: String,
                                     operatorName: String,
                                     reportingPeriod: Year,
                                     fileName: String,
                                     fileSize: Long,
                                     deliveryRoute: DeliveryRoute,
                                     processedAt: Instant,
                                     isSent: Boolean
                                   ) extends AuditEvent {

  override val auditType: String = "AddSubmission"
}

object AddSubmissionEvent {

  lazy val mongoFormat: OFormat[AddSubmissionEvent] = {
    import MongoJavatimeFormats.Implicits.*
    Json.format
  }

  sealed abstract class DeliveryRoute(override val entryName: String) extends EnumEntry

  object DeliveryRoute extends PlayEnum[DeliveryRoute] {

    case object Dprs0502 extends DeliveryRoute("DPRS0502")
    case object Dct52A extends DeliveryRoute("DCT52A")

    override val values: IndexedSeq[DeliveryRoute] = findValues
  }

  given OWrites[AddSubmissionEvent] = (
    (__ \ "conversationId").write[String] and
    (__ \ "digitalPlatformReportingId").write[String] and
    (__ \ "platformOperatorId").write[String] and
    (__ \ "platformOperator").write[String] and
    (__ \ "reportingPeriod").write[String].contramap[Year](_.toString) and
    (__ \ "fileName").write[String] and
    (__ \ "fileSizeInBytes").write[Long] and
    (__ \ "outcome" \ "deliveryRoute").write[DeliveryRoute] and
    (__ \ "outcome" \ "processedAt").write[Instant] and
    (__ \ "outcome" \ "isSent").write[Boolean]
  )(o => Tuple.fromProductTyped(o))
}
