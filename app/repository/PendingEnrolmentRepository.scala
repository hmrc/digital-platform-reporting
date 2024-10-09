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

import config.AppConfig
import models.enrolment.PendingEnrolment
import org.apache.pekko.Done
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.*
import org.mongodb.scala.model.Indexes.{ascending, compoundIndex}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PendingEnrolmentRepository @Inject()(mongoComponent: MongoComponent,
                                           appConfig: AppConfig)
                                          (implicit ec: ExecutionContext) extends PlayMongoRepository[PendingEnrolment](
  collectionName = "pending-enrolments",
  mongoComponent = mongoComponent,
  domainFormat = PendingEnrolment.mongoFormat,
  indexes = PendingEnrolmentRepository.indexes(appConfig),
  replaceIndexes = true
) {

  def insert(pendingEnrolment: PendingEnrolment): Future[Done] =
    collection.insertOne(pendingEnrolment).toFuture().map(_ => Done)

  def find(userId: String): Future[Option[PendingEnrolment]] =
    collection.find(userIdFilter(userId)).limit(1).headOption()

  def delete(userId: String): Future[Done] =
    collection.deleteMany(userIdFilter(userId)).toFuture().map(_ => Done)

  private def userIdFilter(userId: String) = Filters.equal("userId", userId)
}

private object PendingEnrolmentRepository {

  private val userIdIndex: Bson = compoundIndex(
    ascending("userId")
  )

  def indexes(appConfig: AppConfig): Seq[IndexModel] = Seq(
    IndexModel(ascending("created"), IndexOptions().name("LastUpdatedTTL").expireAfter(appConfig.MongoPendingEnrolmentTTL.toMinutes, TimeUnit.MINUTES)),
    IndexModel(userIdIndex, IndexOptions().unique(true).name("UserIdIndex")),
  )
}
