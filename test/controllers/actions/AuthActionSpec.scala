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
import play.api.mvc.*
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import support.auth.Retrievals.Ops
import uk.gov.hmrc.auth.core.*
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class AuthActionSpec extends AnyFreeSpec with Matchers {

  "Auth Action" - {

    val app = GuiceApplicationBuilder().build()
    val bodyParsers = app.injector.instanceOf[BodyParsers.Default]
    val dprsId = "dprs id"

    "must process the request when the user has the correct enrolment with a DPRS Id" in {
      
      val validEnrolments = Enrolments(Set(
        Enrolment(
          key = "HMRC-DPRS",
          identifiers = Seq(EnrolmentIdentifier("DPRSID", dprsId)),
          state = "activated"
        )
      ))

      val action = new AuthAction(new FakeAuthConnector(Some("internalId") ~ validEnrolments), bodyParsers)
      val request = FakeRequest()

      val result = action(a => Ok(a.dprsId))(request)

      status(result) mustEqual OK
      contentAsString(result) mustEqual dprsId
    }

    "must return Forbidden when the user does not have the correct enrolment" in {

      val invalidEnrolments = Enrolments(Set(
        Enrolment(
          key = "ANOTHER-ENROLMENT",
          identifiers = Seq(EnrolmentIdentifier("DPRSID", dprsId)),
          state = "activated"
        )
      ))

      val action = new AuthAction(new FakeAuthConnector(Some("internalId") ~ invalidEnrolments), bodyParsers)
      val request = FakeRequest()

      val result = action(a => Ok(a.dprsId))(request)

      status(result) mustEqual FORBIDDEN
    }

    "must return Forbidden when internalId not present" in {
      val validEnrolments = Enrolments(Set(
        Enrolment(
          key = "HMRC-DPRS",
          identifiers = Seq(EnrolmentIdentifier("DPRSID", dprsId)),
          state = "activated"
        )
      ))

      val action = new AuthAction(new FakeAuthConnector(None ~ validEnrolments), bodyParsers)
      val request = FakeRequest()

      val result = action(a => Ok(a.dprsId))(request)

      status(result) mustEqual FORBIDDEN
    }
  }

  class FakeAuthConnector[T](value: T) extends AuthConnector {
    override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[A] =
      Future.fromTry(Try(value.asInstanceOf[A]))
  }
}
