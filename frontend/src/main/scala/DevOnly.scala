package frontend

import scala.scalajs.LinkingInfo
object DevOnly {
  def apply(code: => Any) {
    if (LinkingInfo.developmentMode)
      code
  }
}
