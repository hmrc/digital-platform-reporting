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
import models.confirmed.ConfirmedContactDetails
import org.apache.pekko.Done
import org.mongodb.scala.model.*
import org.mongodb.scala.model.Filters.equal
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.toBson
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConfirmedContactDetailsRepository @Inject()(mongoComponent: MongoComponent,
                                                  appConfig: AppConfig)
                                                 (using ExecutionContext) extends PlayMongoRepository[ConfirmedContactDetails](
  collectionName = "confirmed-contact-details",
  mongoComponent = mongoComponent,
  domainFormat = ConfirmedContactDetails.mongoFormat,
  indexes = ConfirmedContactDetailsRepository.indexes(appConfig)
) {

  def save(contactDetails: ConfirmedContactDetails): Future[Done] = collection
    .findOneAndReplace(
      filter = equal("userId", contactDetails.userId),
      replacement = contactDetails,
      options = FindOneAndReplaceOptions().upsert(true)
    )
    .toFuture()
    .map(_ => Done)

  def findBy(userId: String): Future[Option[ConfirmedContactDetails]] =
    collection.find(equal("userId", toBson(userId))).limit(1).headOption()
}

private object ConfirmedContactDetailsRepository {

  def indexes(appConfig: AppConfig): Seq[IndexModel] = Seq(
    IndexModel(Indexes.ascending("created"), IndexOptions().name("created_ttl_idx").expireAfter(appConfig.ConfirmedContactDetailsTtl.toHours, TimeUnit.HOURS)),
    IndexModel(Indexes.ascending("userId"), IndexOptions().name("userId-idx").unique(true))
  )
}

