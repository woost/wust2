package wust.util

import scala.util.control.NonFatal

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
  def safeToInt(intStr: String): Option[Int] = {
    try { Some(intStr.toInt) } catch { case NonFatal(_) => None }
  }
  def safeToDouble(doubleStr: String): Option[Double] = {
    try { Some(doubleStr.toDouble) } catch { case NonFatal(_) => None }
  }
}
