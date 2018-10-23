package wust.webApp.state

import org.scalajs.dom
import scala.scalajs.js

sealed trait ScreenSize {
  def minWidth:Int
}
object ScreenSize {
  case object Large extends ScreenSize {def minWidth = 641 /* original 961 */}
//  case object Middle extends ScreenSize {def minWidth = 641}
  case object Small extends ScreenSize {def minWidth = 0}

  def fromPixelSize(width: Int): ScreenSize = width match {
    case _ if width >= Large.minWidth => Large
//    case _ if width >= Middle.minWidth => Middle
    case _ => Small
  }

  // In tests matchMedia function is undefined, we need to catch this here.
  private val matchMediaIsUndefined =
    dom.window.asInstanceOf[js.Dynamic].matchMedia.asInstanceOf[js.UndefOr[js.Dynamic]] == js.undefined

  def calculate(): ScreenSize =
    if (matchMediaIsUndefined)
      ScreenSize.Large
    else if (dom.window.matchMedia(s"only screen and (min-width : ${Large.minWidth}px)").matches)
      ScreenSize.Large
//    else if (dom.window.matchMedia(s"only screen and (min-width : ${Middle.minWidth}px)").matches)
//      ScreenSize.Middle
    else
      ScreenSize.Small
}
