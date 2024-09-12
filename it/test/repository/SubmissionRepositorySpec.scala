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

import models.submission.Submission
import models.submission.Submission.State.{Ready, Validated}
import org.mongodb.scala.model.Indexes
import org.scalactic.source.Position
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.slf4j.MDC
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

class SubmissionRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[Submission]
    with IntegrationPatience
    with BeforeAndAfterEach
    with BeforeAndAfterAll
    with GuiceOneAppPerSuite
    with OptionValues {

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(
      bind[MongoComponent].toInstance(mongoComponent),
    )
    .configure(
      "mongodb.submission.ttl" -> "5minutes"
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  override protected val repository: SubmissionRepository =
    app.injector.instanceOf[SubmissionRepository]

  private val created = Instant.now().truncatedTo(ChronoUnit.MILLIS)
  private val updated = created.plus(1, ChronoUnit.DAYS)

  private val submission = Submission(
    _id = "id",
    dprsId = "dprsId",
    state = Ready,
    created = created,
    updated = updated
  )

  "must have the correct ttl index" in {
    val ttlIndex = repository.indexes.find(_.getOptions.getName == "updated_ttl_idx").value
    ttlIndex.getOptions.getExpireAfter(TimeUnit.MINUTES) mustEqual 5
    ttlIndex.getKeys mustEqual Indexes.ascending("updated")
  }

  "save" - {

    "must insert a submission into mongo when there is not matching submission" in {
      findAll().futureValue mustBe empty
      repository.save(submission).futureValue
      findAll().futureValue must contain only submission
    }

    "must update a submission in mongo when a there is a matching submission" in {
      insert(submission.copy(state = Validated(url"http://example.com", "poid", "fileName", 1337))).futureValue
      repository.save(submission).futureValue
      findAll().futureValue must contain only submission
    }

    mustPreserveMdc(repository.save(submission))
  }

  "get" - {

    "must retrieve the right submission from mongo" in {
      insert(submission).futureValue
      repository.get("dprsId", "id").futureValue.value mustEqual submission
    }

    "must return None when the requested submission does not exist" in {
      repository.get("dprsId", "id").futureValue mustBe None
    }

    "must return None when the dprsId matches but the id does not" in {
      insert(submission).futureValue
      repository.get("dprsId", "id2").futureValue mustBe None
    }

    "must return None when the id matches but the dprsId doesn not" in {
      insert(submission).futureValue
      repository.get("dprsId2", "id").futureValue mustBe None
    }

    mustPreserveMdc(repository.get("dprsId", "id"))
  }

  private def mustPreserveMdc[A](f: => Future[A])(implicit pos: Position): Unit =
    "must preserve MDC" in {

      val ec = app.injector.instanceOf[ExecutionContext]

      MDC.put("test", "foo")

      f.map { _ =>
        MDC.get("test") mustEqual "foo"
      }(ec).futureValue
    }
}
