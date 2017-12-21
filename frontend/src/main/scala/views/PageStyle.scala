package wust.frontend.views

import org.scalajs.d3v4.{Color, d3}
import wust.frontend.Color._
import wust.graph.{Graph, Page, Post}

object PageStyle {
  def apply(page:Page, parents:Set[Post]) = {

    val title = parents.map(_.title).mkString(", ")

    val mixedDirectParentColors = mixColors(page.parentIds.map(baseColor))
    val baseHue = d3.hcl(mixedDirectParentColors).h

    val accentLineColor = d3.hcl(baseHue, 50, 50)

    val darkBgColor = d3.hcl(baseHue, 14.55109, 26.76950)

    val bgColor = mixColors(List(mixedDirectParentColors, d3.lab("#FFFFFF"), d3.lab("#FFFFFF")))
    new PageStyle(title, baseHue, bgColor, accentLineColor, darkBgColor)
  }
}

case class PageStyle(title:String, baseHue:Double, bgColor:Color, accentLineColor:Color, darkBgColor:Color)


