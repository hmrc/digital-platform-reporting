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

import models.audit.FileUploadedEvent.FileUploadOutcome
import models.singletonOFormat
import models.submission.Submission.UploadFailureReason
import play.api.libs.functional.syntax.*
import play.api.libs.json.*

final case class FileUploadedEvent(
                                    conversationId: String,
                                    dprsId: String,
                                    operatorId: String,
                                    operatorName: String,
                                    fileName: Option[String],
                                    outcome: FileUploadOutcome
                                  ) extends AuditEvent {

  override val auditType: String = "FileUploaded"
}

object FileUploadedEvent {

  sealed trait FileUploadOutcome

  object FileUploadOutcome {

    case object Accepted extends FileUploadOutcome
    final case class Rejected(error: UploadFailureReason) extends FileUploadOutcome

    private given OWrites[Accepted.type] = singletonOFormat(Accepted)

    private given OWrites[Rejected] = OWrites { rejected =>
      rejected.error match {
        case UploadFailureReason.UpscanError(failureReason) =>
          Json.obj(
            "fileErrorCode" -> "UpscanError",
            "fileErrorReason" -> failureReason.getClass.getSimpleName.stripSuffix("$")
          )
        case _ =>
          Json.obj(
            "fileErrorCode" -> rejected.error.getClass.getSimpleName.stripSuffix("$"),
            "fileErrorReason" -> rejected.error.getClass.getSimpleName.stripSuffix("$")
          )
      }
    }

    private given JsonConfiguration = JsonConfiguration(
      discriminator = "status",
      typeNaming = _.split("\\.").last
    )

    given OWrites[FileUploadOutcome] = Json.writes
  }

  given OWrites[FileUploadedEvent] = (
    (__ \ "conversationId").write[String] and
    (__ \ "digitalPlatformReportingId").write[String] and
    (__ \ "platformOperatorId").write[String] and
    (__ \ "platformOperator").write[String] and
    (__ \ "fileName").writeNullable[String] and
    (__ \ "outcome").write[FileUploadOutcome]
  )(o => Tuple.fromProductTyped(o))
}
