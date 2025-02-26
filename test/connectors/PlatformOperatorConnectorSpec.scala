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

import connectors.PlatformOperatorConnector.{CreatePlatformOperatorFailure, DeletePlatformOperatorFailure, UpdatePlatformOperatorFailure, ViewPlatformOperatorsFailure}
import support.SpecBase

class PlatformOperatorConnectorSpec extends SpecBase {

  private val correlationId = "any-correlation-id"
  private val status = 500

  "CreatePlatformOperatorFailure" - {
    "must contain correct message" in {
      val underTest = CreatePlatformOperatorFailure(correlationId, status)
      underTest.getMessage mustBe s"Create platform operator failed for correlation ID: $correlationId, got status: $status"
    }
  }

  "UpdatePlatformOperatorFailure" - {
    "must contain correct message" in {
      val underTest = UpdatePlatformOperatorFailure(correlationId, status)
      underTest.getMessage mustBe s"Update platform operator failed for correlation ID: $correlationId, got status: $status"
    }
  }

  "DeletePlatformOperatorFailure" - {
    "must contain correct message" in {
      val underTest = DeletePlatformOperatorFailure(correlationId, status)
      underTest.getMessage mustBe s"Delete platform operator failed for correlation ID: $correlationId, got status: $status"
    }
  }

  "ViewPlatformOperatorsFailure" - {
    "must contain correct message" in {
      val underTest = ViewPlatformOperatorsFailure(correlationId, status)
      underTest.getMessage mustBe s"View platform operator failed for correlation ID: $correlationId, got status: $status"
    }
  }
}
