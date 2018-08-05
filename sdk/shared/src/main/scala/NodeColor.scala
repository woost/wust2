package wust.sdk

import cats.data.NonEmptyList
import colorado._
import wust.graph.{Graph, Page}
import wust.ids._
import collection.breakOut

object BaseColors {
  //TODO: ensure that these are calculated at compile time
  val sidebarBg = RGB("#4D394B").hcl
  val sidebarBgHighlight = RGB("#9D929B").hcl

  val pageBg = RGB("#F3EFCC").hcl
  val pageBgLight = RGB("#e2f8f2").hcl
  val pageBorder = RGB("#95CCDF").hcl

  val tag = RGB("#dfa597").hcl
  val eulerBg = RGB("#89B8FF").hcl
  val kanbanColumnBg = RGB("#dfa597").hcl
}

object NodeColor {
  def genericHue(seed: Any): Double = {
    val rnd = new scala.util.Random(new scala.util.Random(seed.hashCode).nextLong()) // else nextDouble is too predictable
    rnd.nextDouble() * Math.PI * 2
  }
  @inline def hue(id: NodeId): Double = genericHue(id)
  def eulerBgColor(id: NodeId): HCL = BaseColors.eulerBg.copy(h = hue(id))
  def tagColor(nodeId: NodeId): HCL =  BaseColors.tag.copy(h = hue(nodeId))

  def pageHue(page: Page): Option[Double] =
    NonEmptyList
      .fromList(page.parentIds.map(id => BaseColors.pageBg.copy(h = hue(id)))(breakOut): List[Color])
      .map(parentColors => mixColors(parentColors).hcl.h)

  def mixColors(a: Color, b: Color): LAB = {
    val aLab = a.lab
    val bLab = b.lab
    import aLab.{a => aa, b => ab, l => al}
    import bLab.{a => ba, b => bb, l => bl}
    LAB((al + bl) / 2, (aa + ba) / 2, (ab + bb) / 2)
  }

  def mixColors(colors: NonEmptyList[Color]): LAB = {
    // arithmetic mean in LAB color space
    val colorSum = colors.foldLeft(LAB(0, 0, 0))((c1, c2Color) => {
      val c2 = c2Color.lab
      LAB(c1.l + c2.l, c1.a + c2.a, c1.b + c2.b)
    })
    val colorCount = colors.size
    LAB(colorSum.l / colorCount, colorSum.a / colorCount, colorSum.b / colorCount)
  }

  def mixedDirectParentColors(graph: Graph, nodeId: NodeId): Option[Color] =
    NonEmptyList.fromList(graph.parents(nodeId).map(eulerBgColor).toList).map(mixColors)

  def nodeColorWithContext(graph: Graph, nodeId: NodeId): Color = {
    val nodeDefaultColor = RGB("#f8f8f8")

    if (graph.hasChildren(nodeId)) {
      eulerBgColor(nodeId)
    } else {
      if (graph.hasParents(nodeId)) {
        mixedDirectParentColors(graph, nodeId).fold(RGB("#FFFFFF").lab)(
          mixed => mixColors(mixed, nodeDefaultColor)
        )
      } else
        nodeDefaultColor
    }
  }

}
