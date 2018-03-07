package wust.utilWeb
import colorado._
import wust.graph.Graph
import wust.ids._

object Color {
  val postDefaultColor = RGB("#f8f8f8")

  def baseHue(id: PostId):Int = (id.hashCode * 137) % 360
  def baseColor(id: PostId) = HCL(baseHue(id), 50, 75)
  def baseColorDark(id: PostId) = HCL(baseHue(id), 65, 60)
  def baseColorMixedWithDefault(id: PostId) = mixColors(HCL(baseHue(id), 50, 75), postDefaultColor)

  def mixColors(a: Color, b: Color): Color = {
    val aLab = a.lab
    val bLab = b.lab
    import aLab.{a => aa, b => ab, l => al}
    import bLab.{a => ba, b => bb, l => bl}
    LAB((al + bl) / 2, (aa + ba) / 2, (ab + bb) / 2)
  }
  def mixColors(colors: Iterable[Color]): Color = {
    if (colors.isEmpty) return RGB("#FFFFFF")
    val colorSum = colors.map(_.lab).reduce((c1, c2) => LAB(c1.l + c2.l, c1.a + c2.a, c1.b + c2.b))
    val colorCount = colors.size
    LAB(colorSum.l / colorCount, colorSum.a / colorCount, colorSum.b / colorCount)
  }

  implicit def ColorToString(c:Color) = c.toHex
}

object ColorPost {
  import wust.utilWeb.Color._

  def mixedDirectParentColors(graph: Graph, postId: PostId) = mixColors(graph.parents(postId).map(baseColor))

  def computeColor(graph: Graph, postId: PostId): Color = {
    val calculatedBorderColor = if (graph.hasChildren(postId)) {
        baseColor(postId)
    } else {
      if (graph.hasParents(postId))
        mixColors(mixedDirectParentColors(graph, postId), postDefaultColor)
      else
        postDefaultColor
    }
    calculatedBorderColor
  }

  def computeTagColor(graph: Graph, postId: PostId): Color = {
    baseColorDark(postId)
  }
}
