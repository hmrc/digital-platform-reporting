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

import models.submission.{CadxValidationError, Submission}
import models.submission.Submission.State.{Ready, Validated}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
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
  private val created = Instant.now().truncatedTo(ChronoUnit.MILLIS)

  private val fileError: CadxValidationError.FileError = CadxValidationError.FileError(
    submissionId = submissionId,
    code = "1",
    detail = Some("some detail"),
    created = created
  )

  private val rowError: CadxValidationError.RowError = CadxValidationError.RowError(
    submissionId = submissionId,
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

      result must contain only (fileError, rowError)
    }
  }

  "getErrorsForSubmission" - {

    "must return all errors for the given submission id" in {

      given Materializer = app.materializer

      insert(fileError).futureValue
      insert(rowError).futureValue
      insert(fileError.copy(submissionId = "submissionId2")).futureValue
      insert(rowError.copy(submissionId = "submissionId3")).futureValue

      val source = repository.getErrorsForSubmission(submissionId)
      val result = source.runWith(Sink.fold(Seq.empty[CadxValidationError])(_ :+ _)).futureValue
      result must contain only (fileError, rowError)
    }
  }
}
