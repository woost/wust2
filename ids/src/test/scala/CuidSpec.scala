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
    assertThrows[AssertionError] { Cuid.fromUuid(original) }
  }

  "small uuid" in {
    val original = new UUID(-1, -1)
    assertThrows[AssertionError] { Cuid.fromUuid(original) }
  }

  "cuid" in {
    val original = cuid.Cuid()
    val bag = Cuid.fromCuidString(original)
    val converted = bag.toCuidString

    converted mustEqual original
  }

  "cuid byte array" in {
    val original = Cuid.fromCuidString(cuid.Cuid())
    val converted = Cuid.fromByteArray(original.toByteArray)

    converted mustEqual original
  }

  "cuid base58" in {
    val original = Cuid.fromCuidString(cuid.Cuid())
    val converted = Cuid.fromBase58(original.toBase58)

    converted mustEqual original
  }
}
