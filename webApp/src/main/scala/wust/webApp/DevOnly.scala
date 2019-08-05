package wust.webApp

import scala.scalajs.LinkingInfo

object DebugOnly {
  @inline def apply[T](code: => T): Option[T] = {
    if (isTrue) Option(code) else None
  }
  @inline def isTrue = scribe.Logger.root.includes(scribe.Level.Debug)
}

object DevOnly {
  @inline def apply[T](code: => T): Option[T] = {
    if (isTrue) Option(code) else None
  }

  @inline var isTrue = LinkingInfo.developmentMode
  @inline def showDebugLogs = false
}

object DeployedOnly {
  @inline def apply[T](code: => T): Option[T] = {
    if (isTrue) Option(code) else None
  }
  @inline var isTrue = !LinkingInfo.developmentMode
}


object StagingOnly {
  @inline def apply[T](code: => T): Option[T] = {
    if (isTrue) Option(code) else None
  }
  @inline var isTrue = WoostConfig.audience == WoostAudience.Staging || WoostConfig.audience == WoostAudience.Dev
}

object DevPrintln {
  @inline def apply(code: => Any): Unit = {
    DevOnly { println(code) }
  }
}
