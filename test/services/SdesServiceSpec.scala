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

import connectors.SdesConnector
import models.sdes.*
import models.submission.Submission.State.Validated
import models.subscription.responses.SubscriptionInfo
import models.subscription.{Individual, IndividualContact, Organisation, OrganisationContact}
import org.apache.pekko.Done
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repository.SdesSubmissionWorkItemRepository
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import utils.DateTimeFormats.ISO8601Formatter

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, Year, ZoneOffset}
import scala.concurrent.Future

class SdesServiceSpec
  extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with GuiceOneAppPerSuite
    with BeforeAndAfterEach
    with MockitoSugar
    with OptionValues {

  private val now = Instant.now()
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val mockSdesSubmissionWorkItemRepository: SdesSubmissionWorkItemRepository = mock[SdesSubmissionWorkItemRepository]
  private val mockSdesConnector: SdesConnector = mock[SdesConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(
      mockSdesSubmissionWorkItemRepository,
      mockSdesConnector
    )
  }

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "sdes.submission.information-type" -> "information-type",
        "sdes.recipient-or-sender" -> "recipient-or-sender",
        "sdes.submission.retry-after" -> "30m"
      )
      .overrides(
        bind[SdesSubmissionWorkItemRepository].toInstance(mockSdesSubmissionWorkItemRepository),
        bind[SdesConnector].toInstance(mockSdesConnector),
        bind[Clock].toInstance(clock)
      )
      .build()

  private lazy val sdesService: SdesService = app.injector.instanceOf[SdesService]

  "enqueueSubmission" - {

    val submissionId = "submissionId"
    val subscriptionId = "subscriptionId"
    val downloadUrl = url"http://example.com/test.xml"
    val fileName = "test.xml"
    val checksum = "checksum"
    val size = 1337L

    val state = Validated(
      downloadUrl = downloadUrl,
      reportingPeriod = Year.of(2024),
      fileName = fileName,
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
        fileName = fileName,
        checksum = checksum,
        size = size,
        subscriptionInfo = subscription
      )

      when(mockSdesSubmissionWorkItemRepository.pushNew(any(), any(), any())).thenReturn(Future.successful(null))

      sdesService.enqueueSubmission(submissionId, state, subscription).futureValue

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
        fileName = fileName,
        checksum = checksum,
        size = size,
        subscriptionInfo = subscription
      )

      when(mockSdesSubmissionWorkItemRepository.pushNew(any(), any(), any())).thenReturn(Future.successful(null))

      sdesService.enqueueSubmission(submissionId, state, subscription).futureValue

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

      sdesService.enqueueSubmission(submissionId, state, subscription).failed.futureValue
    }
  }

  "processNextSubmission" - {

    "when there is a submission in the work item queue" - {

      val submissionId = "submissionId"
      val subscriptionId = "subscriptionId"
      val tradingName = "tradingName"
      val downloadUrl = url"http://example.com/test.xml"
      val fileName = "test.xml"
      val checksum = "checksum"
      val size = 1337L

      "when all optional data is supplied" - {

        val individualContact = IndividualContact(Individual("first", "last"), "individual email", Some("0777777"))
        val organisationContact = OrganisationContact(Organisation("org name"), "org email", Some("0787777"))
        val subscription = SubscriptionInfo(
          id = subscriptionId,
          gbUser = true,
          tradingName = Some(tradingName),
          primaryContact = individualContact,
          secondaryContact = Some(organisationContact)
        )

        val workItem = WorkItem(
          id = ObjectId(),
          receivedAt = now.minus(1, ChronoUnit.HOURS),
          updatedAt = now.minus(1, ChronoUnit.HOURS),
          availableAt = now.minus(1, ChronoUnit.HOURS),
          status = ToDo,
          failureCount = 0,
          item = SdesSubmissionWorkItem(
            submissionId = submissionId,
            downloadUrl = downloadUrl,
            checksum = checksum,
            fileName = fileName,
            size = size,
            subscriptionInfo = subscription
          )
        )

        val expectedNotificationRequest = FileNotifyRequest(
          informationType = "information-type",
          file = FileMetadata(
            recipientOrSender = "recipient-or-sender",
            name = fileName,
            location = downloadUrl,
            checksum = FileChecksum("SHA-256", checksum),
            size = size,
            properties = List(
              FileProperty("requestCommon/conversationID", submissionId),
              FileProperty("requestCommon/receiptDate", ISO8601Formatter.format(now)),
              FileProperty("requestCommon/regime", "DPI"),
              FileProperty("requestCommon/schemaVersion", "1.0.0"),
              FileProperty("requestAdditionalDetail/fileName", "test"),
              FileProperty("requestAdditionalDetail/subscriptionID", subscriptionId),
              FileProperty("requestAdditionalDetail/tradingName", tradingName),
              FileProperty("requestAdditionalDetail/isGBUser", "true"),
              FileProperty("requestAdditionalDetail/primaryContact/emailAddress", individualContact.email),
              FileProperty("requestAdditionalDetail/primaryContact/phoneNumber", individualContact.phone.value),
              FileProperty("requestAdditionalDetail/primaryContact/individualDetails/firstName", individualContact.individual.firstName),
              FileProperty("requestAdditionalDetail/primaryContact/individualDetails/lastName", individualContact.individual.lastName),
              FileProperty("requestAdditionalDetail/secondaryContact/emailAddress", organisationContact.email),
              FileProperty("requestAdditionalDetail/secondaryContact/phoneNumber", organisationContact.phone.value),
              FileProperty("requestAdditionalDetail/secondaryContact/organisationDetails/organisationName", organisationContact.organisation.name)
            )
          ),
          audit = FileAudit(
            correlationID = submissionId
          )
        )

        "must submit the work item to SDES and return true" in {

          when(mockSdesSubmissionWorkItemRepository.pullOutstanding(any(), any())).thenReturn(Future.successful(Some(workItem)))
          when(mockSdesConnector.notify(any())(using any())).thenReturn(Future.successful(Done))
          when(mockSdesSubmissionWorkItemRepository.complete(any(), any())).thenReturn(Future.successful(true))

          sdesService.processNextSubmission().futureValue mustBe true

          verify(mockSdesSubmissionWorkItemRepository).pullOutstanding(now.minus(30, ChronoUnit.MINUTES), now)
          verify(mockSdesConnector).notify(eqTo(expectedNotificationRequest))(using any())
          verify(mockSdesSubmissionWorkItemRepository).complete(workItem.id, ProcessingStatus.Succeeded)
        }
      }

      "when minimal data is supplied" - {

        val organisationContact = OrganisationContact(Organisation("org name"), "org email", None)
        val subscription = SubscriptionInfo(
          id = subscriptionId,
          gbUser = false,
          tradingName = None,
          primaryContact = organisationContact,
          secondaryContact = None
        )

        val workItem = WorkItem(
          id = ObjectId(),
          receivedAt = now.minus(1, ChronoUnit.HOURS),
          updatedAt = now.minus(1, ChronoUnit.HOURS),
          availableAt = now.minus(1, ChronoUnit.HOURS),
          status = ToDo,
          failureCount = 0,
          item = SdesSubmissionWorkItem(
            submissionId = submissionId,
            downloadUrl = downloadUrl,
            checksum = checksum,
            fileName = fileName,
            size = size,
            subscriptionInfo = subscription
          )
        )

        val expectedNotificationRequest = FileNotifyRequest(
          informationType = "information-type",
          file = FileMetadata(
            recipientOrSender = "recipient-or-sender",
            name = fileName,
            location = downloadUrl,
            checksum = FileChecksum("SHA-256", checksum),
            size = size,
            properties = List(
              FileProperty("requestCommon/conversationID", submissionId),
              FileProperty("requestCommon/receiptDate", ISO8601Formatter.format(now)),
              FileProperty("requestCommon/regime", "DPI"),
              FileProperty("requestCommon/schemaVersion", "1.0.0"),
              FileProperty("requestAdditionalDetail/fileName", "test"),
              FileProperty("requestAdditionalDetail/subscriptionID", subscriptionId),
              FileProperty("requestAdditionalDetail/isGBUser", "false"),
              FileProperty("requestAdditionalDetail/primaryContact/emailAddress", organisationContact.email),
              FileProperty("requestAdditionalDetail/primaryContact/organisationDetails/organisationName", organisationContact.organisation.name)
            )
          ),
          audit = FileAudit(
            correlationID = submissionId
          )
        )

        "must submit the work item to SDES and return true" in {

          when(mockSdesSubmissionWorkItemRepository.pullOutstanding(any(), any())).thenReturn(Future.successful(Some(workItem)))
          when(mockSdesConnector.notify(any())(using any())).thenReturn(Future.successful(Done))
          when(mockSdesSubmissionWorkItemRepository.complete(any(), any())).thenReturn(Future.successful(true))

          sdesService.processNextSubmission().futureValue mustBe true

          verify(mockSdesSubmissionWorkItemRepository).pullOutstanding(now.minus(30, ChronoUnit.MINUTES), now)
          verify(mockSdesConnector).notify(eqTo(expectedNotificationRequest))(using any())
          verify(mockSdesSubmissionWorkItemRepository).complete(workItem.id, ProcessingStatus.Succeeded)
        }
      }

      "when the SDES connector fails" - {

        "must mark the work item as failed and fail" in {

          val organisationContact = OrganisationContact(Organisation("org name"), "org email", None)
          val subscription = SubscriptionInfo(
            id = subscriptionId,
            gbUser = false,
            tradingName = None,
            primaryContact = organisationContact,
            secondaryContact = None
          )

          val workItem = WorkItem(
            id = ObjectId(),
            receivedAt = now.minus(1, ChronoUnit.HOURS),
            updatedAt = now.minus(1, ChronoUnit.HOURS),
            availableAt = now.minus(1, ChronoUnit.HOURS),
            status = ToDo,
            failureCount = 0,
            item = SdesSubmissionWorkItem(
              submissionId = submissionId,
              downloadUrl = downloadUrl,
              checksum = checksum,
              fileName = fileName,
              size = size,
              subscriptionInfo = subscription
            )
          )

          when(mockSdesSubmissionWorkItemRepository.pullOutstanding(any(), any())).thenReturn(Future.successful(Some(workItem)))
          when(mockSdesConnector.notify(any())(using any())).thenReturn(Future.failed(new RuntimeException()))
          when(mockSdesSubmissionWorkItemRepository.markAs(any(), any(), any())).thenReturn(Future.successful(true))

          sdesService.processNextSubmission().failed.futureValue

          verify(mockSdesSubmissionWorkItemRepository).pullOutstanding(now.minus(30, ChronoUnit.MINUTES), now)
          verify(mockSdesConnector).notify(any())(using any())
          verify(mockSdesSubmissionWorkItemRepository).markAs(workItem.id, ProcessingStatus.Failed)
        }
      }
    }

    "when there is no submission in the work item queue" - {

      "must return false" in {

        when(mockSdesSubmissionWorkItemRepository.pullOutstanding(any(), any())).thenReturn(Future.successful(None))

        sdesService.processNextSubmission().futureValue mustBe false

        verify(mockSdesSubmissionWorkItemRepository).pullOutstanding(now.minus(30, ChronoUnit.MINUTES), now)
        verify(mockSdesConnector, never()).notify(any())(using any())
        verify(mockSdesSubmissionWorkItemRepository, never()).markAs(any(), any(), any())
        verify(mockSdesSubmissionWorkItemRepository, never()).complete(any(), any())
      }
    }
  }

  "processAllSubmissions" - {

    val submissionId = "submissionId"
    val subscriptionId = "subscriptionId"
    val downloadUrl = url"http://example.com/test.xml"
    val tradingName = "tradingName"
    val fileName = "test.xml"
    val checksum = "checksum"
    val size = 1337L

    val individualContact = IndividualContact(Individual("first", "last"), "individual email", Some("0777777"))
    val organisationContact = OrganisationContact(Organisation("org name"), "org email", Some("0787777"))
    val subscription = SubscriptionInfo(
      id = subscriptionId,
      gbUser = true,
      tradingName = Some("tradingName"),
      primaryContact = individualContact,
      secondaryContact = Some(organisationContact)
    )

    val workItem = WorkItem(
      id = ObjectId(),
      receivedAt = now.minus(1, ChronoUnit.HOURS),
      updatedAt = now.minus(1, ChronoUnit.HOURS),
      availableAt = now.minus(1, ChronoUnit.HOURS),
      status = ToDo,
      failureCount = 0,
      item = SdesSubmissionWorkItem(
        submissionId = submissionId,
        downloadUrl = downloadUrl,
        checksum = checksum,
        fileName = fileName,
        size = size,
        subscriptionInfo = subscription
      )
    )

    val expectedNotificationRequest = FileNotifyRequest(
      informationType = "information-type",
      file = FileMetadata(
        recipientOrSender = "recipient-or-sender",
        name = fileName,
        location = downloadUrl,
        checksum = FileChecksum("SHA-256", checksum),
        size = size,
        properties = List(
          FileProperty("requestCommon/conversationID", submissionId),
          FileProperty("requestCommon/receiptDate", ISO8601Formatter.format(now)),
          FileProperty("requestCommon/regime", "DPI"),
          FileProperty("requestCommon/schemaVersion", "1.0.0"),
          FileProperty("requestAdditionalDetail/fileName", "test"),
          FileProperty("requestAdditionalDetail/subscriptionID", subscriptionId),
          FileProperty("requestAdditionalDetail/tradingName", tradingName),
          FileProperty("requestAdditionalDetail/isGBUser", "true"),
          FileProperty("requestAdditionalDetail/primaryContact/emailAddress", individualContact.email),
          FileProperty("requestAdditionalDetail/primaryContact/phoneNumber", individualContact.phone.value),
          FileProperty("requestAdditionalDetail/primaryContact/individualDetails/firstName", individualContact.individual.firstName),
          FileProperty("requestAdditionalDetail/primaryContact/individualDetails/lastName", individualContact.individual.lastName),
          FileProperty("requestAdditionalDetail/secondaryContact/emailAddress", organisationContact.email),
          FileProperty("requestAdditionalDetail/secondaryContact/phoneNumber", organisationContact.phone.value),
          FileProperty("requestAdditionalDetail/secondaryContact/organisationDetails/organisationName", organisationContact.organisation.name)
        )
      ),
      audit = FileAudit(
        correlationID = submissionId
      )
    )

    "must process all submissions" in {

      when(mockSdesSubmissionWorkItemRepository.pullOutstanding(any(), any())).thenReturn(
        Future.successful(Some(workItem)),
        Future.successful(Some(workItem)),
        Future.successful(Some(workItem)),
        Future.successful(None)
      )
      when(mockSdesConnector.notify(any())(using any())).thenReturn(Future.successful(Done))
      when(mockSdesSubmissionWorkItemRepository.complete(any(), any())).thenReturn(Future.successful(true))

      sdesService.processAllSubmissions().futureValue

      verify(mockSdesSubmissionWorkItemRepository, times(4)).pullOutstanding(now.minus(30, ChronoUnit.MINUTES), now)
      verify(mockSdesConnector, times(3)).notify(eqTo(expectedNotificationRequest))(using any())
      verify(mockSdesSubmissionWorkItemRepository, times(3)).complete(workItem.id, ProcessingStatus.Succeeded)
    }

    "must fail when one of the submissions fails" in {

      when(mockSdesSubmissionWorkItemRepository.pullOutstanding(any(), any())).thenReturn(Future.successful(Some(workItem)))
      when(mockSdesConnector.notify(any())(using any())).thenReturn(
        Future.successful(Done),
        Future.failed(new RuntimeException())
      )
      when(mockSdesSubmissionWorkItemRepository.complete(any(), any())).thenReturn(Future.successful(true))

      sdesService.processAllSubmissions().failed.futureValue

      verify(mockSdesSubmissionWorkItemRepository, times(2)).pullOutstanding(now.minus(30, ChronoUnit.MINUTES), now)
      verify(mockSdesConnector, times(2)).notify(eqTo(expectedNotificationRequest))(using any())
      verify(mockSdesSubmissionWorkItemRepository, times(1)).complete(workItem.id, ProcessingStatus.Succeeded)
      verify(mockSdesSubmissionWorkItemRepository, times(1)).markAs(workItem.id, ProcessingStatus.Failed)
    }
  }
}
