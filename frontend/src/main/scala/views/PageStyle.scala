package wust.frontend.views

import org.scalajs.d3v4.{Color, d3}
import wust.frontend.Color._
import wust.graph.{Graph, Page}

object PageStyle {
  def apply(page:Page, rawGraph:Graph) = {

    val focusedParentIds = page.parentIds

    val title = {
      val parents = focusedParentIds.map(rawGraph.postsById)
      val parentTitles = parents.map(_.title).mkString(", ")
      parentTitles
    }

    val mixedDirectParentColors = mixColors(focusedParentIds.map(baseColor))
    val baseHue = d3.hcl(mixedDirectParentColors).h

    val accentLineColor = d3.hcl(baseHue, 50, 50)

    val bgColor = mixColors(List(mixedDirectParentColors, d3.lab("#FFFFFF"), d3.lab("#FFFFFF")))
    new PageStyle(title, baseHue, bgColor, accentLineColor)
  }
}

case class PageStyle(title:String, baseHue:Double, bgColor:Color, accentLineColor:Color)


