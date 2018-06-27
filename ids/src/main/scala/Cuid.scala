package wust.ids

import java.util.UUID

case class Cuid(left: Long, right: Long) {
  def toUuid: UUID = new UUID(left, right)

  def toCuidString: String = {
    val base = 36
    // if a cuid was generated from a uuid, we might have 128 bits.
    // But a cuid can only fit 125 bit. We do not do this in production, but in tests.
    // Therefore, we are trimming the resulting string to 12 characters
    val leftCuid = java.lang.Long.toUnsignedString(left, base).take(12).reverse.padTo(12, '0').reverse
    val rightCuid = java.lang.Long.toUnsignedString(right, base).take(12).reverse.padTo(12, '0').reverse
    "c" + leftCuid + rightCuid
  }

  override def toString = toCuidString
}
object Cuid {
  def fromUuid(uuid: UUID): Cuid = Cuid(uuid.getMostSignificantBits, uuid.getLeastSignificantBits)

  def fromCuidString(cuid: String): Cuid = {
    require(cuid.startsWith("c"), "Cuid string needs to start with letter c")
    require(cuid.length == 25, "Cuid string needs to have length of 25")

    val base = 36
    val leftCuid = cuid.substring(1, 13)
    val rightCuid = cuid.substring(13, 25)
    val leftLong = java.lang.Long.parseUnsignedLong(leftCuid, base)
    val rightLong = java.lang.Long.parseUnsignedLong(rightCuid, base)
    Cuid(leftLong, rightLong)
  }
}
