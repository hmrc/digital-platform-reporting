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

import models.sdes.SdesSubmissionWorkItem
import models.submission.Submission.State.Validated
import models.subscription.responses.SubscriptionInfo
import models.subscription.{Individual, IndividualContact, Organisation, OrganisationContact}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repository.SdesSubmissionWorkItemRepository
import uk.gov.hmrc.http.StringContextOps

import java.time.{Clock, Instant, ZoneOffset}
import scala.concurrent.Future

class SdesServiceSpec
  extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach
    with MockitoSugar {

  private val now = Instant.now()
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val mockSdesSubmissionWorkItemRepository: SdesSubmissionWorkItemRepository = mock[SdesSubmissionWorkItemRepository]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockSdesSubmissionWorkItemRepository)
  }

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[SdesSubmissionWorkItemRepository].toInstance(mockSdesSubmissionWorkItemRepository),
        bind[Clock].toInstance(clock)
      )
      .build()

  private lazy val sdesService: SdesService = app.injector.instanceOf[SdesService]

  "submit" - {

    val submissionId = "submissionId"
    val subscriptionId = "subscriptionId"
    val downloadUrl = url"http://example.com/test.xml"
    val checksum = "checksum"
    val size = 1337L

    val state = Validated(
      downloadUrl = downloadUrl,
      platformOperatorId = "poid",
      fileName = "test.xml",
      checksum = checksum,
      size = size
    )

    "must save a work item with the relevant data when given all data" in {

      val individualContact = IndividualContact(Individual("first", "last"), "individual email", Some("0777777"))
      val organisationContact = OrganisationContact(Organisation("org name"), "org email", Some("0787777"))
      val subscription = SubscriptionInfo(
        id = subscriptionId,
        gbUser = true,
        tradingName = Some("tradingName"),
        primaryContact = individualContact,
        secondaryContact = Some(organisationContact)
      )

      val expectedWorkItem = SdesSubmissionWorkItem(
        submissionId = submissionId,
        downloadUrl = downloadUrl,
        checksum = checksum,
        size = size,
        subscriptionInfo = subscription
      )

      when(mockSdesSubmissionWorkItemRepository.pushNew(any(), any(), any())).thenReturn(Future.successful(null))

      sdesService.submit(submissionId, state, subscription).futureValue

      verify(mockSdesSubmissionWorkItemRepository).pushNew(expectedWorkItem, now)
    }

    "must save a work item with the relevant data when given minimal data" in {

      val organisationContact = OrganisationContact(Organisation("org name"), "org email", None)
      val subscription = SubscriptionInfo(
        id = subscriptionId,
        gbUser = false,
        tradingName = None,
        primaryContact = organisationContact,
        secondaryContact = None
      )

      val expectedWorkItem = SdesSubmissionWorkItem(
        submissionId = submissionId,
        downloadUrl = downloadUrl,
        checksum = checksum,
        size = size,
        subscriptionInfo = subscription
      )

      when(mockSdesSubmissionWorkItemRepository.pushNew(any(), any(), any())).thenReturn(Future.successful(null))

      sdesService.submit(submissionId, state, subscription).futureValue

      verify(mockSdesSubmissionWorkItemRepository).pushNew(expectedWorkItem, now)
    }

    "must fail when saving the work item fails" in {

      val organisationContact = OrganisationContact(Organisation("org name"), "org email", None)
      val subscription = SubscriptionInfo(
        id = subscriptionId,
        gbUser = false,
        tradingName = None,
        primaryContact = organisationContact,
        secondaryContact = None
      )

      when(mockSdesSubmissionWorkItemRepository.pushNew(any(), any(), any())).thenReturn(Future.failed(new RuntimeException()))

      sdesService.submit(submissionId, state, subscription).failed.futureValue
    }
  }
}
