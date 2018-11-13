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
}
