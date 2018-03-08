package wust.sdk
import cats.data.NonEmptyList
import colorado._
import wust.graph.Graph
import wust.ids._

object PostColor {
  implicit def ColorToString(c:Color) = c.toHex

  val postDefaultColor = RGB("#f8f8f8")

  @inline def baseHue(id: PostId):Int = (id.hashCode * 137) % 360
  def baseColor(id: PostId) = HCL(baseHue(id), 50, 75)
  def baseColorDark(id: PostId) = HCL(baseHue(id), 65, 60)
  def baseColorMixedWithDefault(id: PostId) = mixColors(HCL(baseHue(id), 50, 75), postDefaultColor)

  def mixColors(a: Color, b: Color): LAB = {
    val aLab = a.lab
    val bLab = b.lab
    import aLab.{a => aa, b => ab, l => al}
    import bLab.{a => ba, b => bb, l => bl}
    LAB((al + bl) / 2, (aa + ba) / 2, (ab + bb) / 2)
  }

  def mixColors(colors: NonEmptyList[Color]): LAB = {
    val colorSum = colors.foldLeft(LAB(0,0,0))((c1, c2Color) => {
      val c2 = c2Color.lab
      LAB(c1.l + c2.l, c1.a + c2.a, c1.b + c2.b)
    })
    val colorCount = colors.size
    LAB(colorSum.l / colorCount, colorSum.a / colorCount, colorSum.b / colorCount)
  }

  def mixedDirectParentColors(graph: Graph, postId: PostId):Option[Color] = NonEmptyList.fromList(graph.parents(postId).map(baseColor).toList).map(mixColors)

  def computeColor(graph: Graph, postId: PostId): Color = {
    if (graph.hasChildren(postId)) {
      baseColor(postId)
    } else {
      if (graph.hasParents(postId)) {
        mixedDirectParentColors(graph, postId).fold(RGB("#FFFFFF").lab)(mixed => mixColors(mixed, postDefaultColor))
      }
      else
        postDefaultColor
    }
  }

  def computeTagColor(graph: Graph, postId: PostId): Color = {
    baseColorDark(postId)
  }
}
