/*
 * Copyright 2025 HM Revenue & Customs
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

package config

import com.codahale.metrics.MetricRegistry
import com.google.inject.Inject
import play.api.Configuration
import repository.SubmissionRepository
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}
import uk.gov.hmrc.mongo.metrix.{MetricOrchestrator, MetricRepository}

import javax.inject.{Provider, Singleton}
import scala.concurrent.duration.Duration

@Singleton
class MetricOrchestratorProvider @Inject() (
                                             submissionRepository: SubmissionRepository,
                                             lockRepository: MongoLockRepository,
                                             metricRegistry: MetricRegistry,
                                             metricRepository: MetricRepository,
                                             configuration: Configuration
                                           ) extends Provider[MetricOrchestrator] {

  private val lockTtl: Duration = configuration.get[Duration]("workers.metric-orchestrator-worker.lock-ttl")

  private val lockService: LockService = LockService(lockRepository, lockId = "metrix-orchestrator", ttl = lockTtl)

  override def get(): MetricOrchestrator = new MetricOrchestrator(
    metricSources = List(submissionRepository),
    lockService = lockService,
    metricRepository = metricRepository,
    metricRegistry = metricRegistry
  )
}
