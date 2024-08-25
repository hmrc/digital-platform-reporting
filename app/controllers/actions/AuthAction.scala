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

package controllers.actions

import models.AuthenticatedRequest
import play.api.mvc.Results.Forbidden
import play.api.mvc.{ActionBuilder, ActionFunction, AnyContent, BodyParsers, Request, Result}
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AuthAction @Inject()(val authConnector: AuthConnector,
                           val parser: BodyParsers.Default)
                          (implicit val executionContext: ExecutionContext)
  extends ActionBuilder[AuthenticatedRequest, AnyContent]
    with ActionFunction[Request, AuthenticatedRequest]
    with AuthorisedFunctions {

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
    
    val enrolmentKey = "HMRC-DPRS"
    val identifierKey = "DPRSID"
    
    authorised(Enrolment(enrolmentKey)).retrieve(Retrievals.authorisedEnrolments) {
      enrolments => {
        for {
          enrolment <- enrolments.getEnrolment(enrolmentKey)
          dprsId <- enrolment.getIdentifier(identifierKey)
        } yield block(AuthenticatedRequest(request, dprsId.value))
      }.getOrElse {
        Future.successful(Forbidden)
      }
    }
  }
}
