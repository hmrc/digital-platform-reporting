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
import models.submission.{DeliveredSubmissionRequest, Submission, SubmissionSummary}
import repository.SubmissionRepository
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ViewSubmissionsService @Inject()(connector: DeliveredSubmissionConnector,
                                       repository: SubmissionRepository)
                                      (implicit ec: ExecutionContext) {

  def getSubmissions(request: DeliveredSubmissionRequest)(implicit hc: HeaderCarrier): Future[SubmissionSummary] =
    for {
      deliveredSubmissions  <- connector.get(request)
      repositorySubmissions <- repository.getBySubscriptionId(request.subscriptionId)
    } yield {

      val deliveredSubmissionIds = deliveredSubmissions.map(_.submissions.map(_.conversationId)).getOrElse(Nil)
      val undeliveredSubmissions =
        repositorySubmissions
          .filter(x => !deliveredSubmissionIds.contains(x._id))
          .flatMap { submission =>
            submission.state match {
              case s: Submission.State.Submitted => Some(submission)
              case _ => None
            }
          }

      SubmissionSummary(deliveredSubmissions.map(_.submissions).getOrElse(Nil), undeliveredSubmissions)
    }
}
