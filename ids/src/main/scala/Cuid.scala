package wust.ids

import java.util.UUID
import java.nio.ByteBuffer

import wust.util.collection._
import org.sazabi.base58.Base58

@inline final case class Cuid(left: Long, right: Long) {
  import wust.ids.Cuid._
  // the maximum number of each long for being convertable to a cuid (base 36 with 12 digits): java.lang.Long.parseLong("z" * 12, 36)
  assert(
    left >= 0 && left <= maxLong,
    s"left part of Cuid needs to be positive and less or equal than $maxLong, value is: $left."
  )
  assert(
    right >= 0 && right <= maxLong,
    s"right part of Cuid needs to be positive and less or equal than $maxLong, value is: $right."
  )

  @inline def toUuid: UUID = new UUID(left, right)

  @inline override def hashCode: Int = (left ^ (left >> 32)).toInt ^ (right ^ (right >> 32)).toInt
  @inline override def equals(that: Any): Boolean = that match {
    case that: Cuid => isEqual(that)
    case _              => false
  }

  @inline def toCuidString: String = {
    val base = 36
    val leftCuid: String = java.lang.Long.toString(left, base).leftPadTo(12, '0')
    val rightCuid: String = java.lang.Long.toString(right, base).leftPadTo(12, '0')
    "c" + leftCuid + rightCuid
  }

  def toByteArray: Array[Byte] = {
    val bb = java.nio.ByteBuffer.allocate(16)
    bb.putLong(left)
    bb.putLong(right)
    bb.array
  }

  // provde urlsafe method
  @inline def toBase58: String = Base58(toByteArray).str

  def shortHumanReadable: String = toBase58.takeRight(3)


  @inline def hi(x:Long):Int = (x >> 32).toInt
  @inline def lo(x:Long):Int = (x & 0x7FFFFFFF).toInt
  @inline def lefthi: Int = hi(left)
  @inline def leftlo: Int = lo(left)
  @inline def righthi: Int = hi(right)
  @inline def rightlo: Int = lo(right)

  @inline def toHex:String = f"$left%016x$right%016x"

  @inline def toStringFast:String = {
    // the fastest tostring found so far (by benchmarks)
    // faster than:
    //    s"${left.toHexString}|${right.toHexString}"
    //    s"${left.toString}|${right.toString}"
    //    s"$lefthi$leftlo$righthi$rightlo"

    //  Cuid_serialization            10000
    //  toCuidString               13550108
    //  toUuid                     15924428
    //  toBase58                  113950249
    //  toHex                      33991233
    //  toStringFast                4959399
    // (times in us)

    import perfolation._
    p"$lefthi$leftlo$righthi$rightlo"
  }

  @inline def isEqual(that: Cuid): Boolean = left == that.left && right == that.right
  @inline def <(that: Cuid): Boolean = left < that.left || (left == that.left && right < that.right)
  def compare(that: Cuid): Int = {
    if (this < that) -1
    else if (this isEqual that) 0
    else 1
  }

  @inline override def toString: String = toStringFast // needs to be as fast as possible, so it can be used as snabbdom key
}
object Cuid {
  def fromUuid(uuid: UUID): Cuid =
    Cuid(uuid.getMostSignificantBits, uuid.getLeastSignificantBits)

  // TODO: these factories should return an option, or either. they can fail!
  // you can still do an explicit `.get` on the client side if you are so sure.
  // note to self: never be sure.

  def fromCuidString(cuid: String): Cuid = {
    require(cuid.startsWith("c"), "Cuid string needs to start with letter c.")
    require(cuid.length == 25, "Cuid string needs to have length of 25.")

    val base = 36
    val leftCuid = cuid.substring(1, 13)
    val rightCuid = cuid.substring(13, 25)
    val leftLong = java.lang.Long.parseLong(leftCuid, base)
    val rightLong = java.lang.Long.parseLong(rightCuid, base)
    Cuid(leftLong, rightLong)
  }

  def fromByteArray(arr: Array[Byte]): Cuid = {
    require(arr.length <= 16, "Array[Byte] must have maximum 16 elements.")
    val bb = java.nio.ByteBuffer.allocate(16)
    bb.position(16-arr.length)
    bb.put(arr)
    bb.flip()
    Cuid(bb.getLong, bb.getLong)
  }

  def fromBase58(str: String): Cuid = {
    require(str.length <= 22, "Base58 Cuid must be max 22 characters long.")
    fromByteArray(Base58.toByteArray(Base58.fromString(str).get).get)
  }

  @inline private def maxLong = 4738381338321616895L

  @inline implicit def ord:Ordering[Cuid] = Ordering.by(cuid => (cuid.left, cuid.right))
}
