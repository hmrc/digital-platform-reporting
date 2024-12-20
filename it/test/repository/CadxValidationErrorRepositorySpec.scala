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
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.mongodb.scala.model.Indexes
import org.scalactic.source.Position
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, OptionValues}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class CadxValidationErrorRepositorySpec
  extends AnyFreeSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[CadxValidationError]
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
      "mongodb.cadx-validation-errors.ttl" -> "5minutes"
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
  }

  override protected val repository: CadxValidationErrorRepository =
    app.injector.instanceOf[CadxValidationErrorRepository]

  private val submissionId = "submissionId"
  private val dprsId = "operatorId"
  private val created = Instant.now().truncatedTo(ChronoUnit.MILLIS)

  private val fileError: CadxValidationError.FileError = CadxValidationError.FileError(
    submissionId = submissionId,
    dprsId = dprsId,
    code = "1",
    detail = Some("some detail"),
    created = created
  )

  private val rowError: CadxValidationError.RowError = CadxValidationError.RowError(
    submissionId = submissionId,
    dprsId = dprsId,
    code = "2",
    detail = Some("some more detail"),
    docRef = "docRef",
    created = created
  )

  "must have the correct ttl index" in {
    val ttlIndex = repository.indexes.find(_.getOptions.getName == "created_ttl_idx").value
    ttlIndex.getOptions.getExpireAfter(TimeUnit.MINUTES) mustEqual 5
    ttlIndex.getKeys mustEqual Indexes.ascending("created")
  }

  "save" - {

    "must save the error to the repository" in {

      repository.save(fileError).futureValue
      repository.save(rowError).futureValue

      val result = findAll().futureValue

      result must contain only(fileError, rowError)
    }
  }

  "saveBatch" - {

    "must save the errors to the repository" in {

      repository.saveBatch(Seq(fileError, rowError)).futureValue

      val result = findAll().futureValue

      result must contain only(fileError, rowError)
    }
  }

  "getErrorsForSubmission" - {

    "when there are fewer errors than the limit" - {

      "must return all errors for the given submission id" in {

        given Materializer = app.materializer

        insert(fileError).futureValue
        insert(rowError).futureValue
        insert(fileError.copy(dprsId = "dprsId2")).futureValue
        insert(rowError.copy(dprsId = "dprsId3")).futureValue
        insert(fileError.copy(submissionId = "submissionId2")).futureValue
        insert(rowError.copy(submissionId = "submissionId3")).futureValue

        val source = repository.getErrorsForSubmission(dprsId, submissionId, 10)
        val result = source.runWith(Sink.fold(Seq.empty[CadxValidationError])(_ :+ _)).futureValue
        result must contain only(fileError, rowError)
      }
    }

    "when there are more errors than the limit" - {

      "must only return errors up to the limit" in {

        given Materializer = app.materializer

        val fileError2 = fileError.copy(code = "2")
        val fileError3 = fileError.copy(code = "3")

        insert(fileError).futureValue
        insert(fileError2).futureValue
        insert(fileError3).futureValue

        val source = repository.getErrorsForSubmission(dprsId, submissionId, 2)
        val result = source.runWith(Sink.fold(Seq.empty[CadxValidationError])(_ :+ _)).futureValue
        result must contain only(fileError, fileError2)
      }
    }
  }
}
