package wust.ids

import org.scalatest._

import java.util.UUID

class CuidSpec extends FreeSpec with MustMatchers {
  "uuid" in {
    val original = new UUID(12, 25)
    val bag = Cuid.fromUuid(original)
    val converted = bag.toUuid

    converted mustEqual original
  }

  "big uuid" in {
    val original = new UUID(Long.MaxValue, Long.MaxValue)
    assertThrows[IllegalArgumentException] { Cuid.fromUuid(original) }
  }

  "small uuid" in {
    val original = new UUID(-1, -1)
    assertThrows[IllegalArgumentException] { Cuid.fromUuid(original) }
  }

  "cuid" in {
    val original = cuid.Cuid()
    val bag = Cuid.fromCuidString(original)
    val converted = bag.toCuidString

    converted mustEqual original
  }
}
