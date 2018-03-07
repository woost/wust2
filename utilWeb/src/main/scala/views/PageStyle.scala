package wust.utilWeb.views

import colorado.{Color, HCL, RGB}
import wust.utilWeb.Color._
import wust.graph.{Graph, Page, Post}

object PageStyle {
  object Color {
    //TODO: ensure that these are calculated at compile time
    val baseBg = RGB("#F3EFCC").hcl
    val baseBgDark = RGB("#4D394B").hcl
    val border = RGB("#95CCDF").hcl
  }


  def apply(page:Page, parents:Set[Post]) = {

    val title = parents.map(_.content).mkString(", ")

    val mixedDirectParentColors = mixColors(page.parentIds.map(baseColor))
    val baseHue = mixedDirectParentColors.hcl.h

    def withBaseHue(base:HCL) = HCL(baseHue, base.c, base.l)

    val accentLineColor = withBaseHue(Color.border)
    val darkBgColor = withBaseHue(Color.baseBgDark)
    val bgColor = withBaseHue(Color.baseBg)

    new PageStyle(title, baseHue, bgColor, accentLineColor, darkBgColor)
  }
}

case class PageStyle(title:String, baseHue:Double, bgColor:Color, accentLineColor:Color, darkBgColor:Color)


