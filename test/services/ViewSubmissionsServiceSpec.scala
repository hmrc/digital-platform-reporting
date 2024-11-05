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
import models.submission.SubmissionStatus.*
import models.submission.Submission.{State, SubmissionType}
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import repository.SubmissionRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Instant, Year}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ViewSubmissionsServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with BeforeAndAfterEach with ScalaFutures with IntegrationPatience {

  private val mockConnector = mock[DeliveredSubmissionConnector]
  private val mockRepository = mock[SubmissionRepository]
  private val mockAssumedReportingService = mock[AssumedReportingService]

  override def beforeEach(): Unit = {
    Mockito.reset(mockConnector, mockRepository, mockAssumedReportingService)
    super.beforeEach()
  }
  
  private val instant = Instant.ofEpochSecond(1)
  private implicit val hc: HeaderCarrier = HeaderCarrier()
  
  private val service = new ViewSubmissionsService(mockConnector, mockRepository, mockAssumedReportingService)
  
  ".getSubmissions" - {
    
    "must return submission summaries for delivered submissions and `Submitted` local submissions that have no corresponding delivered submission" - {
      
      "when there are delivered submissions and no Submitted local submissions" in {        
        
        val deliveredSubmissions = DeliveredSubmissions(
          submissions = Seq(
            DeliveredSubmission("id1", "fileName", "operatorId", "operatorName", Year.of(2024), "submissionCaseId", instant, Success, None),
            DeliveredSubmission("id2", "fileName2", "operatorId", "operatorName", Year.of(2024), "submissionCaseId", instant, Success, None)
          ),
          resultsCount = 2
        )

        val localSubmissions = Seq(
          Submission(_id = "id3", SubmissionType.Xml, "dprsId", "operatorId", "operatorName", None, State.Ready, instant, instant),
          Submission(_id = "id4", SubmissionType.Xml, "dprsId", "operatorId", "operatorName", None, State.Uploading, instant, instant),
        )
        
        when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(deliveredSubmissions)))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(localSubmissions))

        val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
        val result = service.getSubmissions(request).futureValue

        result mustEqual SubmissionsSummary(
          deliveredSubmissions = deliveredSubmissions.submissions.map(x => SubmissionSummary(x, false)),
          localSubmissions = Nil,
          deliveredSubmissionRecordCount =  2,
          deliveredSubmissionsExist =  true
        )

        verify(mockConnector, times(1)).get(eqTo(request))(any())
        verify(mockRepository, times(1)).getBySubscriptionId("dprsId")
      }
      
      "when there are delivered submissions and all Submitted local submissions have a matching delivered submission" in {

        val deliveredSubmissions = DeliveredSubmissions(
          submissions = Seq(
            DeliveredSubmission("id1", "fileName", "operatorId", "operatorName", Year.of(2024), "submissionCaseId", instant, Success, None),
            DeliveredSubmission("id2", "fileName2", "operatorId", "operatorName", Year.of(2024), "submissionCaseId", instant, Success, None)
          ),
          resultsCount = 2
        )

        val localSubmissions = Seq(
          Submission(_id = "id1", SubmissionType.Xml, "dprsId", "operatorId", "operatorName", None, State.Submitted("fileName", Year.of(2024)), instant, instant),
          Submission(_id = "id2", SubmissionType.Xml, "dprsId", "operatorId", "operatorName", None, State.Submitted("fileName", Year.of(2024)), instant, instant),
        )

        when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(deliveredSubmissions)))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(localSubmissions))

        val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
        val result = service.getSubmissions(request).futureValue

        result mustEqual SubmissionsSummary(
          deliveredSubmissions.submissions.map(x => SubmissionSummary(x, false)), Nil, 2, true
        )

        verify(mockConnector, times(1)).get(eqTo(request))(any())
        verify(mockRepository, times(1)).getBySubscriptionId("dprsId")
      }
      
      "when there are delivered submissions and some Submitted local submissions do not have a matching delivered submission" in {

        val deliveredSubmissions = DeliveredSubmissions(
          submissions = Seq(
            DeliveredSubmission("id1", "fileName", "operatorId", "operatorName", Year.of(2024), "submissionCaseId", instant, Success, None),
            DeliveredSubmission("id2", "fileName2", "operatorId", "operatorName", Year.of(2024), "submissionCaseId", instant, Success, None)
          ),
          resultsCount = 2
        )

        val localSubmissions = Seq(
          Submission(_id = "id3", SubmissionType.Xml, "dprsId", "operatorId", "operatorName", None, State.Submitted("fileName3", Year.of(2024)), instant, instant),
          Submission(_id = "id4", SubmissionType.Xml, "dprsId", "operatorId", "operatorName", None, State.Submitted("fileName4", Year.of(2024)), instant, instant),
        )

        when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(deliveredSubmissions)))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(localSubmissions))

        val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
        val result = service.getSubmissions(request).futureValue

        result mustEqual SubmissionsSummary(
          deliveredSubmissions = deliveredSubmissions.submissions.map(x => SubmissionSummary(x, false)),
          localSubmissions =  localSubmissions.flatMap(x => SubmissionSummary(x)),
          deliveredSubmissionRecordCount =  2,
          deliveredSubmissionsExist =  true
        )

        verify(mockConnector, times(1)).get(eqTo(request))(any())
        verify(mockRepository, times(1)).getBySubscriptionId("dprsId")
      }
      
      "when there are no delivered submissions and some Submitted local submissions" in {

        val localSubmissions = Seq(
          Submission(_id = "id3", SubmissionType.Xml, "dprsId", "operatorId", "operatorName", None, State.Submitted("fileName3", Year.of(2024)), instant, instant),
          Submission(_id = "id4", SubmissionType.Xml, "dprsId", "operatorId", "operatorName", None, State.Submitted("fileName4", Year.of(2024)), instant, instant),
        )

        when(mockConnector.get(any())(any())).thenReturn(Future.successful(None))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(localSubmissions))

        val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
        val result = service.getSubmissions(request).futureValue

        result mustEqual SubmissionsSummary(
          deliveredSubmissions = Nil,
          localSubmissions = localSubmissions.flatMap(x => SubmissionSummary(x)),
          deliveredSubmissionRecordCount =  0,
          deliveredSubmissionsExist = false
        )

        verify(mockConnector, times(1)).get(eqTo(request))(any())
        verify(mockRepository, times(1)).getBySubscriptionId("dprsId")
      }
    }

    "must return an empty submission summary when there are no delivered submissions or Submitted local submissions" in {

      when(mockConnector.get(any())(any())).thenReturn(Future.successful(None))
      when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(Nil))

      val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
      val result = service.getSubmissions(request).futureValue

      result mustEqual SubmissionsSummary(Nil, Nil, 0, false)

      verify(mockConnector, times(1)).get(eqTo(request))(any())
      verify(mockRepository, times(1)).getBySubscriptionId("dprsId")
    }

    "must return an empty submission summary, with `deliveredSubmissionsExist` as true, when no delivered submissions are returned but the connector response indicates that some exist" in {

      when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(DeliveredSubmissions(Nil, 0))))
      when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(Nil))

      val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
      val result = service.getSubmissions(request).futureValue

      result mustEqual SubmissionsSummary(Nil, Nil, 0, true)

      verify(mockConnector, times(1)).get(eqTo(request))(any())
      verify(mockRepository, times(1)).getBySubscriptionId("dprsId")
    }
  }

  "getAssumedReports" - {

    "when there are delivered submissions" - {

      "must return the most recent submission for each operatorId/reportingPeriod pair, most recent first" in {

        val deliveredSubmissions = DeliveredSubmissions(
          submissions = Seq(
            DeliveredSubmission("id1", "fileName1", "operatorId1", "operatorName1", Year.of(2024), "submissionCaseId1", instant.plusSeconds(1), Success, Some("assumingName")),
            DeliveredSubmission("id2", "fileName2", "operatorId1", "operatorName1", Year.of(2024), "submissionCaseId2", instant.plusSeconds(2), Success, Some("assumingName")),
            DeliveredSubmission("id3", "fileName3", "operatorId1", "operatorName1", Year.of(2025), "submissionCaseId3", instant.plusSeconds(3), Success, Some("assumingName")),
            DeliveredSubmission("id4", "fileName4", "operatorId2", "operatorName2", Year.of(2024), "submissionCaseId4", instant.plusSeconds(4), Success, Some("assumingName")),
            DeliveredSubmission("id5", "fileName5", "operatorId2", "operatorName2", Year.of(2024), "submissionCaseId5", instant.plusSeconds(5), Success, Some("assumingName")),
            DeliveredSubmission("id6", "fileName6", "operatorId3", "operatorName3", Year.of(2024), "submissionCaseId6", instant.plusSeconds(6), Success, Some("assumingName"))
          ),
          resultsCount = 6
        )

        val assumedReport2 = AssumedReportingSubmission("operatorId1", "operatorName1", AssumingPlatformOperator("name", "GB", Nil, "GB", "address"), Year.of(2024), true)
        val assumedReport3 = AssumedReportingSubmission("operatorId1", "operatorName1", AssumingPlatformOperator("name", "GB", Nil, "GB", "address"), Year.of(2025), true)
        val assumedReport5 = AssumedReportingSubmission("operatorId2", "operatorName2", AssumingPlatformOperator("name", "GB", Nil, "GB", "address"), Year.of(2024), false)
        val assumedReport6 = AssumedReportingSubmission("operatorId3", "operatorName3", AssumingPlatformOperator("name", "GB", Nil, "GB", "address"), Year.of(2024), false)

        when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(deliveredSubmissions)))
        when(mockAssumedReportingService.getSubmission(any(), eqTo("operatorId1"), eqTo(Year.of(2024)))(using any())).thenReturn(Future.successful(Some(assumedReport2)))
        when(mockAssumedReportingService.getSubmission(any(), eqTo("operatorId1"), eqTo(Year.of(2025)))(using any())).thenReturn(Future.successful(Some(assumedReport3)))
        when(mockAssumedReportingService.getSubmission(any(), eqTo("operatorId2"), eqTo(Year.of(2024)))(using any())).thenReturn(Future.successful(Some(assumedReport5)))
        when(mockAssumedReportingService.getSubmission(any(), eqTo("operatorId3"), eqTo(Year.of(2024)))(using any())).thenReturn(Future.successful(Some(assumedReport6)))

        val result = service.getAssumedReports("dprsId").futureValue

        result must contain theSameElementsInOrderAs Seq(
          SubmissionSummary("id6", "fileName6", "operatorId3", "operatorName3", Year.of(2024), instant.plusSeconds(6), Success, Some("assumingName"), Some("submissionCaseId6"), false),
          SubmissionSummary("id5", "fileName5", "operatorId2", "operatorName2", Year.of(2024), instant.plusSeconds(5), Success, Some("assumingName"), Some("submissionCaseId5"), false),
          SubmissionSummary("id3", "fileName3", "operatorId1", "operatorName1", Year.of(2025), instant.plusSeconds(3), Success, Some("assumingName"), Some("submissionCaseId3"), true),
          SubmissionSummary("id2", "fileName2", "operatorId1", "operatorName1", Year.of(2024), instant.plusSeconds(2), Success, Some("assumingName"), Some("submissionCaseId2"), true)
        )
      }
    }
    
    "when there are no delivered submissions" - {
      
      "must return an empty submissions summary" in {

        when(mockConnector.get(any())(any())).thenReturn(Future.successful(None))
        
        val result = service.getAssumedReports("dprsId").futureValue
        
        result mustBe empty
      }
    }

    "when there are multiple pages of results" - {

      "must call subsequent pages until all results are included" in {

        val submission = DeliveredSubmission("1", "fileName", "operatorId", "operatorName", Year.of(2024), "submissionCaseId", instant.plusSeconds(1), Success, Some("assumingName"))

        val page1Submissions = (1 to 10).map(i => submission.copy(conversationId = i.toString, submissionDateTime = instant.plusSeconds(i), reportingPeriod = Year.of(2024)))
        val page2Submissions = (11 to 20).map(i => submission.copy(conversationId = i.toString, submissionDateTime = instant.plusSeconds(i), reportingPeriod = Year.of(2025)))
        val page3Submissions = (21 to 25).map(i => submission.copy(conversationId = i.toString, submissionDateTime = instant.plusSeconds(i), reportingPeriod = Year.of(2026)))
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

        val result = service.getAssumedReports("dprsId").futureValue

        result must contain theSameElementsInOrderAs Seq(
          SubmissionSummary("25", "fileName", "operatorId", "operatorName", Year.of(2026), instant.plusSeconds(25), Success, Some("assumingName"), Some("submissionCaseId"), false),
          SubmissionSummary("20", "fileName", "operatorId", "operatorName", Year.of(2025), instant.plusSeconds(20), Success, Some("assumingName"), Some("submissionCaseId"), true),
          SubmissionSummary("10", "fileName", "operatorId", "operatorName", Year.of(2024), instant.plusSeconds(10), Success, Some("assumingName"), Some("submissionCaseId"), true),
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
      }
    }
  }
}
