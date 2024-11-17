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
import models.audit.AddSubmissionEvent.DeliveryRoute.findValues
import models.audit.CadxSubmissionResponseEvent.FileStatus
import play.api.libs.functional.syntax.*
import play.api.libs.json.{OWrites, __}

import java.time.Instant

final case class CadxSubmissionResponseEvent(
                                              conversationId: String,
                                              dprsId: String,
                                              operatorId: String,
                                              operatorName: String,
                                              fileName: String,
                                              fileStatus: FileStatus
                                            ) extends AuditEvent {

  override val auditType: String = "AddSubmission"
}

object CadxSubmissionResponseEvent {

  sealed abstract class FileStatus(override val entryName: String) extends EnumEntry

  object FileStatus extends PlayEnum[FileStatus] {

    case object Passed extends FileStatus("passed")
    case object Failed extends FileStatus("failed")

    override val values: IndexedSeq[FileStatus] = findValues
  }

  given OWrites[CadxSubmissionResponseEvent] = (
    (__ \ "conversationId").write[String] and
    (__ \ "digitalPlatformReportingId").write[String] and
    (__ \ "platformOperatorId").write[String] and
    (__ \ "platformOperator").write[String] and
    (__ \ "fileName").write[String] and
    (__ \ "fileStatus").write[FileStatus]
  )(o => Tuple.fromProductTyped(o))
}
