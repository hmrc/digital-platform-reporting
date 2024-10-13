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
import models.submission.Submission
import org.apache.pekko.Done
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.mongodb.scala.model.*
import play.api.Configuration
import play.api.libs.json.{Json, OWrites}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.play.http.logging.Mdc

import java.time.{Clock, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

final case class IdAndLastUpdated(id:String, lastUpdated: Instant)

object IdAndLastUpdated {
  implicit val writes: OWrites[IdAndLastUpdated] = Json.writes
}

@Singleton
class SubmissionRepository @Inject() (
                                       mongoComponent: MongoComponent,
                                       configuration: Configuration,
                                       clock: Clock,
                                       appConfig: AppConfig
                                     )(implicit val ec: ExecutionContext) extends PlayMongoRepository[Submission](
  collectionName = "submissions",
  mongoComponent = mongoComponent,
  domainFormat = Submission.mongoFormat,
  indexes = SubmissionRepository.indexes(configuration),
  replaceIndexes = true
) {
  def save(submission: Submission): Future[Done] = Mdc.preservingMdc {
    collection.replaceOne(
      filter = Filters.and(
        Filters.eq("_id", submission._id),
        Filters.eq("dprsId", submission.dprsId)
      ),
      replacement = submission,
      options = ReplaceOptions()
        .upsert(true)
    ).toFuture().map(_ => Done)
  }

  def get(dprsId: String, id: String): Future[Option[Submission]] = Mdc.preservingMdc {
    collection.find(
      filter = Filters.and(
        Filters.eq("_id", id),
        Filters.eq("dprsId", dprsId)
      )
    ).limit(1).headOption()
  }

  def getBlockedSubmissionIds(): Future[Seq[IdAndLastUpdated]] = {
    val cutoff = Instant.now(clock).minus(appConfig.blockedSubmissionThreshold)
    Mdc.preservingMdc {
      collection.find(
          filter = Filters.and(
            Filters.eq("state.type", "Submitted"),
            Filters.lt("updated", cutoff)
          )
        ).limit(1000)
        .map(s => IdAndLastUpdated(s._id, s.updated))
        .toFuture()
    }
  }
  
  def getBySubscriptionId(dprsId: String): Future[Seq[Submission]] = Mdc.preservingMdc {
    collection.find(
      filter = Filters.eq("dprsId", dprsId)
    ).toFuture()
  }

  def getById(id: String): Future[Option[Submission]] = Mdc.preservingMdc {
    collection.find(
      filter = Filters.eq("_id", id)
    ).limit(1).headOption()
  }
}

object SubmissionRepository {
  def indexes(configuration: Configuration): Seq[IndexModel] =
    Seq(
      IndexModel(
        Indexes.ascending("updated"),
        IndexOptions()
          .name("updated_ttl_idx")
          .expireAfter(configuration.get[Duration]("mongodb.submission.ttl").toMinutes, TimeUnit.MINUTES)
      ),
      IndexModel(
        Indexes.ascending("dprsId"),
        IndexOptions()
          .name("dprs_id_idx")
      ),
      IndexModel(
        Indexes.ascending("state.type"),
        IndexOptions()
          .name("state_type_idx")
      )
    )
}
