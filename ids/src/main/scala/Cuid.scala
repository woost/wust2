package wust.ids

import java.util.UUID

case class Cuid(left: Long, right: Long) {
  // the maximum number of each long for being convertable to a cuid (base 36 with 12 digits): java.lang.Long.parseLong("z" * 12, 36)
  private val maxLong = 4738381338321616895L
  require(
    left >= 0 && left <= maxLong,
    s"left part of Cuid needs to be positive and less than $maxLong, value is: $left"
  )
  require(
    right >= 0 && right <= maxLong,
    s"right part of Cuid needs to be positive and less than $maxLong, value is: $right"
  )

  def toUuid: UUID = new UUID(left, right)

  def toCuidString: String = {
    val base = 36
    val leftCuid = java.lang.Long.toString(left, base).reverse.padTo(12, '0').reverse
    val rightCuid = java.lang.Long.toString(right, base).reverse.padTo(12, '0').reverse
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
    val leftLong = java.lang.Long.parseLong(leftCuid, base)
    val rightLong = java.lang.Long.parseLong(rightCuid, base)
    Cuid(leftLong, rightLong)
  }
}
