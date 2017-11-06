package wust.frontend.views

import org.scalajs.d3v4.d3
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

    val bgColor = {
      val mixedDirectParentColors = mixColors(focusedParentIds.map(baseColor))
      mixColors(List(mixedDirectParentColors, d3.lab("#FFFFFF"), d3.lab("#FFFFFF"))).toString
    }
    new PageStyle(title, bgColor)
  }
}

case class PageStyle(title:String, bgColor:String)


