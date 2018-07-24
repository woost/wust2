package wust.webApp

import org.scalajs.dom

sealed trait ScreenSize
object ScreenSize {
  case object Large extends ScreenSize
  case object Middle extends ScreenSize
  case object Small extends ScreenSize

  def calculate(): ScreenSize =
    if (dom.window.matchMedia("only screen and (min-width : 961px)").matches)
      ScreenSize.Large
    else if (dom.window.matchMedia("only screen and (min-width : 641px)").matches)
      ScreenSize.Middle
    else
      ScreenSize.Small
}
