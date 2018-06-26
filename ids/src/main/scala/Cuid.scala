package wust.ids

import java.util.UUID

case class Cuid(left: Long, right: Long) {
  def toUuid: UUID = {
    val hexString = f"${left}%016x${right}%016x"
    val uuidString = uuidStringWithHyphens(hexString)
    UUID.fromString(uuidString)
  }

  def toCuidString: String = {
    val base = 36
    val leftCuid = java.lang.Long.toUnsignedString(left, base)
    val rightCuid = java.lang.Long.toUnsignedString(right, base)
    "c" + leftCuid + rightCuid
  }

  private def uuidStringWithHyphens(uuid: String) = {
    require(uuid.length == 32, "UUID string needs to have length of 32")

    val sb = new StringBuilder
    sb.append(uuid.substring(0, 8))
    sb.append("-")
    sb.append(uuid.substring(8, 12))
    sb.append("-")
    sb.append(uuid.substring(12, 16))
    sb.append("-")
    sb.append(uuid.substring(16, 20))
    sb.append("-")
    sb.append(uuid.substring(20, 32))
    sb.toString
  }

}
object Cuid {
  def fromUuid(uuid: UUID): Cuid = {
    val uuidString = uuid.toString.filterNot(_ == '-')
    require(uuidString.length == 32, "UUID string needs to have length of 32")

    val base = 16
    val leftUuid = uuidString.substring(0, 16)
    val rightUuid = uuidString.substring(16, 32)
    println("LEFT " + leftUuid)
    println("RIGHT " + rightUuid)
    val leftLong = java.lang.Long.parseUnsignedLong(leftUuid, base)
    val rightLong = java.lang.Long.parseUnsignedLong(rightUuid, base)
    Cuid(leftLong, rightLong)
  }

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
