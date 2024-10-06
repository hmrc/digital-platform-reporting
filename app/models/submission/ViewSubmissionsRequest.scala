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

import play.api.libs.functional.syntax.*
import play.api.libs.json.*

final case class ViewSubmissionsRequest(subscriptionId: String,
                                        assumedReporting: Boolean,
                                        pageNumber: Int,
                                        sortBy: DeliveredSubmissionSortBy,
                                        sortOrder: SortOrder,
                                        reportingPeriod: Option[Int],
                                        operatorId: Option[String],
                                        fileName: Option[String],
                                        statuses: Seq[DeliveredSubmissionStatus])

object ViewSubmissionsRequest {

  def apply(subscriptionId: String, inboundRequest: ViewSubmissionsInboundRequest): ViewSubmissionsRequest =
    ViewSubmissionsRequest(
      subscriptionId   = subscriptionId,
      assumedReporting = inboundRequest.assumedReporting,
      pageNumber       = inboundRequest.pageNumber,
      sortBy           = inboundRequest.sortBy,
      sortOrder        = inboundRequest.sortOrder,
      reportingPeriod  = inboundRequest.reportingPeriod,
      operatorId       = inboundRequest.operatorId,
      fileName         = inboundRequest.fileName,
      statuses         = inboundRequest.statuses
      
    )

  implicit lazy val writes: OWrites[ViewSubmissionsRequest] = {
    
    given OWrites[ViewSubmissionsRequest] = (
      (__ \ "subscriptionId").write[String] and
      (__ \ "isManual").write[Boolean] and
      (__ \ "pageNumber").write[Int] and
      (__ \ "sortBy").write[DeliveredSubmissionSortBy] and
      (__ \ "sortOrder").write[SortOrder] and
      (__ \ "reportingYear").writeNullable[String] and
      (__ \ "pOId").writeNullable[String] and
      (__ \ "fileName").writeNullable[String] and
      (__ \ "submissionStatus").writeNullable[String]
    )(request => (
      request.subscriptionId,
      request.assumedReporting,
      request.pageNumber,
      request.sortBy,
      request.sortOrder,
      request.reportingPeriod.map(_.toString),
      request.operatorId,
      request.fileName,
      if(request.statuses.nonEmpty) Some(request.statuses.map(_.entryName).mkString(",")) else None
    ))
    
    OWrites { request =>
      Json.obj(
        "submissionsListRequest" -> Json.obj(
          "requestCommon" -> Json.obj(
            "originatingSystem" -> "MDTP",
            "regime" -> "DPRS",
            "transmittingSystem" -> "EIS"
          ),
          "requestDetails" -> request
        )
      )
    }
  }
}
