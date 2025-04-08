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

package models.submission

import models.{urlFormat, yearFormat, singletonOFormat}
import play.api.libs.json.*
import play.api.libs.functional.syntax.*
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.net.URL
import java.time.{Instant, Year}

final case class Submission(
                             _id: String,
                             submissionType: Submission.SubmissionType,
                             dprsId: String,
                             operatorId: String,
                             operatorName: String,
                             assumingOperatorName: Option[String],
                             state: Submission.State,
                             created: Instant,
                             updated: Instant
                           )

object Submission {

  sealed trait SubmissionType extends Product with Serializable

  object SubmissionType {

    case object Xml extends SubmissionType
    case object ManualAssumedReport extends SubmissionType

    given Format[SubmissionType] = {

      val reads: Reads[SubmissionType] =
        __.read[String].flatMap {
          case "Xml" => Reads.pure(Xml)
          case "ManualAssumedReport" => Reads.pure(ManualAssumedReport)
          case _ => Reads.failed("Invalid submission type")
        }

      val writes: Writes[SubmissionType] =
        Writes { submissionType =>
          JsString(submissionType.toString)
        }

      Format(reads, writes)
    }
  }
  
  sealed trait UploadFailureReason
  
  object UploadFailureReason {
    
    case object NotXml extends UploadFailureReason
    final case class SchemaValidationError(errors: Seq[SchemaValidationError.Error], moreErrors: Boolean) extends UploadFailureReason
    case object ManualAssumedReportExists extends UploadFailureReason
    case object PlatformOperatorIdMissing extends UploadFailureReason
    final case class PlatformOperatorIdMismatch(expectedId: String, actualId: String) extends UploadFailureReason
    case object ReportingPeriodInvalid extends UploadFailureReason
    final case class UpscanError(failureReason: UpscanFailureReason) extends UploadFailureReason
    case object EntityTooLarge extends UploadFailureReason
    case object EntityTooSmall extends UploadFailureReason
    case object InvalidArgument extends UploadFailureReason
    case object UnknownFailure extends UploadFailureReason
    case object InvalidFileNameExtension extends UploadFailureReason
    case object FileNameTooLong extends UploadFailureReason

    object SchemaValidationError {

      final case class Error(line: Int, col: Int, message: String)

      given OFormat[Error] = Json.format

      given OFormat[SchemaValidationError] = {

        val reads = (
          (__ \ "errors").readWithDefault(Seq.empty[Error]) ~
          (__ \ "moreErrors").readWithDefault(false)
        )(SchemaValidationError.apply)

        val writes = Json.writes[SchemaValidationError]

        OFormat(reads, writes)
      }
    }

    private given OFormat[NotXml.type] = singletonOFormat(NotXml)
    private given OFormat[ManualAssumedReportExists.type] = singletonOFormat(ManualAssumedReportExists)
    private given OFormat[PlatformOperatorIdMissing.type] = singletonOFormat(PlatformOperatorIdMissing)
    private given OFormat[PlatformOperatorIdMismatch] = Json.format
    private given OFormat[ReportingPeriodInvalid.type] = singletonOFormat(ReportingPeriodInvalid)
    private given OFormat[UpscanError] = Json.format
    private given OFormat[EntityTooSmall.type] = singletonOFormat(EntityTooSmall)
    private given OFormat[EntityTooLarge.type] = singletonOFormat(EntityTooLarge)
    private given OFormat[InvalidArgument.type] = singletonOFormat(InvalidArgument)
    private given OFormat[UnknownFailure.type] = singletonOFormat(UnknownFailure)
    private given OFormat[InvalidFileNameExtension.type] = singletonOFormat(InvalidFileNameExtension)
    private given OFormat[FileNameTooLong.type] = singletonOFormat(FileNameTooLong)

    private given JsonConfiguration = JsonConfiguration(
      discriminator = "type",
      typeNaming = _.split("\\.").last
    )
    
    given OFormat[UploadFailureReason] = Json.format
  }

  sealed trait State extends Product with Serializable

  object State {

    case object Ready extends State
    case object Uploading extends State
    final case class UploadFailed(reason: UploadFailureReason, fileName: Option[String]) extends State
    final case class Validated(downloadUrl: URL, reportingPeriod: Year, fileName: String, checksum: String, size: Long) extends State
    final case class Submitted(fileName: String, reportingPeriod: Year, size: Long) extends State
    final case class Approved(fileName: String, reportingPeriod: Year) extends State
    final case class Rejected(fileName: String, reportingPeriod: Year) extends State

    private val submittedReads: Reads[Submitted] = (
      (__ \ "fileName").read[String] ~
        (__ \ "reportingPeriod").read[Year] ~
        (__ \ "size").readWithDefault[Long](0L)
      )(Submitted.apply)

    private val submittedWrites: OWrites[Submitted] = Json.writes[Submitted]

    private given OFormat[Ready.type] = singletonOFormat(Ready)
    private given OFormat[UploadFailed] = Json.format
    private given OFormat[Uploading.type] = singletonOFormat(Uploading)
    private given OFormat[Validated] = Json.format
    private given OFormat[Submitted] = OFormat[Submitted](submittedReads, submittedWrites)
    private given OFormat[Approved] = Json.format
    private given OFormat[Rejected] = Json.format


    private given JsonConfiguration = JsonConfiguration(
      discriminator = "type",
      typeNaming = _.split("\\.").last
    )

    given OFormat[State] = Json.format

    lazy val mongoFormat: OFormat[State] = {
      import MongoJavatimeFormats.Implicits.*
      Json.format
    }
  }

  given OFormat[Submission] = format

  lazy val mongoFormat: OFormat[Submission] = {
    import MongoJavatimeFormats.Implicits.given
    given OFormat[State] = State.mongoFormat
    format
  }

  private def format(using Format[Instant], OFormat[State]): OFormat[Submission] = {

    val readSubmissionType: Reads[SubmissionType] =
      (__ \ "submissionType").readNullable[SubmissionType].flatMap {
        _.map(Reads.pure).getOrElse {
          (__ \ "assumingOperatorName").readNullable[String].map {
            case Some(_) => SubmissionType.ManualAssumedReport
            case _       => SubmissionType.Xml
          }
        }
      }

    val reads: Reads[Submission] = (
      (__ \ "_id").read[String] ~
      __.read(readSubmissionType) ~
      (__ \ "dprsId").read[String] ~
      (__ \ "operatorId").read[String] ~
      (__ \ "operatorName").read[String] ~
      (__ \ "assumingOperatorName").readNullable[String] ~
      (__ \ "state").read[State] ~
      (__ \ "created").read[Instant] ~
      (__ \ "updated").read[Instant]
    )(Submission.apply)

    val writes: OWrites[Submission] = Json.writes[Submission]

    OFormat(reads, writes)
  }
}