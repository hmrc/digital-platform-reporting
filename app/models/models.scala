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

package models

import play.api.libs.json.{Format, Reads, Writes}

import java.net.URL
import java.time.Year
import scala.util.Try

given urlFormat: Format[URL] = {

  val reads = Reads.of[String].flatMap { string =>
    Try(URL(string))
      .map(Reads.pure)
      .getOrElse(Reads.failed("error.expected.url"))
  }

  val writes = Writes.of[String].contramap[URL](_.toString)

  Format(reads, writes)
}

given yearFormat: Format[Year] = {

  val reads = Reads.of[Int].flatMap { number =>
    Try(Year.of(number))
      .map(Reads.pure)
      .getOrElse(Reads.failed("error.invalid"))
  }

  val writes = Writes.of[Int].contramap[Year](_.getValue)

  Format(reads, writes)
}