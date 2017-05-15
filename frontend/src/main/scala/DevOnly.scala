package wust.frontend

import scala.scalajs.LinkingInfo
object DevOnly {
  var enabled = true
  def apply[T](code: => T): Option[T] = {
    if (LinkingInfo.developmentMode && enabled)
      Option(code)
    else
      None
  }
}

object DevPrintln {
  def apply(code: => String) {
    DevOnly { println(code) }
  }
}
