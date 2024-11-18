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

package utils

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import utils.ErrorCodeMapper.{getErrorDescription, loadErrorDescriptions}

class ErrorCodeMapperSpec extends AnyFreeSpec with Matchers {

  "getErrorDescription" - {

    "return correct error description for valid error code" in {
      val result = getErrorDescription("002", Some("File contains multiple records (more than one DPIBody)"))
      result mustEqual "Only one record (DPIBody) is allowed per XML submission."
    }

    "return original detail if error code not found" in {
      val result = getErrorDescription("999", Some("File contains multiple records (more than one DPIBody)"))
      result mustEqual "File contains multiple records (more than one DPIBody)"
    }

    "return 'Unknown error' if error code not found and details is None" in {
      val result = getErrorDescription("999", None)
      result mustEqual "Unknown error"
    }

  }

  "loadErrorDescriptions" - {

    "handle empty JSON gracefully" in {
      val errorDescription = loadErrorDescriptions("test/resources/error_codes_empty.json")
      errorDescription.isEmpty mustEqual true
    }

    "handle malformed JSON gracefully" in {
      val errorDescription = loadErrorDescriptions("test/resources/error_codes_malformed.json")
      errorDescription.isEmpty mustEqual true
    }

  }



}
