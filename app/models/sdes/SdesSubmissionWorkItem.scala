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

package models.sdes

import models.audit.AddSubmissionEvent
import models.urlFormat
import models.subscription.responses.SubscriptionInfo
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.net.URL

final case class SdesSubmissionWorkItem(
                                         submissionId: String,
                                         downloadUrl: URL,
                                         fileName: String,
                                         checksum: String,
                                         size: Long,
                                         subscriptionInfo: SubscriptionInfo,
                                         auditEvent: AddSubmissionEvent
                                       )

object SdesSubmissionWorkItem extends MongoJavatimeFormats.Implicits {

  given mongoFormat: OFormat[SdesSubmissionWorkItem] = {
    given OFormat[SubscriptionInfo] = SubscriptionInfo.mongoFormat
    given OFormat[AddSubmissionEvent] = AddSubmissionEvent.mongoFormat
    Json.format
  }
}
