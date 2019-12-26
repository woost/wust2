package wust.sdk

import cats.data.NonEmptyList
import colorado._
import wust.ids._
import wust.graph.{ Node, Graph }
import rx._

import scala.collection.breakOut

case class BaseColor(base: Color) {
  val hcl = base.hcl

  @inline def finalColor(hue: Double): HCL = hcl.copy(h = hue)
  @inline def finalHex(hue: Double): String = finalColor(hue).toHex

  def of(node:Node.Content):String = colorOf(node).toHex
  def colorOf(node: Node.Content): HCL = {
    val settingsHue = for {
      nodeSettings <- node.settings
      globalSettings <- nodeSettings.global
      colorHue <- globalSettings.colorHue
    } yield colorHue
    val hue = settingsHue.getOrElse(NodeColor.genericHue(node.id))
    hcl.copy(h = hue)
  }

  def rgbOf(node: Node):RGB = colorOf(node).rgb
  def of(node: Node): String = colorOf(node).toHex
  def colorOf(node: Node): HCL = {
    node match {
      case node: Node.Content => colorOf(node)
      case node               => finalColor(NodeColor.genericHue(node.id))
    }
  }

  def of(node: Option[Node]): Option[String] = node.map(of)
  def of(node: Rx[Option[Node]])(implicit ctx: Ctx.Owner): Rx[Option[String]] = node.map(_.map(of))
  def of(nodeId: NodeId, graph: Graph): Option[String] = of(graph.nodesById(nodeId))
  def ofNodeWithFallback(nodeId: NodeId, graph: Graph): String = of(graph.nodesById(nodeId)).getOrElse(finalHex(NodeColor.genericHue(nodeId)))
  def of(nodeId: Option[NodeId], graph: Graph): Option[String] = nodeId.flatMap(nodeId => of(nodeId, graph))
  def of(nodeId: NodeId, graph: Rx[Graph])(implicit ctx: Ctx.Owner): Rx[Option[String]] = Rx{ of(graph().nodesById(nodeId)) }
  def of(nodeId: Option[NodeId], graph: Rx[Graph])(implicit ctx: Ctx.Owner): Rx[Option[String]] = Rx{ nodeId.flatMap(nodeId => of(graph().nodesById(nodeId))) }
}

object NodeColor {
  def goodHue(hueFraction: Double): Double = {
    // the hues between 1 and 1.8 look ugly (dark yellow)
    // Color preview:
    // div(
    //  color.white,
    //  Range.Double(1, 1.8, 0.1).map(hue =>
    //      div(
    //        border := "2px solid white",
    //        margin := "30px",
    //        height := "100px",
    //        backgroundColor := BaseColors.pageBg.copy(h = hue).toHex,
    //        h2("Interesting Title", hue)
    //      )
    //  )
    // ),
    //
    // so we skip this interval
    // 0                   2*PI
    // |--------------------|
    //      |----|
    //       ugly
    //
    // |----|----------|
    //      ^
    //     removed ugly interval here
    //

    val skipRangeStart = 1.0
    val skipRangeEnd = 1.8
    val skipRangeSize = skipRangeEnd - skipRangeStart
    val fullRangeSize = Math.PI * 2
    val selectedRangeSize = fullRangeSize - skipRangeSize
    val scaledHue = hueFraction * selectedRangeSize
    if (scaledHue < skipRangeStart) scaledHue
    else skipRangeSize + scaledHue
  }

  def genericHue(seed: Any): Double = {
    val rnd = new scala.util.Random(new scala.util.Random(seed.hashCode).nextLong()) // else nextDouble is too predictable

    goodHue(hueFraction = rnd.nextDouble())
  }

  @inline def defaultHue(id: NodeId): Double = genericHue(id)

  val pageBg = BaseColor(BaseColors.pageBg)
  val pageBgLight = BaseColor(BaseColors.pageBgLight)
  val kanbanColumnBg = BaseColor(BaseColors.kanbanColumnBg)
  val eulerBg = BaseColor(BaseColors.eulerBg)
  val tag = BaseColor(BaseColors.tag)
  val accent = BaseColor(BaseColors.accent)
}
