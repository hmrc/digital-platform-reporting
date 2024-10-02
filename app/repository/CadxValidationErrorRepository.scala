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

import models.submission.CadxValidationError
import org.apache.pekko.Done
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.ObservableFuture
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes}
import play.api.Configuration
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration

@Singleton
class CadxValidationErrorRepository @Inject() (
                                                mongoComponent: MongoComponent,
                                                configuration: Configuration,
                                              )(implicit ec: ExecutionContext) extends PlayMongoRepository[CadxValidationError](
  collectionName = "cadx-validation-errors",
  mongoComponent = mongoComponent,
  domainFormat = CadxValidationError.mongoFormat,
  indexes = CadxValidationErrorRepository.indexes(configuration),
  replaceIndexes = true
) {

  def save(validationError: CadxValidationError): Future[Done] =
    collection.insertOne(validationError).toFuture().map(_ => Done)

  // TODO should this include a limit?
  def getErrorsForSubmission(submissionId: String): Future[Seq[CadxValidationError]] =
    collection.find(Filters.eq("submissionId", submissionId)).toFuture()
}

object CadxValidationErrorRepository {

  def indexes(configuration: Configuration): Seq[IndexModel] =
    Seq(
      IndexModel(
        Indexes.ascending("created"),
        IndexOptions()
          .name("created_ttl_idx")
          .expireAfter(configuration.get[Duration]("mongodb.cadx-validation-errors.ttl").toMinutes, TimeUnit.MINUTES)
      ),
      IndexModel(
        Indexes.ascending("submissionId"),
        IndexOptions()
          .name("submissionId_idx")
      )
    )
}