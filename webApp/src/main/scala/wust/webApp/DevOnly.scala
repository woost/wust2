package wust.webApp

import scala.scalajs.LinkingInfo
object DevOnly {
  @inline def apply[T](code: => T): Option[T] = {
    if (isTrue) Option(code) else None
  }

  @inline var isTrue = LinkingInfo.developmentMode
  @inline def showDebugLogs = false
}

object DevPrintln {
  @inline def apply(code: => Any): Unit = {
    DevOnly { println(code) }
  }
}
