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

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.EitherValues
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.mvc.PathBindable

import java.time.Year

class YearPathBindableSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks with EitherValues {

  private val pathBindable = implicitly[PathBindable[Year]]

  "pathBindable" - {

    "must bind valid years" in {

      forAll(Gen.choose(2000, 3000)) { year =>
        pathBindable.bind("key", year.toString).value mustEqual Year.of(year)
      }
    }

    "must not bind strings that are not a valid year" in {

      forAll(arbitrary[String]) { string =>
        whenever(!string.forall(_.isDigit)) {
          pathBindable.bind("key", string) mustEqual Left(s"Could not bind $string as a Year")
        }
      }
    }
    
    "must unbind" in {
      forAll(Gen.choose(2000, 3000)) { year =>
        pathBindable.unbind("key", Year.of(year)) mustEqual year.toString
      }
    }
  }
}
