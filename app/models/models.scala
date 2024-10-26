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
import play.api.mvc.PathBindable

import java.net.URL
import java.time.Year
import scala.language.implicitConversions
import scala.util.{Success, Try}

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

implicit def yearPathBindable(using intBinder: PathBindable[Int]): PathBindable[Year] = new PathBindable[Year] {
  
  override def bind(key: String, value: String): Either[String, Year] =
    intBinder.bind(key, value) match {
      case Right(x) =>
        Try(Year.of(x)) match {
          case Success(year) => Right(year)
          case _             => Left(s"Could not bind $x as a Year")
        }
      case _ => Left(s"Could not bind $value as a Year")
    }

  override def unbind(key: String, value: Year): String =
    value.toString
}
