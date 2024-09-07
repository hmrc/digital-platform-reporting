package controllers

import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}

@Singleton
class SubmissionController @Inject() (
                                       cc: ControllerComponents,
                                     ) extends BackendController(cc) {

  def start(dprsId: String): Action[AnyContent] = ???

  def get(dprsId: String, id: String): Action[AnyContent] = ???

  def uploadSuccess(dprsId: String, id: String): Action[AnyContent] = ???

  def uploadFailed(dprsId: String, id: String): Action[AnyContent] = ???

  def submit(dprsId: String, id: String): Action[AnyContent] = ???
}
