package wust.webApp.views

import cats.data.NonEmptyList
import colorado.{Color, HCL, LAB, RGB}
import rx.{Ctx, Rx}
import wust.graph.{Node, Page}
import wust.sdk.{BaseColors, NodeColor}
import wust.sdk.NodeColor._
import wust.util._

import scala.collection.breakOut

object PageStyle {

  def apply(view: View, page: Page)(implicit ctx: Ctx.Owner) = {

    def applyPageHue(base: HCL): String = {
      val pageHueOpt = NodeColor.pageHue(page).filter(_ => view.isContent)
      pageHueOpt.fold[Color](LAB(base.l, 0, 0))(hue => base.copy(h = hue)).toHex
    }

    new PageStyle(
      bgColor = applyPageHue(BaseColors.pageBg),
      bgLightColor = applyPageHue(BaseColors.pageBgLight),
      sidebarBgColor = applyPageHue(BaseColors.sidebarBg),
      sidebarBgHighlightColor = applyPageHue(BaseColors.sidebarBgHighlight),
      borderColor = applyPageHue(BaseColors.pagePorder),
    )
  }
}

case class PageStyle(
  bgColor: String,
  bgLightColor: String,
  sidebarBgColor: String,
  sidebarBgHighlightColor: String,
  borderColor: String,
)
