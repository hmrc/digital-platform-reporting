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

package connectors

import connectors.SdesConnector.UnexpectedResponseException
import support.SpecBase

class SdesConnectorSpec extends SpecBase {

  private val status = 500
  private val body = "any-body-content"

  "UnexpectedResponseException" - {
    "must contain correct message" in {
      val underTest = UnexpectedResponseException(status, body)
      underTest.getMessage mustBe s"Unexpected response from SDES, status: $status, body: $body"
    }
  }
}
