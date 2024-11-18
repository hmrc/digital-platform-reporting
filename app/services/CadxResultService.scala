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

package services

import generated.{Accepted, FileAcceptanceStatus_EnumType, Rejected}
import models.audit.CadxSubmissionResponseEvent
import models.audit.CadxSubmissionResponseEvent.FileStatus.{Failed, Passed}
import models.submission.CadxValidationError.{FileError, RowError}
import models.submission.Submission.State
import models.submission.Submission.State.{Approved, Submitted}
import models.submission.{CadxValidationError, Submission}
import org.apache.pekko.stream.connectors.xml.{ParseEvent, StartElement}
import org.apache.pekko.stream.connectors.xml.scaladsl.XmlParsing
import org.apache.pekko.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Merge, Sink, Source}
import org.apache.pekko.stream.{FlowShape, Materializer, OverflowStrategy, SinkShape}
import org.apache.pekko.util.ByteString
import org.apache.pekko.{Done, NotUsed}
import repository.{CadxValidationErrorRepository, SubmissionRepository}
import services.CadxResultService.{InvalidResultStatusException, InvalidSubmissionStateException, SubmissionNotFoundException}
import uk.gov.hmrc.http.HeaderCarrier

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CadxResultService @Inject()(
                                   submissionRepository: SubmissionRepository,
                                   cadxValidationErrorRepository: CadxValidationErrorRepository,
                                   clock: Clock,
                                   auditService: AuditService
                                 )(using Materializer, ExecutionContext) {

  def processResult(response: Source[ByteString, ?]): Future[Done] =
    response.runWith(resultSink)

  private def getActualStatus: Flow[ParseEvent, FileAcceptanceStatus_EnumType, NotUsed] =
    XmlParsing.subtree(Seq("BREResponse", "requestDetail", "GenericStatusMessage", "ValidationResult", "Status"))
      .mapAsync(1) { element =>
        element.getTextContent match {
          case "Accepted" => Future.successful(Accepted)
          case "Rejected" => Future.successful(Rejected)
          case status =>
            Future.failed(InvalidResultStatusException(status))
        }
      }

  private def getImpliedStatus: Flow[ParseEvent, FileAcceptanceStatus_EnumType, NotUsed] =
    XmlParsing.subslice(Seq("BREResponse", "requestDetail", "GenericStatusMessage", "ValidationErrors"))
      .collect({ case e: StartElement if Set("FileError", "RecordError").contains(e.localName) => Rejected })

  private def getStatusSink = Sink.fromGraph(GraphDSL.createGraph(Sink.head[FileAcceptanceStatus_EnumType]) { implicit b => sink =>
    import GraphDSL.Implicits._

    val broadcast = b.add(Broadcast[ParseEvent](2))
    val actualStatus = b.add(getActualStatus)
    val impliedStatus = b.add(getImpliedStatus)
    val merge = b.add(Merge[FileAcceptanceStatus_EnumType](2))

    broadcast ~> actualStatus ~> merge
    broadcast ~> impliedStatus ~> merge ~> sink

    SinkShape(broadcast.in)
  })

  private def getSubmissionSink =
    XmlParsing.subtree(Seq("BREResponse", "requestCommon", "conversationID"))
      .map(_.getTextContent)
      .flatMapConcat { conversationId =>
        Source.futureSource {
          submissionRepository.getById(conversationId).flatMap { maybeSubmission =>
            maybeSubmission.map { submission =>
              submission.state match {
                case state: Submitted =>
                  Future.successful(Source.single((submission, state)))
                case _ =>
                  Future.failed(InvalidSubmissionStateException(submission._id, submission.state))
              }
            }.getOrElse(Future.failed(SubmissionNotFoundException(conversationId)))
          }
        }
      }.toMat(Sink.head)(Keep.right)

  private def fileErrorsFlow(submission: Submission) =
    XmlParsing.subtree(Seq("FileError")).map { element =>
      val code = element.getElementsByTagName("Code").item(0).getTextContent
      val details = Option(element.getElementsByTagName("Details").item(0)).map(_.getTextContent)
      FileError(submission._id, submission.dprsId, code, details, clock.instant())
    }

  private def recordErrorsFlow(submission: Submission) =
    XmlParsing.subtree(Seq("RecordError")).mapConcat { element =>
      val code = element.getElementsByTagName("Code").item(0).getTextContent
      val details = Option(element.getElementsByTagName("Details").item(0)).map(_.getTextContent)
      val docRefs = element.getElementsByTagName("DocRefIDInError")
      (0 until docRefs.getLength).map { i =>
        RowError(submission._id, submission.dprsId, code, details, docRefs.item(i).getTextContent, clock.instant())
      }
    }

  private def getErrorsFlow(submission: Submission) =
    Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits.*

      val filter = b.add(XmlParsing.subslice(Seq("BREResponse", "requestDetail", "GenericStatusMessage", "ValidationErrors")))
      val broadcast = b.add(Broadcast[ParseEvent](2))
      val merge = b.add(Merge[CadxValidationError](2))

      val fileErrors = b.add(fileErrorsFlow(submission))
      val recordErrors = b.add(recordErrorsFlow(submission))

      filter ~> broadcast
      broadcast ~> fileErrors ~> merge
      broadcast ~> recordErrors ~> merge

      FlowShape(filter.in, merge.out)
    })

  private def acceptedFlow(submission: Submission, state: Submitted): Flow[CadxValidationError, Done, NotUsed] =
    Flow.futureFlow {

      val auditEvent = CadxSubmissionResponseEvent(
        conversationId = submission._id,
        dprsId = submission.dprsId,
        operatorId = submission.operatorId,
        operatorName = submission.operatorName,
        fileName = state.fileName,
        fileStatus = Passed
      )

      given HeaderCarrier = HeaderCarrier()
      auditService.audit(auditEvent)

      submissionRepository.save(submission.copy(state = Approved(fileName = state.fileName, reportingPeriod = state.reportingPeriod), updated = clock.instant())).map { _ =>
        Flow.fromSinkAndSource(Sink.cancelled[CadxValidationError], Source.single[Done](Done))
      }
    }.mapMaterializedValue(_ => NotUsed)

  private def rejectedFlow(submission: Submission, state: Submitted): Flow[CadxValidationError, Done, NotUsed] =
    Flow.futureFlow {

      val auditEvent = CadxSubmissionResponseEvent(
        conversationId = submission._id,
        dprsId = submission.dprsId,
        operatorId = submission.operatorId,
        operatorName = submission.operatorName,
        fileName = state.fileName,
        fileStatus = Failed
      )

      given HeaderCarrier = HeaderCarrier()
      auditService.audit(auditEvent)

      submissionRepository.save(submission.copy(state = State.Rejected(fileName = state.fileName, reportingPeriod = state.reportingPeriod), updated = clock.instant())).map { _ =>
        Flow[CadxValidationError]
          .grouped(1000)
          .mapAsync(1)(cadxValidationErrorRepository.saveBatch)
          .via(Flow[Done].reduce((_, next) => next)) // maintains demand
      }
    }.mapMaterializedValue(_ => NotUsed)

  private def resultSink: Sink[ByteString, Future[Done]] = Sink.fromGraph(GraphDSL.createGraph(Sink.head[Done]) { implicit b => sink =>
    import GraphDSL.Implicits.*

    val (futureSubmission, submissionSink) = getSubmissionSink.preMaterialize()
    val submission = b.add(submissionSink)

    val (futureStatus, statusSink) = getStatusSink.preMaterialize()
    val status = b.add(statusSink)

    val errors = b.add {
      Flow[ParseEvent]
        // This buffer is required to allow the flow to prevent backpressuring
        // the initial XML parse to retrieve the submission which is required
        // for this flow to initiate
        .buffer(100, OverflowStrategy.backpressure)
        .via {
          Flow.futureFlow {
            futureSubmission.map { case (submission, _) =>
              getErrorsFlow(submission)
            }
          }
        }
    }

    val completion = b.add(Flow.futureFlow {
      for {
        (submission, state) <- futureSubmission
        status              <- futureStatus
      } yield if (status == Accepted) {
        acceptedFlow(submission, state)
      } else {
        rejectedFlow(submission, state)
      }
    })

    val parser = b.add(XmlParsing.parser())
    val broadcast = b.add(Broadcast[ParseEvent](3))

    parser ~> broadcast
    broadcast ~> submission
    broadcast ~> status
    broadcast ~> errors ~> completion ~> sink

    SinkShape(parser.in)
  })
}

object CadxResultService {

  final case class InvalidResultStatusException(status: String) extends Throwable {
    override def getMessage: String = s"Expected `Accepted` or `Rejected` but got `$status`"
  }

  final case class InvalidSubmissionStateException(submissionId: String, state: Submission.State) extends Throwable {
    override def getMessage: String = s"Expected submission $submissionId to be `Submitted` but was in `${state.getClass.getSimpleName}`"
  }

  final case class SubmissionNotFoundException(submissionId: String) extends Throwable {
    override def getMessage: String = s"Unable to find submission with ID $submissionId"
  }
}