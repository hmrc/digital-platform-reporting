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

import models.enrolment.PendingEnrolment
import org.mongodb.scala.model.Indexes
import org.scalatest.OptionValues
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import support.SpecBase
import support.builders.PendingEnrolmentBuilder.aPendingEnrolment
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.util.concurrent.TimeUnit

class PendingEnrolmentRepositorySpec extends SpecBase
  with DefaultPlayMongoRepositorySupport[PendingEnrolment]
  with IntegrationPatience
  with GuiceOneAppPerSuite
  with OptionValues {

  protected val config: Map[String, String] = Map(
    "mongodb.pending-enrolment.ttl" -> "5minutes"
  )

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(bind[MongoComponent].toInstance(mongoComponent))
    .configure(config)
    .build()

  override protected val repository: PendingEnrolmentRepository =
    app.injector.instanceOf[PendingEnrolmentRepository]

  "must have the correct ttl index" in {
    val ttlIndex = repository.indexes.find(_.getOptions.getName == "LastUpdatedTTL").value
    ttlIndex.getOptions.getExpireAfter(TimeUnit.MINUTES) mustEqual 5
    ttlIndex.getKeys mustEqual Indexes.ascending("created")
  }

  ".insert" - {
    "must insert the pending enrolment to the repository" in {
      repository.insert(aPendingEnrolment).futureValue

      val result = findAll().futureValue

      result must contain only aPendingEnrolment
    }
  }

  ".find" - {
    "must retrieve the right pending enrolment" in {
      insert(aPendingEnrolment).futureValue
      repository.find(aPendingEnrolment.userId).futureValue.value mustBe aPendingEnrolment
    }

    "must return None when the requested pending enrolment does not exist" in {
      repository.find("unknown-id").futureValue mustBe None
    }
  }
}
