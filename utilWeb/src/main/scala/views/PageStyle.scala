package wust.utilWeb.views

import cats.data.NonEmptyList
import colorado.{Color, HCL, LAB, RGB}
import wust.graph.{Page, Post}
import wust.utilWeb.Color._

import scala.collection.breakOut

object PageStyle {
  object Color {
    //TODO: ensure that these are calculated at compile time
    private[PageStyle] val baseBg = RGB("#F3EFCC").hcl
    private[PageStyle] val baseBgDark = RGB("#4D394B").hcl
    private[PageStyle] val border = RGB("#95CCDF").hcl
  }


  def apply(page:Page, parents:Set[Post]) = {

    val title = parents.map(_.content).mkString(", ")

    val mixedDirectParentColors = NonEmptyList.fromList(page.parentIds.map(baseColor)(breakOut):List[Color]).map(mixColors)
    val baseHue = mixedDirectParentColors.map(_.hcl.h)

    def withBaseHueDefaultGray(base:HCL) = baseHue.fold(LAB(base.l, 0, 0):Color)(hue => HCL(hue, base.c, base.l))

    val accentLineColor = withBaseHueDefaultGray(Color.border)
    val darkBgColor = withBaseHueDefaultGray(Color.baseBgDark)
    val bgColor = withBaseHueDefaultGray(Color.baseBg)

    new PageStyle(title, baseHue, bgColor, accentLineColor, darkBgColor)
  }
}

case class PageStyle(title:String, baseHue:Option[Double], bgColor:Color, accentLineColor:Color, darkBgColor:Color)


