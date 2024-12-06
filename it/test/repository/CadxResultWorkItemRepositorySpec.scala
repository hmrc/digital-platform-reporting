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

import models.sdes.CadxResultWorkItem
import org.mongodb.scala.model.Indexes
import org.scalatest.OptionValues
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import support.SpecBase
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}

import java.time.Instant
import java.util.concurrent.TimeUnit

class CadxResultWorkItemRepositorySpec extends SpecBase
  with DefaultPlayMongoRepositorySupport[WorkItem[CadxResultWorkItem]]
  with IntegrationPatience
  with GuiceOneAppPerSuite
  with OptionValues {

  override def fakeApplication(): Application = GuiceApplicationBuilder()
    .overrides(bind[MongoComponent].toInstance(mongoComponent))
    .configure(
      "mongodb.cadx-result.ttl" -> "5minutes"
    )
    .build()

  override protected val repository: CadxResultWorkItemRepository =
    app.injector.instanceOf[CadxResultWorkItemRepository]

  private val now: Instant = Instant.now()

  "must have the correct ttl index" in {
    val ttlIndex = repository.indexes.find(_.getOptions.getName == "updated_ttl_idx").value
    ttlIndex.getOptions.getExpireAfter(TimeUnit.MINUTES) mustEqual 5
    ttlIndex.getKeys mustEqual Indexes.ascending("receivedAt")
  }

  ".listWorkItems" - {

    "must list work items" in {

      repository.pushNew(CadxResultWorkItem("1"), now.minusSeconds(1), _ => ProcessingStatus.ToDo).futureValue
      repository.pushNew(CadxResultWorkItem("2"), now, _ => ProcessingStatus.Failed).futureValue
      repository.pushNew(CadxResultWorkItem("3"), now.plusSeconds(1), _ => ProcessingStatus.PermanentlyFailed).futureValue

      val result = repository.listWorkItems(Set.empty, 10, 0).futureValue

      result.map(_.item) mustEqual Seq(
        CadxResultWorkItem("3"),
        CadxResultWorkItem("2"),
        CadxResultWorkItem("1")
      )
    }

    "must filter work items by the given statuses" in {

      repository.pushNew(CadxResultWorkItem("1"), now.minusSeconds(1), _ => ProcessingStatus.ToDo).futureValue
      repository.pushNew(CadxResultWorkItem("2"), now, _ => ProcessingStatus.Failed).futureValue
      repository.pushNew(CadxResultWorkItem("3"), now.plusSeconds(1), _ => ProcessingStatus.PermanentlyFailed).futureValue

      val result = repository.listWorkItems(Set(ProcessingStatus.ToDo, ProcessingStatus.Failed), 10, 0).futureValue

      result.map(_.item) mustEqual Seq(
        CadxResultWorkItem("2"),
        CadxResultWorkItem("1")
      )
    }

    "must allow pagination using limit and offset" in {

      repository.pushNew(CadxResultWorkItem("1"), now.minusSeconds(1), _ => ProcessingStatus.ToDo).futureValue
      repository.pushNew(CadxResultWorkItem("2"), now, _ => ProcessingStatus.Failed).futureValue
      repository.pushNew(CadxResultWorkItem("3"), now.plusSeconds(1), _ => ProcessingStatus.PermanentlyFailed).futureValue

      val result1 = repository.listWorkItems(Set(ProcessingStatus.ToDo, ProcessingStatus.Failed), 1, 0).futureValue
      val result2 = repository.listWorkItems(Set(ProcessingStatus.ToDo, ProcessingStatus.Failed), 1, 1).futureValue

      result1.map(_.item) mustEqual Seq(
        CadxResultWorkItem("2")
      )

      result2.map(_.item) mustEqual Seq(
        CadxResultWorkItem("1")
      )
    }
  }
}
