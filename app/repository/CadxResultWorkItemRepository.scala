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

package repository

import models.sdes.CadxResultWorkItem
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes, Sorts}
import play.api.Configuration
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem, WorkItemFields, WorkItemRepository}

import java.time.{Duration, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CadxResultWorkItemRepository @Inject()(
                                              configuration: Configuration,
                                              mongoComponent: MongoComponent,
                                            )(using ExecutionContext) extends WorkItemRepository[CadxResultWorkItem](
  collectionName = "cadxResultWorkItems",
  mongoComponent = mongoComponent,
  itemFormat = CadxResultWorkItem.mongoFormat,
  workItemFields = WorkItemFields.default,
  extraIndexes = Seq(
    IndexModel(
      Indexes.ascending("receivedAt"),
      IndexOptions()
        .name("updated_ttl_idx")
        .expireAfter(configuration.get[Duration]("mongodb.cadx-result.ttl").toMinutes, TimeUnit.MINUTES)
    )
  ),
  extraCodecs = Codecs.playFormatSumCodecs(ProcessingStatus.format)
) {

  override def now(): Instant =
    Instant.now()

  override val inProgressRetryAfter: Duration =
    configuration.get[Duration]("sdes.cadx-result.retry-after")

  def listWorkItems(statuses: Set[ProcessingStatus], limit: Int, offset: Int): Future[Seq[WorkItem[CadxResultWorkItem]]] = {

    val filter = if (statuses.isEmpty) {
      Filters.empty()
    } else {
      Filters.in("status", statuses.toSeq.map(_.name)*)
    }

    collection
      .find(filter)
      .sort(Sorts.descending("receivedAt"))
      .skip(offset)
      .limit(limit)
      .toFuture()
  }
}