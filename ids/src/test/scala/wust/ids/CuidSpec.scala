package wust.ids

import java.util.UUID

import org.scalatest._
import org.scalatest.matchers._
import org.scalatest.freespec.AnyFreeSpec

class CuidSpec extends AnyFreeSpec with must.Matchers {
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
    val bag = Cuid.fromCuidString(original).right.get
    val converted = bag.toCuidString

    converted mustEqual original
  }

  "cuid byte array" in {
    val original = Cuid.fromCuidString(cuid.Cuid()).right.get
    val converted = Cuid.fromByteArray(original.toByteArray)

    converted mustEqual Right(original)
  }

  "cuid base58" in {
    val original = Cuid.fromCuidString(cuid.Cuid()).right.get
    val converted = Cuid.fromBase58String(original.toBase58)

    converted mustEqual Right(original)
  }
}
