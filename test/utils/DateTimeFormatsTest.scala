package utils

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import java.time.LocalDateTime

class DateTimeFormatsTest extends AnyFreeSpec with Matchers {

  private val localDateTime = LocalDateTime.of(2024, 8, 9, 2, 23, 59)

  "RFC7231Formatter" - {
    "must format to RFC-7231 standard" in {
      localDateTime.format(DateTimeFormats.RFC7231Formatter) mustBe "Fri, 09 Aug 2024 02:23:59 UTC"
    }
  }
}
