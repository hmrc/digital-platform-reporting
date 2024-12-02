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

package support.builders

import models.operator.responses.PlatformOperator
import support.builders.AddressDetailsBuilder.anAddressDetails
import support.builders.NotificationDetailsBuilder.aNotificationDetails
import support.builders.TinDetailsBuilder.aTinDetails
import support.builders.operator.ContactDetailsBuilder.aContactDetails

object PlatformOperatorBuilder {

  val aPlatformOperator: PlatformOperator = PlatformOperator(
    operatorId = "default-operator-id",
    operatorName = "default-operator-name",
    tinDetails = Seq(aTinDetails),
    businessName = None,
    tradingName = None,
    primaryContactDetails = aContactDetails,
    secondaryContactDetails = None,
    addressDetails = anAddressDetails,
    notifications = Seq(aNotificationDetails)
  )
}
