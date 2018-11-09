package wust.util

object StringOps {
  def trimToMaxLength(str: String, maxLength: Option[Int]): String = {
    maxLength.fold(str) { length =>
      val rawString = str.trim
      if(rawString.length > length)
        rawString.take(length - 3) + "..."
      else rawString
    }
  }
}
