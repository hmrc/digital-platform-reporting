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

import com.mongodb.client.model.IndexModel
import config.AppConfig
import models.confirmed.ConfirmedBusinessDetails
import org.apache.pekko.Done
import org.mongodb.scala.model.*
import org.mongodb.scala.model.Filters.{and, equal}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.toBson
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConfirmedBusinessDetailsRepository @Inject()(mongoComponent: MongoComponent,
                                                   appConfig: AppConfig)
                                                  (using ExecutionContext) extends PlayMongoRepository[ConfirmedBusinessDetails](
  collectionName = "confirmed-business-details",
  mongoComponent = mongoComponent,
  domainFormat = ConfirmedBusinessDetails.mongoFormat,
  indexes = ConfirmedBusinessDetailsRepository.indexes(appConfig)
) {

  def save(contactDetails: ConfirmedBusinessDetails): Future[Done] = collection
    .findOneAndReplace(
      filter = Filters.and(
        Filters.eq("userId", contactDetails.userId),
        Filters.eq("operatorId", contactDetails.operatorId)
      ),
      replacement = contactDetails,
      options = FindOneAndReplaceOptions().upsert(true)
    )
    .toFuture()
    .map(_ => Done)

  def findBy(userId: String, operatorId: String): Future[Option[ConfirmedBusinessDetails]] =
    collection.find(and(
      equal("userId", toBson(userId)),
      equal("operatorId", toBson(operatorId))
    )).limit(1).headOption()
}

private object ConfirmedBusinessDetailsRepository {

  def indexes(appConfig: AppConfig): Seq[IndexModel] = Seq(
    IndexModel(Indexes.ascending("created"), IndexOptions().name("created_ttl_idx").expireAfter(appConfig.ConfirmedBusinessDetailsTtl.toHours, TimeUnit.HOURS)),
    IndexModel(Indexes.ascending("userId", "operatorId"), IndexOptions().name("userId-operatorId-idx").unique(true))
  )
}

