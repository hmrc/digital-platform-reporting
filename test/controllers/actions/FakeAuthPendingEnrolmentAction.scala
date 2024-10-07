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

import models.AuthenticatedPendingEnrolmentRequest
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.mvc.{BodyParsers, Request, Result}
import uk.gov.hmrc.auth.core.AuthConnector

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FakeAuthPendingEnrolmentAction @Inject()(userId: String,
                                               providerId: String,
                                               groupIdentifier: String) extends AuthPendingEnrolmentAction(mock[AuthConnector], mock[BodyParsers.Default]) {

  override def invokeBlock[A](request: Request[A], block: AuthenticatedPendingEnrolmentRequest[A] => Future[Result]): Future[Result] =
    block(AuthenticatedPendingEnrolmentRequest(userId, providerId, groupIdentifier, request))
}
