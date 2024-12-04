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

import models.confirmed.ConfirmedReportingNotification
import org.mongodb.scala.model.Indexes
import org.scalactic.source.Position
import org.scalatest.OptionValues
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import support.builders.ConfirmedReportingNotificationBuilder.aConfirmedReportingNotification
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.util.concurrent.TimeUnit

class ConfirmedReportingNotificationsRepositorySpec extends AnyFreeSpec
  with Matchers
  with DefaultPlayMongoRepositorySupport[ConfirmedReportingNotification]
  with IntegrationPatience
  with GuiceOneAppPerSuite
  with OptionValues {

  protected val config: Map[String, String] = Map(
    "mongodb.confirmed-reporting-notifications.ttl" -> "24hours"
  )

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(bind[MongoComponent].toInstance(mongoComponent))
    .configure(config)
    .build()

  override protected val repository: ConfirmedReportingNotificationsRepository = app.injector.instanceOf[ConfirmedReportingNotificationsRepository]

  "Indexes" - {
    "must have the correct ttl index" in {
      val ttlIndex = repository.indexes.find(_.getOptions.getName == "created_ttl_idx").value
      ttlIndex.getOptions.getExpireAfter(TimeUnit.HOURS) mustEqual 24
      ttlIndex.getKeys mustEqual Indexes.ascending("created")
    }

    "must have the correct userId-operatorId-idx index" in {
      val ttlIndex = repository.indexes.find(_.getOptions.getName == "userId-operatorId-idx").value
      ttlIndex.getKeys mustEqual Indexes.ascending("userId", "operatorId")
    }
  }

  ".save(...)" - {
    "must add ConfirmedReportingNotification if does not exist in DB" in {
      repository.save(aConfirmedReportingNotification).futureValue

      val result = findAll().futureValue

      result must contain only aConfirmedReportingNotification
    }
  }

  ".findBy(...)" - {
    "must find ConfirmedReportingNotification when exist" in {
      repository.save(aConfirmedReportingNotification).futureValue

      val result = repository.findBy(aConfirmedReportingNotification.userId, aConfirmedReportingNotification.operatorId).futureValue

      result mustBe Some(aConfirmedReportingNotification)
    }

    "must return None when ConfirmedReportingNotification does not exist" in {
      val result = repository.findBy("unknown-user-id", "unknown-operator-id").futureValue

      result mustBe None
    }
  }
}
