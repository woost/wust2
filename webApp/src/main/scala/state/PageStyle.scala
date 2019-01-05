package wust.webApp.state

import colorado.{Color, HCL, LAB}
import rx.Ctx
import wust.graph.Page
import wust.sdk.{BaseColors, NodeColor}

object PageStyle {

  def apply(view: View, page: Page)(implicit ctx: Ctx.Owner) = {

    def applyPageHue(base: HCL): String = {
      val pageHueOpt = NodeColor.mixHues(page.parentId).filter(_ => view.isContent)
      pageHueOpt.fold[Color](LAB(base.l, 0, 0))(hue => base.copy(h = hue)).toHex
    }

    new PageStyle(
      bgColor = applyPageHue(BaseColors.pageBg),
      bgLightColor = applyPageHue(BaseColors.pageBgLight),
      sidebarBgHighlightColor = applyPageHue(BaseColors.sidebarBgHighlight),
      borderColor = applyPageHue(BaseColors.pageBorder),
    )
  }
}

case class PageStyle(
  bgColor: String,
  bgLightColor: String,
  sidebarBgHighlightColor: String,
  borderColor: String,
)
