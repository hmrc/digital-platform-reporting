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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.BodyParsers
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.{AuthConnector, MissingBearerToken}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class AuthWithoutEnrolmentActionSpec extends AnyFreeSpec with Matchers {

  "Auth Without Enrolment Action" - {

    val app = GuiceApplicationBuilder().build()
    val bodyParsers = app.injector.instanceOf[BodyParsers.Default]

    "must process the request when the user is signed in" in {
      
      val action = new AuthWithoutEnrolmentAction(new FakeAuthConnector, bodyParsers)
      val request = FakeRequest()

      val result = action(a => Ok)(request)

      status(result) mustEqual OK
    }

    "must return Unauthorized when the user is not signed in" in {

      val action = new AuthWithoutEnrolmentAction(new FakeFailingAuthConnector(new MissingBearerToken), bodyParsers)
      val request = FakeRequest()
      
      val result = action(x => Ok)(request)
      
      status(result) mustEqual UNAUTHORIZED
    }
  }
}

class FakeAuthConnector extends AuthConnector {
  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
    Future.fromTry(Try(().asInstanceOf[A]))
}

class FakeFailingAuthConnector(exceptionToReturn: Throwable) extends AuthConnector {
  override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
    Future.failed(exceptionToReturn)
}
