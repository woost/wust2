package wust.ids

import java.util.UUID
import java.nio.ByteBuffer

import org.sazabi.base58.Base58

case class Cuid(left: Long, right: Long) {
  import wust.ids.Cuid._
  // the maximum number of each long for being convertable to a cuid (base 36 with 12 digits): java.lang.Long.parseLong("z" * 12, 36)
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
    val leftCuid =
      java.lang.Long.toString(left, base).reverse.padTo(12, '0').reverse
    val rightCuid =
      java.lang.Long.toString(right, base).reverse.padTo(12, '0').reverse
    "c" + leftCuid + rightCuid
  }

  def toByteArray: Array[Byte] = {
    val bb = java.nio.ByteBuffer.allocate(16)
    bb.putLong(left)
    bb.putLong(right)
    bb.array
  }

  def toBase58: String = {
    Base58(toByteArray).str
  }

  override def toString = toCuidString
}
object Cuid {
  def fromUuid(uuid: UUID): Cuid =
    Cuid(uuid.getMostSignificantBits, uuid.getLeastSignificantBits)

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

  def fromByteArray(arr: Array[Byte]): Cuid = {
    require(arr.length == 16, "Array[Byte] must have exactly 16 elements")
    val bb = java.nio.ByteBuffer.allocate(16)
    bb.put(arr)
    bb.flip()
    Cuid(bb.getLong, bb.getLong)
  }

  def fromBase58(str: String): Cuid = {
    fromByteArray(Base58.toByteArray(Base58.fromString(str).get).get)
  }

  private def maxLong = 4738381338321616895L
}
