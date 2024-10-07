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
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.*
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import support.SpecBase
import support.auth.Retrievals.*
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class AuthPendingEnrolmentActionSpec extends SpecBase {

  private val app = GuiceApplicationBuilder().build()
  private val bodyParsers = app.injector.instanceOf[BodyParsers.Default]
  private val credentials = Credentials("some-provider-id", "some-provider-type")

  "Auth for Pending Enrolment Action" - {
    "must process the request when the user is signed in" in {
      val authConnector = new FakeAuthConnector(Some("some-internal-id") ~ Some(credentials) ~ Some("some-group-identifier"))
      val action = new AuthPendingEnrolmentAction(authConnector, bodyParsers)
      val request = FakeRequest()

      val result = action(a => Ok)(request)

      status(result) mustBe OK
    }

    "must return Forbidden" - {
      "when user does not have internalId" in {
        val authConnector = new FakeAuthConnector(None ~ Some(credentials) ~ Some("some-group-identifier"))
        val action = new AuthPendingEnrolmentAction(authConnector, bodyParsers)
        val request = FakeRequest()

        val result = action(a => Ok)(request)

        status(result) mustBe FORBIDDEN
      }

      "when user does not have credentials" in {
        val authConnector = new FakeAuthConnector(Some("some-internal-id") ~ None ~ Some("some-group-identifier"))
        val action = new AuthPendingEnrolmentAction(authConnector, bodyParsers)
        val request = FakeRequest()

        val result = action(a => Ok)(request)

        status(result) mustBe FORBIDDEN
      }

      "when user does not have groupIdentifier" in {
        val authConnector = new FakeAuthConnector(Some("some-internal-id") ~ Some(credentials) ~ None)
        val action = new AuthPendingEnrolmentAction(authConnector, bodyParsers)
        val request = FakeRequest()

        val result = action(a => Ok)(request)

        status(result) mustBe FORBIDDEN
      }
    }
  }

  class FakeAuthConnector[T](value: T) extends AuthConnector {
    override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
      Future.fromTry(Try(value.asInstanceOf[A]))
  }
}
