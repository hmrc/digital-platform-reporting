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

import models.audit.{AddSubmissionEvent, AuditEvent}
import org.mockito.ArgumentMatchers.{eq as eqTo, any}
import org.mockito.Mockito.verify
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import play.api.libs.json.{Json, OFormat}
import services.AuditServiceSpec.TestEvent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

class AuditServiceSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar {

  private val mockAuditConnector: AuditConnector = mock[AuditConnector]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[AuditConnector].toInstance(mockAuditConnector)
      )
      .build()

  private lazy val auditService: AuditService = app.injector.instanceOf[AuditService]

  "audit" - {

    "must call the audit connector with the relevant data" in {

      val event = TestEvent("some-audit-type")
      given hc: HeaderCarrier = HeaderCarrier()

      auditService.audit(event)

      verify(mockAuditConnector).sendExplicitAudit(eqTo("some-audit-type"), eqTo(event))(eqTo(hc), any(), any())
    }
  }
}

object AuditServiceSpec {

  final case class TestEvent(auditType: String) extends AuditEvent

  object TestEvent {

    given OFormat[TestEvent] = Json.format
  }
}
