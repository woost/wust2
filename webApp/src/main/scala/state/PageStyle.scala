package wust.webApp.state

import colorado.{Color, HCL, LAB}
import rx.Ctx
import wust.graph.Page
import wust.ids.{NodeId, View}
import wust.sdk.{BaseColors, NodeColor}

object PageStyle {

  private def create(doMix: Boolean, nodeId: Option[NodeId]) = {

    def applyPageHue(base: HCL): String = {
      val pageHueOpt = NodeColor.mixHues(nodeId).filter(_ => doMix)
      pageHueOpt.fold[Color](LAB(base.l, 0, 0))(hue => base.copy(h = hue)).toHex
    }

    new PageStyle(
      bgColor = applyPageHue(BaseColors.pageBg),
      bgLightColor = applyPageHue(BaseColors.pageBgLight),
      sidebarBgHighlightColor = applyPageHue(BaseColors.sidebarBgHighlight),
      borderColor = applyPageHue(BaseColors.pageBorder),
    )
  }

  def apply(view: View, page: Page) = create(view.isContent, page.parentId)
  def ofNode(nodeId: Option[NodeId]) = create(true, nodeId)
}

case class PageStyle(
  bgColor: String,
  bgLightColor: String,
  sidebarBgHighlightColor: String,
  borderColor: String,
)
