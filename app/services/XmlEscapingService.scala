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

import services.XmlEscapingService.StringReplacementRewriteRule

import javax.inject.{Inject, Singleton}
import scala.annotation.tailrec
import scala.xml.{EntityRef, Node, NodeSeq, Text}
import scala.xml.transform.{RewriteRule, RuleTransformer}

@Singleton
class XmlEscapingService @Inject() {

  def escape(nodeSeq: NodeSeq): NodeSeq =
    transformer.transform(nodeSeq)

  private val apostropheRule = new StringReplacementRewriteRule("'", EntityRef("apos"))
  private val atRule = new StringReplacementRewriteRule("@", EntityRef("commat"))

  private val transformer = RuleTransformer(apostropheRule, atRule)
}

object XmlEscapingService {

  class StringReplacementRewriteRule(matcher: String, replacement: Node) extends RewriteRule {

    override def transform(n: Node): collection.Seq[Node] =
      n match {
        case Text(content) =>
          process(content, Seq.empty)
        case _ =>
          n
      }

    @tailrec
    private def process(text: String, nodes: NodeSeq): NodeSeq = {
      val index = text.indexOf(matcher)
      if (index < 0) {
        nodes :+ Text(text)
      } else {
        process(text.substring(index + matcher.length), nodes ++ Seq(Text(text.take(index)), replacement))
      }
    }
  }
}