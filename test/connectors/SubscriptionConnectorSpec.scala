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

import connectors.SubscriptionConnector.{CreateSubscriptionFailure, CreateSubscriptionUnprocessableFailure, UpdateSubscriptionFailure}
import support.SpecBase

class SubscriptionConnectorSpec extends SpecBase {

  private val correlationId = "any-correlation-id"
  private val status = 500

  "CreateSubscriptionFailure" - {
    "must contain correct message" in {
      val underTest = CreateSubscriptionFailure(correlationId, status)
      underTest.getMessage mustBe s"Create subscription failed for correlation ID: $correlationId, got status: $status"
    }
  }

  "CreateSubscriptionUnprocessableFailure" - {
    "must contain correct message" in {
      val underTest = CreateSubscriptionUnprocessableFailure(correlationId, status.toString)
      underTest.getMessage mustBe s"Create subscription received UNPROCESSABLE_ENTITY for correlation ID: $correlationId, got error code: $status"
    }
  }

  "UpdateSubscriptionFailure" - {
    "must contain correct message" in {
      val underTest = UpdateSubscriptionFailure(correlationId, status)
      underTest.getMessage mustBe s"Update subscription failed for correlation ID: $correlationId, got status: $status"
    }
  }
}
