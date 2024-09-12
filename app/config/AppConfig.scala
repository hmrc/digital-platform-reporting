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

package config

import play.api.Configuration

import javax.inject.{Inject, Singleton}

@Singleton
class AppConfig @Inject()(configuration: Configuration) {

  val AppName: String = configuration.get[String]("appName")

  val RegisterWithIdBaseUrl: String = configuration.get[Service]("microservice.services.register-with-id").baseUrl
  val RegisterWithIdBearerToken: String = configuration.get[String]("microservice.services.register-with-id.bearer-token")

  val RegisterWithoutIdBaseUrl: String = configuration.get[Service]("microservice.services.register-without-id").baseUrl
  val RegisterWithoutIdBearerToken: String = configuration.get[String]("microservice.services.register-without-id.bearer-token")

  val SubscribeBaseUrl: String = configuration.get[Service]("microservice.services.subscribe").baseUrl
  val UserSubscriptionBearerToken: String = configuration.get[String]("microservice.services.subscribe.bearerTokens.userSubscription")
  val ReadContactsBearerToken: String = configuration.get[String]("microservice.services.subscribe.bearerTokens.readContacts")
  val UpdateContactsBearerToken: String = configuration.get[String]("microservice.services.subscribe.bearerTokens.updateContacts")
  
  val UpdatePlatformOperatorBaseUrl: String = configuration.get[Service]("microservice.services.update-platform-operator").baseUrl
  val UpdatePlatformOperatorBearerToken: String = configuration.get[String]("microservice.services.update-platform-operator.bearer-token")
  
  val ViewPlatformOperatorsBaseUrl: String = configuration.get[Service]("microservice.services.view-platform-operator").baseUrl
  val ViewPlatformOperatorBearerToken: String = configuration.get[String]("microservice.services.view-platform-operator.bearer-token")

  val SubmissionBaseUrl: String = configuration.get[Service]("microservice.services.report-submission").baseUrl
  val SubmissionBearerToken: String = configuration.get[String]("microservice.services.report-submission.bearer-token")
}
