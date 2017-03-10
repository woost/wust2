package frontend

import scala.scalajs.LinkingInfo
object DevOnly {
  def apply[T](code: => T): Option[T] = {
    if (LinkingInfo.developmentMode)
      Some(code)
    else
      None
  }
}
