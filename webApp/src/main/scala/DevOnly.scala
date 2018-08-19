package wust.webApp

import scala.scalajs.LinkingInfo
object DevOnly {
  var enabled = true
  def apply[T](code: => T): Option[T] = {
    if (isTrue) Option(code) else None
  }

  def isTrue = LinkingInfo.developmentMode && enabled
}

object DevPrintln {
  def apply(code: => Any): Unit = {
    DevOnly { println(code) }
  }
}
