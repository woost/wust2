package wust.sdk

import cats.data.NonEmptyList
import colorado._
import wust.graph.{Graph, Page}
import wust.ids._
import collection.breakOut

object NodeColor {
  implicit def ColorToString(c: Color) = c.toHex

  val nodeDefaultColor = RGB("#f8f8f8")

  def genericBaseHue(seed: Any): Double = {
    val rnd = new scala.util.Random(new scala.util.Random(seed.hashCode).nextLong()) // else nextDouble is too predictable
    rnd.nextDouble() * Math.PI * 2
  }
  @inline def baseHue(id: NodeId): Double = genericBaseHue(id)
  def baseColor(id: NodeId) = HCL(baseHue(id), 50, 75)
  def baseColorDark(id: NodeId) = HCL(baseHue(id), 65, 60)
  def baseColorMixedWithDefault(id: NodeId) = mixColors(HCL(baseHue(id), 50, 75), nodeDefaultColor)

  def pageHue(page: Page): Option[Double] =
    NonEmptyList
      .fromList(page.parentIds.map(baseColor)(breakOut): List[Color])
      .map(parentColors => mixColors(parentColors).hcl.h)

  def mixColors(a: Color, b: Color): LAB = {
    val aLab = a.lab
    val bLab = b.lab
    import aLab.{a => aa, b => ab, l => al}
    import bLab.{a => ba, b => bb, l => bl}
    LAB((al + bl) / 2, (aa + ba) / 2, (ab + bb) / 2)
  }

  def mixColors(colors: NonEmptyList[Color]): LAB = {
    val colorSum = colors.foldLeft(LAB(0, 0, 0))((c1, c2Color) => {
      val c2 = c2Color.lab
      LAB(c1.l + c2.l, c1.a + c2.a, c1.b + c2.b)
    })
    val colorCount = colors.size
    LAB(colorSum.l / colorCount, colorSum.a / colorCount, colorSum.b / colorCount)
  }

  def mixedDirectParentColors(graph: Graph, nodeId: NodeId): Option[Color] =
    NonEmptyList.fromList(graph.parents(nodeId).map(baseColor).toList).map(mixColors)

  def computeColor(graph: Graph, nodeId: NodeId): Color = {
    if (graph.hasChildren(nodeId)) {
      baseColor(nodeId)
    } else {
      if (graph.hasParents(nodeId)) {
        mixedDirectParentColors(graph, nodeId).fold(RGB("#FFFFFF").lab)(
          mixed => mixColors(mixed, nodeDefaultColor)
        )
      } else
        nodeDefaultColor
    }
  }

  def computeTagColor(nodeId: NodeId): Color = {
    baseColorDark(nodeId)
  }
}
