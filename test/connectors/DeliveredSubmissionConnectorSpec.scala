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

import connectors.DeliveredSubmissionConnector.GetDeliveredSubmissionsFailure
import support.SpecBase

class DeliveredSubmissionConnectorSpec extends SpecBase {

  private val correlationId = "any-correlation-id"
  private val status = 500

  "GetDeliveredSubmissionsFailure" - {
    "must contain correct message" in {
      val underTest = GetDeliveredSubmissionsFailure(correlationId, status)
      underTest.getMessage mustBe s"Get delivered submissions failed for correlation ID: $correlationId, got status: $status"
    }
  }
}
