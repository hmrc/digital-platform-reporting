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

package controllers

import cats.data.{EitherT, OptionT}
import cats.implicits.given
import generated.{BREResponse_Type, Generated_BREResponse_TypeFormat, ValidationErrors_Type}
import logging.Logging
import models.submission.Submission.State
import models.submission.Submission.State.{Approved, Rejected, Submitted}
import models.submission.{CadxValidationError, Submission}
import org.apache.pekko.Done
import play.api.Configuration
import play.api.mvc.{Action, ControllerComponents, Request, Result}
import repository.{CadxValidationErrorRepository, SubmissionRepository}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

@Singleton
class SubmissionResultCallbackController @Inject() (
                                                     cc: ControllerComponents,
                                                     submissionRepository: SubmissionRepository,
                                                     cadxValidationErrorRepository: CadxValidationErrorRepository,
                                                     clock: Clock,
                                                     configuration: Configuration
                                                   )(using ExecutionContext) extends BackendController(cc) with Logging {

  private val expectedBearerToken: String = configuration.get[String]("cadx.incoming-bearer-token")
  private val expectedAuthHeader: String = s"Bearer $expectedBearerToken"

  // TODO add authorisation
  def callback(): Action[NodeSeq] = Action.async(parse.xml) { implicit request =>

    val result = for {
      _               <- checkAuth
      correlationId   <- validateCorrelationId
      conversationId  <- validateConversationId
      breResponse     <- parseBody(correlationId)
      submission      <- getSubmission(conversationId)
      _               <- handleBreResponse(breResponse, submission)
    } yield NoContent

    result.merge
  }

  private def parseBody(correlationId: String)(using request: Request[NodeSeq]): EitherT[Future, Result, BREResponse_Type] =
    EitherT.fromEither {
      scalaxb.fromXMLEither[BREResponse_Type](request.body)
        .left.map { error =>
          logger.error(s"Failed to parse result response, correlationId: $correlationId, error: $error")
          BadRequest
        }
    }

  private def checkAuth(using request: Request[?]): EitherT[Future, Result, String] =
    OptionT.fromOption(request.headers.get("AUTHORIZATION").filter(_ == expectedAuthHeader)).toRight(Forbidden)

  private def validateCorrelationId(using request: Request[?]): EitherT[Future, Result, String] =
    EitherT.fromEither(request.headers.get("X-CORRELATION-ID").toRight(BadRequest))

  private def validateConversationId(using request: Request[?]): EitherT[Future, Result, String] =
    EitherT.fromEither(request.headers.get("X-CONVERSATION-ID").toRight(BadRequest))

  private def getSubmission(submissionId: String): EitherT[Future, Result, Submission] =
    OptionT(submissionRepository.getById(submissionId))
      .filter(_.state.isInstanceOf[Submitted.type]).toRight(NotFound)

  private def handleBreResponse(breResponse: BREResponse_Type, submission: Submission): EitherT[Future, Result, Done] = {
    val now = clock.instant()
    if (breResponse.requestDetail.GenericStatusMessage.ValidationResult.Status == generated.Accepted) {
      EitherT.right[Result].apply(submissionRepository.save(submission.copy(state = Approved, updated = now)))
    } else {
      for {
        _ <- EitherT.right[Result].apply(submissionRepository.save(submission.copy(state = Rejected, updated = now)))
        _ <- saveErrors(submission._id, breResponse.requestDetail.GenericStatusMessage.ValidationErrors, now)
      } yield Done
    }
  }

  private def saveErrors(submissionId: String, validationErrors: ValidationErrors_Type, timestamp: Instant): EitherT[Future, Result, Done] = {

    val fileErrors = validationErrors.FileError.map { error =>
      CadxValidationError.FileError(
        submissionId = submissionId,
        code = error.Code,
        detail = error.Details.map(_.value),
        created = timestamp
      )
    }

    val rowErrors = for {
      error  <- validationErrors.RecordError
      docRef <- error.DocRefIDInError
    } yield CadxValidationError.RowError(
      submissionId = submissionId,
      code = error.Code,
      detail = error.Details.map(_.value),
      docRef = docRef,
      created = timestamp
    )

    EitherT.right((fileErrors ++ rowErrors).traverse(cadxValidationErrorRepository.save).as(Done))
  }
}
