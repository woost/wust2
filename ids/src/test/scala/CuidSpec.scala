package wust.ids

import org.scalatest._

import java.util.UUID

class CuidSpec extends FreeSpec with MustMatchers {
  "uuid" in {
    val original = UUID.randomUUID
    val bag = Cuid.fromUuid(original)
    val converted = bag.toUuid

    converted mustEqual original
  }

  "cuid" in {
    val original = cuid.Cuid()
    val bag = Cuid.fromCuidString(original)
    val converted = bag.toCuidString

    converted mustEqual original
  }
}
