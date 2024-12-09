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

import org.scalatest.{EitherValues, OptionValues}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.mvc.QueryStringBindable
import uk.gov.hmrc.mongo.workitem.ProcessingStatus

class ProcessingStatusQueryStringBindableSpec extends AnyFreeSpec with Matchers with OptionValues with EitherValues {

  private val bindable = implicitly[QueryStringBindable[ProcessingStatus]]

  "must parse" - {

    "when the value is ToDo" in {
      val result = bindable.bind("status", Map("status" -> Seq("todo"))).value.value
      result mustBe ProcessingStatus.ToDo
    }

    "when the value is InProgress" in {
      val result = bindable.bind("status", Map("status" -> Seq("in-progress"))).value.value
      result mustBe ProcessingStatus.InProgress
    }

    "when the value is Succeeded" in {
      val result = bindable.bind("status", Map("status" -> Seq("succeeded"))).value.value
      result mustBe ProcessingStatus.Succeeded
    }

    "when the value is Deferred" in {
      val result = bindable.bind("status", Map("status" -> Seq("deferred"))).value.value
      result mustBe ProcessingStatus.Deferred
    }

    "when the value is Failed" in {
      val result = bindable.bind("status", Map("status" -> Seq("failed"))).value.value
      result mustBe ProcessingStatus.Failed
    }

    "when the value is PermanentlyFailed" in {
      val result = bindable.bind("status", Map("status" -> Seq("permanently-failed"))).value.value
      result mustBe ProcessingStatus.PermanentlyFailed
    }

    "when the value is Ignored" in {
      val result = bindable.bind("status", Map("status" -> Seq("ignored"))).value.value
      result mustBe ProcessingStatus.Ignored
    }

    "when the value is Duplicate" in {
      val result = bindable.bind("status", Map("status" -> Seq("duplicate"))).value.value
      result mustBe ProcessingStatus.Duplicate
    }

    "when the value is Cancelled" in {
      val result = bindable.bind("status", Map("status" -> Seq("cancelled"))).value.value
      result mustBe ProcessingStatus.Cancelled
    }
  }

  "must write" - {

    "when the value is ToDo" in {
      val result = bindable.unbind("status", ProcessingStatus.ToDo)
      result mustEqual "status=todo"
    }

    "when the value is InProgress" in {
      val result = bindable.unbind("status", ProcessingStatus.InProgress)
      result mustEqual "status=in-progress"
    }

    "when the value is Succeeded" in {
      val result = bindable.unbind("status", ProcessingStatus.Succeeded)
      result mustEqual "status=succeeded"
    }

    "when the value is Deferred" in {
      val result = bindable.unbind("status", ProcessingStatus.Deferred)
      result mustEqual "status=deferred"
    }

    "when the value is Failed" in {
      val result = bindable.unbind("status", ProcessingStatus.Failed)
      result mustEqual "status=failed"
    }

    "when the value is PermanentlyFailed" in {
      val result = bindable.unbind("status", ProcessingStatus.PermanentlyFailed)
      result mustEqual "status=permanently-failed"
    }

    "when the value is Ignored" in {
      val result = bindable.unbind("status", ProcessingStatus.Ignored)
      result mustEqual "status=ignored"
    }

    "when the value is Duplicate" in {
      val result = bindable.unbind("status", ProcessingStatus.Duplicate)
      result mustEqual "status=duplicate"
    }

    "when the value is Cancelled" in {
      val result = bindable.unbind("status", ProcessingStatus.Cancelled)
      result mustEqual "status=cancelled"
    }
  }
}