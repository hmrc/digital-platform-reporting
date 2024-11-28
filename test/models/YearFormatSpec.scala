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
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.libs.json.{JsError, JsNumber, JsString}

import java.time.Year
import scala.util.Try

class YearFormatSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks {

  "reads" - {

    "must read from a jsString containing a valid year" in {

      forAll(Gen.choose(2000, 3000)) { year =>
        JsString(year.toString).as[Year] mustEqual Year.of(year)
      }
    }

    "must read from a JsNumber containing a valid year" in {

      forAll(Gen.choose(2000, 3000)) { year =>
        JsNumber(year).as[Year] mustEqual Year.of(year)
      }
    }
    
    "must fail to read strings that do not contain a valid year" in {
      
      forAll(arbitrary[String]) { string =>
        whenever(Try(string.toInt).isFailure) {
          JsString(string).validate[Year] mustBe a[JsError]
        }
      }
    }
  }
}
