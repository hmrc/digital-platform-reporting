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
import java.time.Year
import scala.concurrent.{ExecutionContext, Future}

class ViewSubmissionsService @Inject()(connector: DeliveredSubmissionConnector,
                                       repository: SubmissionRepository,
                                       assumedReportingService: AssumedReportingService)
                                      (implicit ec: ExecutionContext) {

  def getSubmissions(request: ViewSubmissionsRequest)(implicit hc: HeaderCarrier): Future[SubmissionsSummary] =
    for {
      deliveredSubmissions  <- connector.get(request)
      repositorySubmissions <- repository.getBySubscriptionId(request.subscriptionId)
    } yield {

      val deliveredSubmissionSummaries = deliveredSubmissions.map(_.submissions.map(x => SubmissionSummary(x))).getOrElse(Nil)
      val deliveredSubmissionIds = deliveredSubmissionSummaries.map(_.submissionId)
      val undeliveredSubmissions =
        repositorySubmissions
          .filter(x => !deliveredSubmissionIds.contains(x._id))
          .flatMap(x => SubmissionSummary(x))

      SubmissionsSummary(deliveredSubmissionSummaries, undeliveredSubmissions)
    }

  def getAssumedReports(dprsId: String)(implicit hc: HeaderCarrier): Future[SubmissionsSummary] = {
    val request = ViewSubmissionsRequest(
      subscriptionId = dprsId,
      assumedReporting = true,
      pageNumber = 1,
      sortBy = SubmissionDate,
      sortOrder = Descending,
      reportingPeriod = None,
      operatorId = None,
      fileName = None,
      statuses = Seq(Pending, Success, Rejected)
    )

    connector.get(request).flatMap(_.map { deliveredSubmissions =>
      val consolidatedSubmissions = deliveredSubmissions.submissions
        .groupBy(submission => (submission.operatorId, submission.reportingPeriod))
        .map(_._2.sortBy(_.submissionDateTime).reverse.head)
        .toList
        .sortBy(_.submissionDateTime).reverse
        
      consolidatedSubmissions.traverse { submission =>
        assumedReportingService
          .getSubmission(dprsId, submission.operatorId, Year.of(submission.reportingPeriod.toInt)) // TODO: Get rid of toInt
          .map(_.map(assumedReport => SubmissionSummary(submission, assumedReport.isDeleted)))
      }
      .map(submissionSummaries => SubmissionsSummary(submissionSummaries.flatten, Nil))
    }.getOrElse(Future.successful(SubmissionsSummary(Nil, Nil))))
  }
}
