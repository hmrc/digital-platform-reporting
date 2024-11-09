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

import cats.implicits.*
import connectors.DeliveredSubmissionConnector
import models.submission.*
import models.submission.DeliveredSubmissionSortBy.SubmissionDate
import models.submission.SortOrder.Descending
import models.submission.SubmissionStatus.{Pending, Rejected, Success}
import repository.SubmissionRepository
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ViewSubmissionsService @Inject()(connector: DeliveredSubmissionConnector,
                                       repository: SubmissionRepository,
                                       assumedReportingService: AssumedReportingService)
                                      (implicit ec: ExecutionContext) {

  def getDeliveredSubmissions(request: ViewSubmissionsRequest)(implicit hc: HeaderCarrier): Future[SubmissionsSummary] =
    for {
      deliveredSubmissions  <- connector.get(request)
      repositorySubmissions <- repository.getBySubscriptionId(request.subscriptionId)
    } yield {

      val deliveredSubmissionSummaries = deliveredSubmissions.map(_.submissions.map(x => SubmissionSummary(x, false))).getOrElse(Nil)
      val deliveredSubmissionsCount = deliveredSubmissions.map(_.resultsCount).getOrElse(0)
      val undeliveredSubmissionCount =
        repositorySubmissions
          .filter(_.submissionType == Submission.SubmissionType.Xml)
          .count(submitted)

      SubmissionsSummary(
        deliveredSubmissionSummaries,
        deliveredSubmissionsCount,
        deliveredSubmissions.nonEmpty,
        undeliveredSubmissionCount
      )
    }
    
  def getUndeliveredSubmissions(dprsId: String)(implicit hc: HeaderCarrier): Future[Seq[SubmissionSummary]] =
    repository.getBySubscriptionId(dprsId).map { submissions =>
      submissions
        .filter(_.submissionType == Submission.SubmissionType.Xml)
        .filter(submitted)
        .sortBy(_.created)
        .reverse
        .flatMap(x => SubmissionSummary(x))
    }
    
  private def submitted: Submission => Boolean =
    submission => submission.state match {
      case _: Submission.State.Submitted => true
      case _                             => false
    }

  def getAssumedReports(dprsId: String)(implicit hc: HeaderCarrier): Future[Seq[SubmissionSummary]] = {
    getAllAssumedReportingSubmissions(dprsId).flatMap { deliveredSubmissions =>
      val consolidatedSubmissions = deliveredSubmissions
        .groupBy(submission => (submission.operatorId, submission.reportingPeriod))
        .map(_._2.sortBy(_.submissionDateTime).reverse.head)
        .toList
        .sortBy(_.submissionDateTime).reverse
        
      consolidatedSubmissions.traverse { submission =>
        assumedReportingService
          .getSubmission(dprsId, submission.operatorId, submission.reportingPeriod)
          .map(_.map(assumedReport => SubmissionSummary(submission, assumedReport.isDeleted)))
      }
      .map(_.flatten)
    }
  }

  private def getAllAssumedReportingSubmissions(dprsId: String)(implicit hc: HeaderCarrier): Future[Seq[DeliveredSubmission]] =
    connector.get(buildAssumedReportingRequest(dprsId, 1)).flatMap(_.map { page1 =>
      if (page1.resultsCount <= 10) {
        Future.successful(page1.submissions)
      } else {
        val numberOfPages = (page1.resultsCount + 9) / 10

        (2 to numberOfPages).toList.traverse { page =>
          connector.get(buildAssumedReportingRequest(dprsId, page)).flatMap(_.map { result =>
            Future.successful(result.submissions)
          }.getOrElse(Future.successful(Nil)))
        }.map(_.flatten ++ page1.submissions)
      }
    }.getOrElse(Future.successful(Nil)))

  private def buildAssumedReportingRequest(dprsId: String, page: Int): ViewSubmissionsRequest =
    ViewSubmissionsRequest(
      subscriptionId = dprsId,
      assumedReporting = true,
      pageNumber = page,
      sortBy = SubmissionDate,
      sortOrder = Descending,
      reportingPeriod = None,
      operatorId = None,
      fileName = None,
      statuses = Seq(Pending, Success, Rejected)
    )
}
