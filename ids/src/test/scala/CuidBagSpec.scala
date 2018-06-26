package wust.ids

import org.scalatest._

import java.util.UUID

class CuidBagSpec extends FreeSpec with MustMatchers {
  "uuid" in {
    val original = UUID.randomUUID
    val bag = CuidBag.fromUuid(original)
    val converted = bag.toUuid

    converted mustEqual original
  }

  "cuid" in {
    val original = cuid.Cuid()
    val bag = CuidBag.fromCuid(original)
    val converted = bag.toCuid

    converted mustEqual original
  }
}
