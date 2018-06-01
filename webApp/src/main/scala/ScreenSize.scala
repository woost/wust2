package wust.webApp

import org.scalajs.dom

sealed trait ScreenSize
object ScreenSize {
  case object Desktop extends ScreenSize
  case object Mobile extends ScreenSize

  def calculate(): ScreenSize =
    if (dom.window.matchMedia("only screen and (min-width : 992px)").matches)
      ScreenSize.Desktop
    else
      ScreenSize.Mobile
}
