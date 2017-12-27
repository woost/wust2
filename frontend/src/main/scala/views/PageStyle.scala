package wust.frontend.views

import org.scalajs.d3v4.{Color, Hcl, d3}
import wust.frontend.Color._
import wust.graph.{Graph, Page, Post}

object PageStyle {
  object Color {
    //TODO: ensure that these are calculated at compile time
    val baseBg = d3.hcl("#F3EFCC")
    val baseBgDark = d3.hcl("#4D394B")
    val border = d3.hcl("#95CCDF")
  }


  def apply(page:Page, parents:Set[Post]) = {

    val title = parents.map(_.title).mkString(", ")

    val mixedDirectParentColors = mixColors(page.parentIds.map(baseColor))
    val baseHue = d3.hcl(mixedDirectParentColors).h

    def withBaseHue(base:Hcl) = d3.hcl(baseHue, base.c, base.l)

    val accentLineColor = withBaseHue(Color.border)
    val darkBgColor = withBaseHue(Color.baseBgDark)
    val bgColor = withBaseHue(Color.baseBg)

    new PageStyle(title, baseHue, bgColor, accentLineColor, darkBgColor)
  }
}

case class PageStyle(title:String, baseHue:Double, bgColor:Color, accentLineColor:Color, darkBgColor:Color)


