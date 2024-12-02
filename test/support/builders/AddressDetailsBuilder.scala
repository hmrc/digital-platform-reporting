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

import models.operator.AddressDetails

object AddressDetailsBuilder {

  val anAddressDetails: AddressDetails = AddressDetails(
    line1 = "default-line-1",
    line2 = None,
    line3 = None,
    line4 = None,
    postCode = None,
    countryCode = None
  )
}
