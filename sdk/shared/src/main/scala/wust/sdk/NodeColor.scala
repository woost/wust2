package wust.sdk

import cats.data.NonEmptyList
import colorado._
import wust.ids._

import scala.collection.breakOut

object NodeColor {
  def genericHue(seed: Any): Double = {
    val rnd = new scala.util.Random(new scala.util.Random(seed.hashCode).nextLong()) // else nextDouble is too predictable
    rnd.nextDouble() * Math.PI * 2
  }

  @inline def hue(id: NodeId): Double = genericHue(id)
  @inline def hue(id: Option[NodeId]): Option[Double] = id map genericHue
  @inline def eulerBgColor(id: NodeId): HCL = BaseColors.eulerBg.copy(h = hue(id))
  @inline def tagColor(nodeId: NodeId): HCL =  BaseColors.tag.copy(h = hue(nodeId))
  @inline def accentColor(nodeId: NodeId): HCL =  BaseColors.accent.copy(h = hue(nodeId))

  def mixHues(parentIds: Iterable[NodeId]): Option[Double] =
    NonEmptyList
      .fromList(parentIds.map(id => BaseColors.pageBgLight.copy(h = hue(id)))(breakOut): List[Color])
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
}
