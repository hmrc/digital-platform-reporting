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

package services

import support.SpecBase

import java.util.UUID

class UuidServiceSpec extends SpecBase {

  private val underTest = new UuidService()

  ".generate()" - {
    "must generate a valid UUID string" in {
      val result = underTest.generate()

      result must not be null
      result must not be empty

      noException must be thrownBy UUID.fromString(result)

      val uuidRegex = """^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"""
      result must fullyMatch regex uuidRegex
    }

    "must generate unique UUIDs on multiple calls" in {
      val uuid1 = underTest.generate()
      val uuid2 = underTest.generate()

      uuid1 must not equal uuid2
    }
  }
}
