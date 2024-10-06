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
import models.submission.*
import models.submission.DeliveredSubmissionStatus.*
import models.submission.Submission.State
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import repository.SubmissionRepository
import uk.gov.hmrc.http.HeaderCarrier

import java.time.{Instant, Year}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ViewSubmissionsServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with BeforeAndAfterEach with ScalaFutures {

  private val mockConnector = mock[DeliveredSubmissionConnector]
  private val mockRepository = mock[SubmissionRepository]

  override def beforeEach(): Unit = {
    Mockito.reset(mockConnector, mockRepository)
    super.beforeEach()
  }
  
  private val instant = Instant.ofEpochSecond(1)
  private implicit val hc: HeaderCarrier = HeaderCarrier()
  
  private val service = new ViewSubmissionsService(mockConnector, mockRepository)
  
  ".getSubmissions" - {
    
    "must return submission summaries for delivered submissions and `Submitted` local submissions that have no corresponding delivered submission" - {
      
      "when there are delivered submissions and no Submitted local submissions" in {        
        
        val deliveredSubmissions = DeliveredSubmissions(
          submissions = Seq(
            DeliveredSubmission("id1", "fileName", "operatorId", "operatorName", "2024", instant, Success, None),
            DeliveredSubmission("id2", "fileName2", "operatorId", "operatorName", "2024", instant, Success, None)
          ),
          resultsCount = 2
        )

        val localSubmissions = Seq(
          Submission(_id = "id3", "dprsId", "operatorId", "operatorName", State.Ready, instant, instant),
          Submission(_id = "id4", "dprsId", "operatorId", "operatorName", State.Uploading, instant, instant),
        )
        
        when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(deliveredSubmissions)))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(localSubmissions))

        val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
        val result = service.getSubmissions(request).futureValue

        result mustEqual SubmissionSummary(
          deliveredSubmissions.submissions,
          Nil
        )

        verify(mockConnector, times(1)).get(eqTo(request))(any())
        verify(mockRepository, times(1)).getBySubscriptionId("dprsId")
      }
      
      "when there are delivered submissions and all Submitted local submissions have a matching delivered submission" in {

        val deliveredSubmissions = DeliveredSubmissions(
          submissions = Seq(
            DeliveredSubmission("id1", "fileName", "operatorId", "operatorName", "2024", instant, Success, None),
            DeliveredSubmission("id2", "fileName2", "operatorId", "operatorName", "2024", instant, Success, None)
          ),
          resultsCount = 2
        )

        val localSubmissions = Seq(
          Submission(_id = "id1", "dprsId", "operatorId", "operatorName", State.Submitted("fileName", Year.of(2024)), instant, instant),
          Submission(_id = "id2", "dprsId", "operatorId", "operatorName", State.Submitted("fileName", Year.of(2024)), instant, instant),
        )

        when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(deliveredSubmissions)))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(localSubmissions))

        val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
        val result = service.getSubmissions(request).futureValue

        result mustEqual SubmissionSummary(
          deliveredSubmissions.submissions, Nil
        )

        verify(mockConnector, times(1)).get(eqTo(request))(any())
        verify(mockRepository, times(1)).getBySubscriptionId("dprsId")
      }
      
      "when there are delivered submissions and some Submitted local submissions do not have a matching delivered submission" in {

        val deliveredSubmissions = DeliveredSubmissions(
          submissions = Seq(
            DeliveredSubmission("id1", "fileName", "operatorId", "operatorName", "2024", instant, Success, None),
            DeliveredSubmission("id2", "fileName2", "operatorId", "operatorName", "2024", instant, Success, None)
          ),
          resultsCount = 2
        )

        val localSubmissions = Seq(
          Submission(_id = "id3", "dprsId", "operatorId", "operatorName", State.Submitted("fileName3", Year.of(2024)), instant, instant),
          Submission(_id = "id4", "dprsId", "operatorId", "operatorName", State.Submitted("fileName4", Year.of(2024)), instant, instant),
        )

        when(mockConnector.get(any())(any())).thenReturn(Future.successful(Some(deliveredSubmissions)))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(localSubmissions))

        val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
        val result = service.getSubmissions(request).futureValue

        result mustEqual SubmissionSummary(
          deliveredSubmissions.submissions,
          localSubmissions
        )

        verify(mockConnector, times(1)).get(eqTo(request))(any())
        verify(mockRepository, times(1)).getBySubscriptionId("dprsId")
      }
      
      "when there are no delivered submissions and some Submitted local submissions" in {

        val localSubmissions = Seq(
          Submission(_id = "id3", "dprsId", "operatorId", "operatorName", State.Submitted("fileName3", Year.of(2024)), instant, instant),
          Submission(_id = "id4", "dprsId", "operatorId", "operatorName", State.Submitted("fileName4", Year.of(2024)), instant, instant),
        )

        when(mockConnector.get(any())(any())).thenReturn(Future.successful(None))
        when(mockRepository.getBySubscriptionId(any())).thenReturn(Future.successful(localSubmissions))

        val request = ViewSubmissionsRequest("dprsId", false, 1, DeliveredSubmissionSortBy.SubmissionDate, SortOrder.Descending, None, None, None, Nil)
        val result = service.getSubmissions(request).futureValue

        result mustEqual SubmissionSummary(
          Nil,
          localSubmissions
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

      result mustEqual SubmissionSummary(Nil, Nil)

      verify(mockConnector, times(1)).get(eqTo(request))(any())
      verify(mockRepository, times(1)).getBySubscriptionId("dprsId")
    }
  }
}
