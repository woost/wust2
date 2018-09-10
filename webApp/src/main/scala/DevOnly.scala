package wust.webApp

import scala.scalajs.LinkingInfo
object DevOnly {
  val enabled = false
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
