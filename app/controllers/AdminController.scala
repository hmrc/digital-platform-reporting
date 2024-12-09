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

package controllers

import models.sdes.CadxResultWorkItem
import play.api.libs.json.{Format, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import repository.{CadxResultWorkItemRepository, SubmissionRepository}
import uk.gov.hmrc.internalauth.client.*
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AdminController @Inject()(
                                 submissionRepository: SubmissionRepository,
                                 cadxResultWorkItemRepository: CadxResultWorkItemRepository,
                                 cc: ControllerComponents,
                                 auth: BackendAuthComponents
                               )(using ExecutionContext) extends BackendController(cc) {

  private given Format[WorkItem[CadxResultWorkItem]] = WorkItem.workItemRestFormat[CadxResultWorkItem]

  private def permission(path: String) = Predicate.Permission(
    Resource(
      ResourceType("digital-platform-reporting"),
      ResourceLocation(path.stripPrefix("/").stripSuffix("/"))
    ),
    IAAction("ADMIN")
  )

  private def authorise(path: String): AuthenticatedActionBuilder[Unit, AnyContent] = {
    auth.authorizedAction(
      predicate = permission(path),
      onUnauthorizedError = Future.successful(Unauthorized("Unauthorized")),
      onForbiddenError = Future.successful(Forbidden("Forbidden"))
    )
  }

  def getBlockedSubmissions(): Action[AnyContent] = {
    authorise(routes.AdminController.getBlockedSubmissions().path()).async {
      submissionRepository.getBlockedSubmissionIds().map(items => Ok(Json.toJson(items)))
    }
  }

  def getCadxResultWorkItems(statuses: Set[ProcessingStatus], limit: Int, offset: Int): Action[AnyContent] =
    authorise(routes.AdminController.getCadxResultWorkItems(statuses, limit, offset).path()).async {
      cadxResultWorkItemRepository.listWorkItems(statuses, limit, offset).map { result =>
        Ok(Json.obj("workItems" -> result))
      }
    }
}
