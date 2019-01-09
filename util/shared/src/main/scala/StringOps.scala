package wust.util

object StringOps {

  def trimToMaxLength(str: String, maxLength: Int): String = {
    val rawString = str.trim
    if(rawString.length > maxLength)
      rawString.take(maxLength - 3) + "..."
    else rawString
  }
  def trimToMaxLength(str: String, maxLength: Option[Int]): String = {
    maxLength.fold(str)(trimToMaxLength(str, _))
  }
  @inline def safeToInt(intStr: String): Option[Int] = {
    try { Some(intStr.toInt) } catch { case _: Throwable => None }
  }
  @inline def safeToDouble(doubleStr: String): Option[Int] = {
    try { Some(doubleStr.toInt) } catch { case _: Throwable => None }
  }
}
