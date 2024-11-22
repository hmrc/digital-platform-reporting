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

import java.time.{Instant, LocalDateTime}

class DateTimeFormatsSpec extends AnyFreeSpec with Matchers {

  private val localDateTime = LocalDateTime.of(2024, 8, 9, 2, 23, 59)

  "RFC7231Formatter" - {
    "must format to RFC-7231 standard" in {
      localDateTime.format(DateTimeFormats.RFC7231Formatter) mustBe "Fri, 09 Aug 2024 02:23:59 UTC"
    }
  }

  " ISO8601Formatter" - {
    "must format to ISO-8601 standard" in {
      val instant = Instant.ofEpochSecond(1, 223345000)

      DateTimeFormats.ISO8601Formatter.format(instant) mustBe "1970-01-01T00:00:01Z"
    }
  }

  "EmailDateTimeFormatter" - {
    "must format to RFC-7231 standard" in {
      localDateTime.format(DateTimeFormats.EmailDateTimeFormatter) mustBe "2:23am (GMT) on 9 August 2024"
    }
  }

}
