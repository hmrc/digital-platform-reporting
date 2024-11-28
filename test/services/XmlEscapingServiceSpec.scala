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

package services

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import scala.xml.{EntityRef, Text}

class XmlEscapingServiceSpec
  extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite {

  private val service = app.injector.instanceOf[XmlEscapingService]

  "escape" - {

    "must escape '\'' and '@'" in {

      val bar = "lorem ' ipsum @ dolor"
      val xml = <foo>' lorem ipsum ' @ dolor '<bar>{bar}</bar></foo>

      val expectedXml = <foo>{Seq(
        EntityRef("apos"),
        Text(" lorem ipsum "),
        EntityRef("apos"),
        Text(" "),
        EntityRef("commat"),
        Text(" dolor "),
        EntityRef("apos"),
        <bar>{Seq(
            Text("lorem "),
            EntityRef("apos"),
            Text(" ipsum "),
            EntityRef("commat"),
            Text(" dolor"))}</bar>
        )}</foo>

      service.escape(xml).head mustEqual expectedXml
    }
  }
}
