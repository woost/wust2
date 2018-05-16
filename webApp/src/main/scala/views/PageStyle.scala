package wust.webApp.views

import cats.data.NonEmptyList
import colorado.{Color, HCL, LAB, RGB}
import wust.graph.{Page, Post}
import wust.sdk.PostColor._

import scala.collection.breakOut

object PageStyle {
  object Color {
    //TODO: ensure that these are calculated at compile time
    val baseBg = RGB("#F3EFCC").hcl
    val baseBgDark = RGB("#4D394B").hcl
    val baseBgDarkHighlight = RGB("#9D929B").hcl
    val border = RGB("#95CCDF").hcl
  }

  def apply(view: View, page:Page) = {
    val pageColors = view match {
      case view if view.isContent => NonEmptyList.fromList(page.parentIds.map(baseColor)(breakOut):List[Color]).map(mixColors)
      case _ => None
    }

    val baseHue = pageColors.map(_.hcl.h)
    def withBaseHueDefaultGray(base:HCL) = baseHue.fold(LAB(base.l, 0, 0):Color)(hue => HCL(hue, base.c, base.l))

    new PageStyle(
      baseHue,
      bgColor = withBaseHueDefaultGray(Color.baseBg),
      accentLineColor = withBaseHueDefaultGray(Color.border),
      darkBgColor = withBaseHueDefaultGray(Color.baseBgDark),
      darkBgColorHighlight = withBaseHueDefaultGray(Color.baseBgDarkHighlight)
    )
  }
}

case class PageStyle(baseHue:Option[Double], bgColor:Color, accentLineColor:Color, darkBgColor:Color, darkBgColorHighlight:Color)


