package wust.webApp.state

import colorado.{Color, HCL, LAB}
import wust.graph.Page
import wust.ids.{NodeId, View}
import wust.sdk.{BaseColors, NodeColor}

object PageStyle {

  private def create(doMix: Boolean, nodeId: Option[NodeId]) = {

    def applyPageHue(base: HCL): String = {
      val pageHueOpt = NodeColor.hue(nodeId).filter(_ => doMix)
      pageHueOpt.fold[Color](LAB(base.l, 0, 0))(hue => base.copy(h = hue)).toHex
    }

    new PageStyle(
      bgLightColor = applyPageHue(BaseColors.pageBgLight),
      pageBgColor = applyPageHue(BaseColors.pageBg),
    )
  }

  @inline def apply(view: View, page: Page): PageStyle = create(view.isContent, page.parentId)
  @inline def ofNode(nodeId: Option[NodeId]): PageStyle = create(true, nodeId)
  @inline def ofNode(nodeId: NodeId): PageStyle = ofNode(Some(nodeId))
}

final case class PageStyle(
  bgLightColor: String,
  pageBgColor: String,
)
