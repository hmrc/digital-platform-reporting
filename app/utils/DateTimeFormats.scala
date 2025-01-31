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

import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateTimeFormats {

  val RFC7231Formatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz")
    .withZone(ZoneId.of("UTC"))

  val ISO8601Formatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
    .withZone(ZoneId.of("UTC"))

  val EmailDateTimeFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("h:mma z 'on' d MMMM yyyy")
    .withZone(ZoneId.of("GMT"))

}
