package wust.util

import java.text.DateFormat
import java.util.Date

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
  @inline def safeToInt(intStr: String): Option[Int] = {
    try { Some(intStr.toInt) } catch { case NonFatal(_) => None }
  }
  @inline def safeToDouble(doubleStr: String): Option[Double] = {
    try { Some(doubleStr.toDouble) } catch { case NonFatal(_) => None }
  }
  @inline def safeToDate(epochString: String): Option[Date] = {
    // alternative DateFormat#parse is not available in js
    try { Some(new Date(epochString)) } catch { case NonFatal(_) => None }
  }
}
