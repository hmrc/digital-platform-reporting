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
import org.apache.pekko.{Done, NotUsed}
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.Configuration
import play.api.libs.streams.Accumulator
import play.api.mvc.{Action, AnyContent, BodyParser, ControllerComponents, Request, Result}
import repository.{CadxValidationErrorRepository, SubmissionRepository}
import services.CadxResultService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.{Clock, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

@Singleton
class SubmissionResultCallbackController @Inject()(cc: ControllerComponents,
                                                   cadxResultService: CadxResultService,
                                                   configuration: Configuration)
                                                  (using ExecutionContext) extends BackendController(cc) with Logging {

  private val expectedBearerToken: String = configuration.get[String]("cadx.incoming-bearer-token")
  private val expectedAuthHeader: String = s"Bearer $expectedBearerToken"

  def callback(): Action[NodeSeq] = Action.async(parse.xml) { implicit request =>

    val result = for {
      _ <- checkAuth
      _ <- validateCorrelationId
      _ <- validateConversationId
      body = Source.single(ByteString.fromString(request.body.toString))
      _ <- EitherT.liftF(cadxResultService.processResult(body))
    } yield NoContent

    result.merge
  }

  private def checkAuth(using request: Request[?]): EitherT[Future, Result, String] =
    OptionT.fromOption(request.headers.get("AUTHORIZATION").filter(_ == expectedAuthHeader)).toRight(Forbidden)

  private def validateCorrelationId(using request: Request[?]): EitherT[Future, Result, String] =
    EitherT.fromEither(request.headers.get("X-CORRELATION-ID").toRight(BadRequest))

  private def validateConversationId(using request: Request[?]): EitherT[Future, Result, String] =
    EitherT.fromEither(request.headers.get("X-CONVERSATION-ID").toRight(BadRequest))
}
