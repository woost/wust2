package wust.webApp

import scala.scalajs.LinkingInfo
object DevOnly {
  val enabled = true
  def apply[T](code: => T): Option[T] = {
    if (LinkingInfo.developmentMode && enabled)
      Option(code)
    else
      None
  }
}

object DevPrintln {
  def apply(code: => Any):Unit = {
    DevOnly { println(code) }
  }
}
