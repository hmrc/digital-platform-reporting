/*
 * Copyright 2026 HM Revenue & Customs
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

package workers

import logging.Logging
import org.apache.pekko.actor.ActorSystem
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import services.UploadSuccessService

import javax.inject.Inject
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class UploadSuccessWorker @Inject()(
                                     actorSystem: ActorSystem,
                                     configuration: Configuration,
                                     uploadSuccessService: UploadSuccessService,
                                     applicationLifecycle: ApplicationLifecycle
                                   )(using ExecutionContext) extends Logging {

  private val initialDelay =
    configuration.get[FiniteDuration]("workers.upload-success.initial-delay")

  private val interval =
    configuration.get[FiniteDuration]("workers.upload-success.interval")

  private val scheduler = actorSystem.scheduler

  private val cancellable =
    scheduler.scheduleAtFixedRate(initialDelay, interval) { () =>
      logger.info("Starting to process upload-success callbacks")

      uploadSuccessService.processAllUploadSuccesses().onComplete {
        case Success(_) =>
          logger.info("Completed processing upload success work items")

        case Failure(NonFatal(e)) =>
          logger.error("Error while processing upload success work items", e)

        case Failure(e) =>
          throw e
      }
    }

  applicationLifecycle.addStopHook(() =>
    Future.successful(cancellable.cancel())
  )
}
