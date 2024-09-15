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
import org.apache.pekko.Done
import repository.SdesSubmissionWorkItemRepository

import java.time.Clock
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SdesService @Inject()(
                             clock: Clock,
                             workItemRepository: SdesSubmissionWorkItemRepository
                           )(using ExecutionContext) {

  def submit(submissionId: String, state: Validated, subscription: SubscriptionInfo): Future[Done] = {

    val workItem = SdesSubmissionWorkItem(
      submissionId = submissionId,
      downloadUrl = state.downloadUrl,
      checksum = state.checksum,
      size = state.size,
      subscriptionInfo = subscription
    )

    workItemRepository.pushNew(workItem, clock.instant()).map(_ => Done)
  }
}
