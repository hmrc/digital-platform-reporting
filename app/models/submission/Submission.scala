package models.submission

import play.api.libs.json.{Json, JsonConfiguration, OFormat, OWrites, Reads}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class Submission(
                             _id: String,
                             dprsId: String,
                             platformOperatorId: String,
                             state: Submission.State,
                             created: Instant,
                             updated: Instant
                           )

object Submission {

  sealed trait State

  object State {

    case object Ready extends State
    case object Uploading extends State
    final case class UploadFailed(reason: String) extends State
    case object Validated extends State
    case object Submitted extends State
    case object Approved extends State
    final case class Rejected(reason: String) extends State

    private def singletonOFormat[A](a: A): OFormat[A] =
      OFormat(Reads.pure(a), OWrites[A](_ => Json.obj()))

    private implicit lazy val readyFormat: OFormat[Ready.type] = singletonOFormat(Ready)
    private implicit lazy val uploadFailedFormat: OFormat[UploadFailed] = Json.format
    private implicit lazy val uploadingFormat: OFormat[Uploading.type] = singletonOFormat(Uploading)
    private implicit lazy val validatedFormat: OFormat[Validated.type] = singletonOFormat(Validated)
    private implicit lazy val submittedFormat: OFormat[Submitted.type] = singletonOFormat(Submitted)
    private implicit lazy val approvedFormat: OFormat[Approved.type] = singletonOFormat(Approved)
    private implicit lazy val rejectedFormat: OFormat[Rejected] = Json.format

    private implicit val jsonConfig: JsonConfiguration = JsonConfiguration(
      discriminator = "type",
      typeNaming = _.split("\\.").last
    )

    implicit lazy val format: OFormat[State] = Json.format

    lazy val mongoFormat: OFormat[State] = {
      import MongoJavatimeFormats.Implicits._
      Json.format
    }
  }

  implicit lazy val format: OFormat[Submission] = Json.format

  lazy val mongoFormat: OFormat[Submission] = {
    import MongoJavatimeFormats.Implicits._
    implicit val stateFormat: OFormat[State] = State.mongoFormat
    Json.format
  }
}