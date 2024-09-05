package repository

import models.submission.Submission
import org.apache.pekko.Done
import org.mongodb.scala.SingleObservableFuture
import org.mongodb.scala.model.*
import play.api.Configuration
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.play.http.logging.Mdc

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmissionRepository @Inject() (
                                       mongoComponent: MongoComponent,
                                       configuration: Configuration
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
    )
}
