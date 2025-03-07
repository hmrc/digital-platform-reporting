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

import connectors.DeliveredSubmissionConnector
import models.assumed.AssumingPlatformOperator
import models.submission.*
import models.submission.DeliveredSubmissionSortBy.SubmissionDate
import models.submission.SortOrder.Descending
import models.submission.Submission.SubmissionType.{ManualAssumedReport, Xml}
import models.submission.Submission.{State, SubmissionType}
import models.submission.SubmissionStatus.*
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import repository.SubmissionRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Instant, Year}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ViewSubmissionsServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with BeforeAndAfterEach with ScalaFutures with IntegrationPatience {

  private val mockConnector = mock[DeliveredSubmissionConnector]
  private val mockRepository = mock[SubmissionRepository]
  private val mockAssumedReportingService = mock[AssumedReportingService]
  private val instant = Instant.ofEpochSecond(1)
  private val service = new ViewSubmissionsService(mockConnector, mockRepository, mockAssumedReportingService)
  private implicit val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach(): Unit = {
    Mockito.reset(mockConnector, mockRepository, mockAssumedReportingService)
    super.beforeEach()
  }

  ".getSubmissions" - {

    "must return submission summaries for delivered submissions and `Submitted` local submissions" - {

      "when there are delivered submissions and no Submitted local XML submissions" in {

        val deliveredSubmission1 = DeliveredSubmission("id1", "fileName", Some("operatorId"), Some("operatorName"), Some(Year.of(2024)), "submissionCaseId", instant, Success, None)
        val deliveredSubmission2 = DeliveredSubmission("id2", "fileName2", Some("operatorId"), Some("operatorName"), Some(Year.of(2024)), "submissionCaseId", instant, Success, None)

        val deliveredSubmissions = DeliveredSubmissions(
          submissions = Seq(deliveredSubmission1, deliveredSubmission2),
          resultsCount = 2
        )

        val submission1 = Submission(_id = "id1", submissionType = Xml, dprsId = "dprsId", operatorId = "operatorId", operatorName = "operatorName", assumingOperatorName = None, state = State.Approved("filename", Year.of(2024)), created = instant, updated = instant)

        when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(deliveredSubmissions)))
        when(mockRepository.countSubmittedXmlSubmissions(any())).thenReturn(Future.successful(0L))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(Seq(submission1)))

        val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
        val result = service.getDeliveredSubmissions(request).futureValue

        val summary1 = SubmissionSummary(deliveredSubmission1, false, true)
        val summary2 = SubmissionSummary(deliveredSubmission2, false, false)

        result mustEqual SubmissionsSummary(
          deliveredSubmissions = Seq(summary1, summary2),
          deliveredSubmissionRecordCount = 2,
          deliveredSubmissionsExist = true,
          undeliveredSubmissionCount = 0L
        )

        verify(mockConnector).get(eqTo(request))(any())
        verify(mockRepository).countSubmittedXmlSubmissions("dprsId")
        verify(mockRepository).getBySubscriptionId("dprsId")
      }

      "when there are delivered submissions and some Submitted local submissions" in {

        val deliveredSubmission1 = DeliveredSubmission("id1", "fileName", Some("operatorId"), Some("operatorName"), Some(Year.of(2024)), "submissionCaseId", instant, Success, None)
        val deliveredSubmission2 = DeliveredSubmission("id2", "fileName2", Some("operatorId"), Some("operatorName"), Some(Year.of(2024)), "submissionCaseId", instant, Success, None)

        val deliveredSubmissions = DeliveredSubmissions(
          submissions = Seq(deliveredSubmission1, deliveredSubmission2),
          resultsCount = 2
        )

        val submission1 = Submission(_id = "id1", submissionType = Xml, dprsId = "dprsId", operatorId = "operatorId", operatorName = "operatorName", assumingOperatorName = None, state = State.Approved("filename", Year.of(2024)), created = instant, updated = instant)

        when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(deliveredSubmissions)))
        when(mockRepository.countSubmittedXmlSubmissions(any())).thenReturn(Future.successful(2L))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(Seq(submission1)))

        val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
        val result = service.getDeliveredSubmissions(request).futureValue

        val summary1 = SubmissionSummary(deliveredSubmission1, false, true)
        val summary2 = SubmissionSummary(deliveredSubmission2, false, false)

        result mustEqual SubmissionsSummary(
          deliveredSubmissions = Seq(summary1, summary2),
          deliveredSubmissionRecordCount = 2,
          deliveredSubmissionsExist = true,
          undeliveredSubmissionCount = 2L
        )

        verify(mockConnector).get(eqTo(request))(any())
        verify(mockRepository).countSubmittedXmlSubmissions("dprsId")
        verify(mockRepository).getBySubscriptionId("dprsId")
      }

      "when there are no delivered submissions and some Submitted local submissions" in {

        when(mockConnector.get(any())(any())).thenReturn(Future.successful(None))
        when(mockRepository.countSubmittedXmlSubmissions(any())).thenReturn(Future.successful(2L))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(Nil))

        val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
        val result = service.getDeliveredSubmissions(request).futureValue

        result mustEqual SubmissionsSummary(
          deliveredSubmissions = Nil,
          deliveredSubmissionRecordCount = 0,
          deliveredSubmissionsExist = false,
          undeliveredSubmissionCount = 2L
        )

        verify(mockConnector).get(eqTo(request))(any())
        verify(mockRepository).countSubmittedXmlSubmissions("dprsId")
        verify(mockRepository).getBySubscriptionId("dprsId")
      }
    }

    "must return an empty submission summary when there are no delivered submissions or Submitted local submissions" in {

      when(mockConnector.get(any())(any())).thenReturn(Future.successful(None))
      when(mockRepository.countSubmittedXmlSubmissions(any())).thenReturn(Future.successful(0L))
      when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(Nil))

      val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
      val result = service.getDeliveredSubmissions(request).futureValue

      result mustEqual SubmissionsSummary(Nil, 0, false, 0)

      verify(mockConnector).get(eqTo(request))(any())
      verify(mockRepository).countSubmittedXmlSubmissions("dprsId")
      verify(mockRepository).getBySubscriptionId("dprsId")
    }

    "must return an empty submission summary, with `deliveredSubmissionsExist` as true, when no delivered submissions are returned but the connector response indicates that some exist" in {

      when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(DeliveredSubmissions(Nil, 0))))
      when(mockRepository.countSubmittedXmlSubmissions(any())).thenReturn(Future.successful(0L))
      when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(Nil))

      val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
      val result = service.getDeliveredSubmissions(request).futureValue

      result mustEqual SubmissionsSummary(Nil, 0, true, 0)

      verify(mockConnector).get(eqTo(request))(any())
      verify(mockRepository).countSubmittedXmlSubmissions("dprsId")
      verify(mockRepository).getBySubscriptionId("dprsId")
    }
  }

  "getUndeliveredSubmissions" - {

    "must return all `Submitted` XML submissions in descending created time" in {

      val submissions = Seq(
        Submission(_id = "id1", SubmissionType.Xml, "dprsId", "operatorId", "operatorName", None, State.Submitted("fileName1", Year.of(2024), 343534L), instant, instant),
        Submission(_id = "id2", SubmissionType.Xml, "dprsId", "operatorId", "operatorName", None, State.Submitted("fileName2", Year.of(2024), 57567L), instant.plusSeconds(1), instant)
      )

      when(mockRepository.getSubmittedXmlSubmissions(any())).thenReturn(Future.successful(submissions))

      val result = service.getUndeliveredSubmissions("dprsId").futureValue

      result must contain theSameElementsInOrderAs Seq(
        SubmissionSummary("id2", "fileName2", Some("operatorId"), Some("operatorName"), Some(Year.of(2024)), instant.plusSeconds(1), Pending, None, None, isDeleted = false, localDataExists = true),
        SubmissionSummary("id1", "fileName1", Some("operatorId"), Some("operatorName"), Some(Year.of(2024)), instant, Pending, None, None, isDeleted = false, localDataExists = true)
      )
    }
  }

  "getAssumedReports" - {

    "when there are delivered submissions" - {

      "must return the most recent submission for each operatorId/reportingPeriod pair, most recent first" in {

        val deliveredSubmissions = DeliveredSubmissions(
          submissions = Seq(
            DeliveredSubmission("id1", "fileName1", Some("operatorId1"), Some("operatorName1"), Some(Year.of(2024)), "submissionCaseId1", instant.plusSeconds(1), Success, Some("assumingName")),
            DeliveredSubmission("id2", "fileName2", Some("operatorId1"), Some("operatorName1"), Some(Year.of(2024)), "submissionCaseId2", instant.plusSeconds(2), Success, Some("assumingName")),
            DeliveredSubmission("id3", "fileName3", Some("operatorId1"), Some("operatorName1"), Some(Year.of(2025)), "submissionCaseId3", instant.plusSeconds(3), Success, Some("assumingName")),
            DeliveredSubmission("id4", "fileName4", Some("operatorId2"), Some("operatorName2"), Some(Year.of(2024)), "submissionCaseId4", instant.plusSeconds(4), Success, Some("assumingName")),
            DeliveredSubmission("id5", "fileName5", Some("operatorId2"), Some("operatorName2"), Some(Year.of(2024)), "submissionCaseId5", instant.plusSeconds(5), Success, Some("assumingName")),
            DeliveredSubmission("id6", "fileName6", Some("operatorId3"), Some("operatorName3"), Some(Year.of(2024)), "submissionCaseId6", instant.plusSeconds(6), Success, Some("assumingName"))
          ),
          resultsCount = 6
        )

        val localSubmission2 = Submission(_id = "id2", submissionType = ManualAssumedReport, dprsId = "dprsId", operatorId = "operatorId1", operatorName = "operatorName1", assumingOperatorName = Some("assumingName"), state = State.Approved("filename", Year.of(2024)), created = instant, updated = instant)
        val localSubmission3 = Submission(_id = "id3", submissionType = ManualAssumedReport, dprsId = "dprsId", operatorId = "operatorId1", operatorName = "operatorName1", assumingOperatorName = Some("assumingName"), state = State.Approved("filename", Year.of(2025)), created = instant, updated = instant)

        val assumedReport2 = AssumedReportingSubmission("operatorId1", "operatorName1", AssumingPlatformOperator("name", "GB", Nil, "GB", "address"), Year.of(2024), true)
        val assumedReport3 = AssumedReportingSubmission("operatorId1", "operatorName1", AssumingPlatformOperator("name", "GB", Nil, "GB", "address"), Year.of(2025), true)
        val assumedReport5 = AssumedReportingSubmission("operatorId2", "operatorName2", AssumingPlatformOperator("name", "GB", Nil, "GB", "address"), Year.of(2024), false)
        val assumedReport6 = AssumedReportingSubmission("operatorId3", "operatorName3", AssumingPlatformOperator("name", "GB", Nil, "GB", "address"), Year.of(2024), false)

        when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(deliveredSubmissions)))
        when(mockAssumedReportingService.getSubmission(any(), eqTo("operatorId1"), eqTo(Year.of(2024)))(using any())).thenReturn(Future.successful(Some(assumedReport2)))
        when(mockAssumedReportingService.getSubmission(any(), eqTo("operatorId1"), eqTo(Year.of(2025)))(using any())).thenReturn(Future.successful(Some(assumedReport3)))
        when(mockAssumedReportingService.getSubmission(any(), eqTo("operatorId2"), eqTo(Year.of(2024)))(using any())).thenReturn(Future.successful(Some(assumedReport5)))
        when(mockAssumedReportingService.getSubmission(any(), eqTo("operatorId3"), eqTo(Year.of(2024)))(using any())).thenReturn(Future.successful(Some(assumedReport6)))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(Seq(localSubmission2, localSubmission3)))

        val result = service.getAssumedReports("dprsId").futureValue

        result must contain theSameElementsInOrderAs Seq(
          SubmissionSummary("id6", "fileName6", Some("operatorId3"), Some("operatorName3"), Some(Year.of(2024)), instant.plusSeconds(6), Success, Some("assumingName"), Some("submissionCaseId6"), false, false),
          SubmissionSummary("id5", "fileName5", Some("operatorId2"), Some("operatorName2"), Some(Year.of(2024)), instant.plusSeconds(5), Success, Some("assumingName"), Some("submissionCaseId5"), false, false),
          SubmissionSummary("id3", "fileName3", Some("operatorId1"), Some("operatorName1"), Some(Year.of(2025)), instant.plusSeconds(3), Success, Some("assumingName"), Some("submissionCaseId3"), true, true),
          SubmissionSummary("id2", "fileName2", Some("operatorId1"), Some("operatorName1"), Some(Year.of(2024)), instant.plusSeconds(2), Success, Some("assumingName"), Some("submissionCaseId2"), true, true)
        )

        verify(mockRepository).getBySubscriptionId(eqTo("dprsId"))
      }

      "must exclude submissions where either the operatorId or the reportingPeriod is None" in {

        val deliveredSubmissions = DeliveredSubmissions(
          submissions = Seq(
            DeliveredSubmission("id1", "fileName1", None, Some("operatorName1"), Some(Year.of(2024)), "submissionCaseId1", instant.plusSeconds(1), Success, Some("assumingName")),
            DeliveredSubmission("id2", "fileName2", Some("operatorId1"), Some("operatorName1"), None, "submissionCaseId2", instant.plusSeconds(2), Success, Some("assumingName")),
            DeliveredSubmission("id3", "fileName3", Some("operatorId1"), Some("operatorName1"), Some(Year.of(2025)), "submissionCaseId3", instant.plusSeconds(3), Success, Some("assumingName")),
            DeliveredSubmission("id4", "fileName4", Some("operatorId2"), Some("operatorName2"), Some(Year.of(2024)), "submissionCaseId4", instant.plusSeconds(4), Success, Some("assumingName")),
            DeliveredSubmission("id5", "fileName5", Some("operatorId2"), Some("operatorName2"), Some(Year.of(2024)), "submissionCaseId5", instant.plusSeconds(5), Success, Some("assumingName")),
            DeliveredSubmission("id6", "fileName6", Some("operatorId3"), Some("operatorName3"), Some(Year.of(2024)), "submissionCaseId6", instant.plusSeconds(6), Success, Some("assumingName"))
          ),
          resultsCount = 6
        )

        val localSubmission2 = Submission(_id = "id2", submissionType = ManualAssumedReport, dprsId = "dprsId", operatorId = "operatorId1", operatorName = "operatorName1", assumingOperatorName = Some("assumingName"), state = State.Approved("filename", Year.of(2024)), created = instant, updated = instant)
        val localSubmission3 = Submission(_id = "id3", submissionType = ManualAssumedReport, dprsId = "dprsId", operatorId = "operatorId1", operatorName = "operatorName1", assumingOperatorName = Some("assumingName"), state = State.Approved("filename", Year.of(2025)), created = instant, updated = instant)

        val assumedReport3 = AssumedReportingSubmission("operatorId1", "operatorName1", AssumingPlatformOperator("name", "GB", Nil, "GB", "address"), Year.of(2025), true)
        val assumedReport5 = AssumedReportingSubmission("operatorId2", "operatorName2", AssumingPlatformOperator("name", "GB", Nil, "GB", "address"), Year.of(2024), false)
        val assumedReport6 = AssumedReportingSubmission("operatorId3", "operatorName3", AssumingPlatformOperator("name", "GB", Nil, "GB", "address"), Year.of(2024), false)

        when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(deliveredSubmissions)))
        when(mockAssumedReportingService.getSubmission(any(), eqTo("operatorId1"), eqTo(Year.of(2025)))(using any())).thenReturn(Future.successful(Some(assumedReport3)))
        when(mockAssumedReportingService.getSubmission(any(), eqTo("operatorId2"), eqTo(Year.of(2024)))(using any())).thenReturn(Future.successful(Some(assumedReport5)))
        when(mockAssumedReportingService.getSubmission(any(), eqTo("operatorId3"), eqTo(Year.of(2024)))(using any())).thenReturn(Future.successful(Some(assumedReport6)))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(Seq(localSubmission2, localSubmission3)))

        val result = service.getAssumedReports("dprsId").futureValue

        result must contain theSameElementsInOrderAs Seq(
          SubmissionSummary("id6", "fileName6", Some("operatorId3"), Some("operatorName3"), Some(Year.of(2024)), instant.plusSeconds(6), Success, Some("assumingName"), Some("submissionCaseId6"), false, false),
          SubmissionSummary("id5", "fileName5", Some("operatorId2"), Some("operatorName2"), Some(Year.of(2024)), instant.plusSeconds(5), Success, Some("assumingName"), Some("submissionCaseId5"), false, false),
          SubmissionSummary("id3", "fileName3", Some("operatorId1"), Some("operatorName1"), Some(Year.of(2025)), instant.plusSeconds(3), Success, Some("assumingName"), Some("submissionCaseId3"), true, true)
        )

        verify(mockRepository).getBySubscriptionId(eqTo("dprsId"))
      }
    }

    "when there are no delivered submissions" - {

      "must return an empty submissions summary" in {

        when(mockConnector.get(any())(any())).thenReturn(Future.successful(None))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(Nil))

        val result = service.getAssumedReports("dprsId").futureValue

        result mustBe empty

        verify(mockRepository).getBySubscriptionId(eqTo("dprsId"))
      }
    }

    "when there are multiple pages of results" - {

      "must call subsequent pages until all results are included" in {

        val submission = DeliveredSubmission("1", "fileName", Some("operatorId"), Some("operatorName"), Some(Year.of(2024)), "submissionCaseId", instant.plusSeconds(1), Success, Some("assumingName"))

        val page1Submissions = (1 to 10).map(i => submission.copy(conversationId = i.toString, submissionDateTime = instant.plusSeconds(i), reportingPeriod = Some(Year.of(2024))))
        val page2Submissions = (11 to 20).map(i => submission.copy(conversationId = i.toString, submissionDateTime = instant.plusSeconds(i), reportingPeriod = Some(Year.of(2025))))
        val page3Submissions = (21 to 25).map(i => submission.copy(conversationId = i.toString, submissionDateTime = instant.plusSeconds(i), reportingPeriod = Some(Year.of(2026))))
        val page1 = DeliveredSubmissions(page1Submissions, 25)
        val page2 = DeliveredSubmissions(page2Submissions, 25)
        val page3 = DeliveredSubmissions(page3Submissions, 25)

        val assumedReport1 = AssumedReportingSubmission("operatorId", "operatorName", AssumingPlatformOperator("name", "GB", Nil, "GB", "address"), Year.of(2024), true)
        val assumedReport2 = AssumedReportingSubmission("operatorId", "operatorName", AssumingPlatformOperator("name", "GB", Nil, "GB", "address"), Year.of(2025), true)
        val assumedReport3 = AssumedReportingSubmission("operatorId", "operatorName", AssumingPlatformOperator("name", "GB", Nil, "GB", "address"), Year.of(2026), false)

        when(mockConnector.get(any())(any())).thenReturn(
          Future.successful(Some(page1)),
          Future.successful(Some(page2)),
          Future.successful(Some(page3))
        )

        when(mockAssumedReportingService.getSubmission(any(), eqTo("operatorId"), eqTo(Year.of(2024)))(using any())).thenReturn(Future.successful(Some(assumedReport1)))
        when(mockAssumedReportingService.getSubmission(any(), eqTo("operatorId"), eqTo(Year.of(2025)))(using any())).thenReturn(Future.successful(Some(assumedReport2)))
        when(mockAssumedReportingService.getSubmission(any(), eqTo("operatorId"), eqTo(Year.of(2026)))(using any())).thenReturn(Future.successful(Some(assumedReport3)))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(Nil))

        val result = service.getAssumedReports("dprsId").futureValue

        result must contain theSameElementsInOrderAs Seq(
          SubmissionSummary("25", "fileName", Some("operatorId"), Some("operatorName"), Some(Year.of(2026)), instant.plusSeconds(25), Success, Some("assumingName"), Some("submissionCaseId"), false, false),
          SubmissionSummary("20", "fileName", Some("operatorId"), Some("operatorName"), Some(Year.of(2025)), instant.plusSeconds(20), Success, Some("assumingName"), Some("submissionCaseId"), true, false),
          SubmissionSummary("10", "fileName", Some("operatorId"), Some("operatorName"), Some(Year.of(2024)), instant.plusSeconds(10), Success, Some("assumingName"), Some("submissionCaseId"), true, false),
        )

        val expectedRequest1 = ViewSubmissionsRequest("dprsId", true, 1, SubmissionDate, Descending, None, None, None, Seq(Pending, Success, Rejected))
        val expectedRequest2 = ViewSubmissionsRequest("dprsId", true, 2, SubmissionDate, Descending, None, None, None, Seq(Pending, Success, Rejected))
        val expectedRequest3 = ViewSubmissionsRequest("dprsId", true, 3, SubmissionDate, Descending, None, None, None, Seq(Pending, Success, Rejected))

        verify(mockConnector).get(eqTo(expectedRequest1))(any())
        verify(mockConnector).get(eqTo(expectedRequest2))(any())
        verify(mockConnector).get(eqTo(expectedRequest3))(any())

        verify(mockAssumedReportingService).getSubmission(eqTo("dprsId"), eqTo("operatorId"), eqTo(Year.of(2024)))(using any())
        verify(mockAssumedReportingService).getSubmission(eqTo("dprsId"), eqTo("operatorId"), eqTo(Year.of(2025)))(using any())
        verify(mockAssumedReportingService).getSubmission(eqTo("dprsId"), eqTo("operatorId"), eqTo(Year.of(2026)))(using any())
        verify(mockRepository).getBySubscriptionId(eqTo("dprsId"))
      }
    }
  }
}
