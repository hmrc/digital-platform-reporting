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
      deliveredSubmissions <- connector.get(request)
      localSubmissions <- repository.getBySubscriptionId(request.subscriptionId)
      localSubmissionIds = localSubmissions.map(_._id)
      repositorySubmissionCount <- repository.countSubmittedXmlSubmissions(request.subscriptionId)
    } yield {

      val submissions = deliveredSubmissions.map(_.submissions).getOrElse(Nil)
      val deliveredSubmissionSummaries = submissions.map(s => SubmissionSummary(s, false, localSubmissionIds.contains(s.conversationId)))
      val deliveredSubmissionsCount = deliveredSubmissions.map(_.resultsCount).getOrElse(0)

      SubmissionsSummary(
        deliveredSubmissions = deliveredSubmissionSummaries,
        deliveredSubmissionRecordCount = deliveredSubmissionsCount,
        deliveredSubmissionsExist = deliveredSubmissions.nonEmpty,
        undeliveredSubmissionCount = repositorySubmissionCount
      )
    }

  def getUndeliveredSubmissions(dprsId: String)(implicit hc: HeaderCarrier): Future[Seq[SubmissionSummary]] =
    repository.getSubmittedXmlSubmissions(dprsId).map { submissions =>
      submissions
        .sortBy(_.created)
        .reverse
        .flatMap(x => SubmissionSummary(x))
    }

  def getAssumedReports(dprsId: String, operatorId: Option[String] = None)
                       (implicit hc: HeaderCarrier): Future[Seq[SubmissionSummary]] =
    getAllAssumedReportingSubmissions(dprsId, operatorId).flatMap { deliveredSubmissions =>
      repository.getBySubscriptionId(dprsId).flatMap { localSubmissions =>

        val localSubmissionIds = localSubmissions.map(_._id)

        val consolidatedSubmissions = deliveredSubmissions
          .groupBy(submission => (submission.operatorId, submission.reportingPeriod))
          .map(_._2.sortBy(_.submissionDateTime).reverse.head)
          .toList
          .sortBy(_.submissionDateTime).reverse

        consolidatedSubmissions.traverse { submission =>
            assumedReportingService
              .getSubmission(dprsId, submission.operatorId, submission.reportingPeriod)
              .map(_.map(assumedReport => SubmissionSummary(submission, assumedReport.isDeleted, localSubmissionIds.contains(submission.conversationId))))
          }
          .map(_.flatten)
      }
    }

  private def getAllAssumedReportingSubmissions(dprsId: String, operatorId: Option[String] = None)
                                               (implicit hc: HeaderCarrier): Future[Seq[DeliveredSubmission]] =
    connector.get(buildAssumedReportingRequest(1, dprsId, operatorId)).flatMap(_.map { page1 =>
      if (page1.resultsCount <= 10) {
        Future.successful(page1.submissions)
      } else {
        val numberOfPages = (page1.resultsCount + 9) / 10

        (2 to numberOfPages).toList.traverse { page =>
          connector.get(buildAssumedReportingRequest(page, dprsId, operatorId)).flatMap(_.map { result =>
            Future.successful(result.submissions)
          }.getOrElse(Future.successful(Nil)))
        }.map(_.flatten ++ page1.submissions)
      }
    }.getOrElse(Future.successful(Nil)))

  private def buildAssumedReportingRequest(page: Int,
                                           dprsId: String,
                                           operatorId: Option[String] = None) =
    ViewSubmissionsRequest(
      subscriptionId = dprsId,
      assumedReporting = true,
      pageNumber = page,
      sortBy = SubmissionDate,
      sortOrder = Descending,
      reportingPeriod = None,
      operatorId = operatorId,
      fileName = None,
      statuses = Seq(Pending, Success, Rejected)
    )
}
