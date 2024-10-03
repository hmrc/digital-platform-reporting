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

package models.submission

import play.api.libs.json.{Json, Reads}

final case class DeliveredSubmissionInboundRequest(assumedReporting: Boolean,
                                                   pageNumber: Int = 1, // TODO: Check if this is 0-based
                                                   sortBy: DeliveredSubmissionSortBy = DeliveredSubmissionSortBy.SubmissionDate,
                                                   sortOrder: SortOrder = SortOrder.Descending,
                                                   reportingPeriod: Option[Int] = None,
                                                   operatorId: Option[String] = None,
                                                   fileName: Option[String] = None,
                                                   statuses: Seq[DeliveredSubmissionStatus] = Nil)

object DeliveredSubmissionInboundRequest {

  implicit lazy val reads: Reads[DeliveredSubmissionInboundRequest] = Json.using[Json.WithDefaultValues].reads
}