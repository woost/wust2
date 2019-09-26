package wust.webApp

import scala.scalajs.LinkingInfo

object DebugOnly {
  @inline def apply[T](code: => T): Option[T] = {
    if (isTrue) Option(code) else None
  }
  var isTrueSetting = false
  @inline def isTrue = isTrueSetting
}

object DevOnly {
  @inline def apply[T](code: => T): Option[T] = {
    if (isTrue) Option(code) else None
  }

  @inline def isTrue = LinkingInfo.developmentMode || DebugOnly.isTrue // show DevOnly stuff also in DebugOnly
}

object DeployedOnly {
  @inline def apply[T](code: => T): Option[T] = {
    if (isTrue) Option(code) else None
  }
  @inline def isTrue = LinkingInfo.productionMode
}


object StagingOnly {
  @inline def apply[T](code: => T): Option[T] = {
    if (isTrue) Option(code) else None
  }
  @inline def isTrue = WoostConfig.audience == WoostAudience.Staging || DevOnly.isTrue // show StagingOnly stuff also in DevOnly
}

object DevPrintln {
  @inline def apply(code: => Any): Unit = {
    DevOnly { println(code) }
  }
}
