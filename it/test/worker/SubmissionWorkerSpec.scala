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

package worker

import connectors.SdesConnector
import models.sdes.{FileNotifyRequest, SdesSubmissionWorkItem}
import models.subscription.responses.SubscriptionInfo
import models.subscription.{Individual, IndividualContact, Organisation, OrganisationContact}
import org.apache.pekko.Done
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{verify, when}
import org.mockito.{ArgumentCaptor, Mockito}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repository.SdesSubmissionWorkItemRepository
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import scala.concurrent.Future

class SubmissionWorkerSpec
  extends AnyFreeSpec
    with Matchers
    with BeforeAndAfterEach
    with GuiceOneServerPerSuite
    with CleanMongoCollectionSupport
    with IntegrationPatience
    with OptionValues
    with MockitoSugar
    with Eventually {

  private val mockSdesConnector: SdesConnector = mock[SdesConnector]

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(
      bind[MongoComponent].toInstance(mongoComponent),
      bind[SdesConnector].toInstance(mockSdesConnector)
    )
    .configure(
      "mongodb.submission.ttl" -> "5minutes",
      "workers.sdes-submission.initial-delay" -> "1s",
      "workers.sdes-submission.interval" -> "1s"
    )
    .build()

  private val submissionWorkerItemRepository: SdesSubmissionWorkItemRepository =
    app.injector.instanceOf[SdesSubmissionWorkItemRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockSdesConnector)
    submissionWorkerItemRepository.initialised.futureValue
  }

  "must process waiting submissions" in {

    val submissionId = "id"
    val dprsId = "dprsId"

    val individualContact = IndividualContact(Individual("first", "last"), "individual email", Some("0777777"))
    val organisationContact = OrganisationContact(Organisation("org name"), "org email", Some("0787777"))
    val subscription = SubscriptionInfo(
      id = dprsId,
      gbUser = true,
      tradingName = Some("tradingName"),
      primaryContact = individualContact,
      secondaryContact = Some(organisationContact)
    )

    val workItem = SdesSubmissionWorkItem(
      submissionId = submissionId,
      downloadUrl = url"http://example.com/test.xml",
      fileName = "test.xml",
      checksum = "checksum",
      size = 1337L,
      subscriptionInfo = subscription
    )

    when(mockSdesConnector.notify(any())(using any())).thenReturn(Future.successful(Done))
    val captor: ArgumentCaptor[FileNotifyRequest] = ArgumentCaptor.forClass(classOf[FileNotifyRequest])

    submissionWorkerItemRepository.pushNew(workItem).futureValue

    eventually {
      verify(mockSdesConnector).notify(captor.capture())(using any())
      captor.getValue.audit.correlationID mustEqual submissionId
    }
  }
}
