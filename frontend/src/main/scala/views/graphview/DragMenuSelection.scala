package wust.frontend.views.graphview

import d3v4._

import scala.math._
import scala.scalajs.js

class DragMenuSelection(dragActions: js.Array[DragAction], d3State: D3State) extends DataSelection[SimPost] {
  val menuOuterRadius = 100.0
  val menuInnerRadius = 30.0
  val menuPaddingAngle = 2.0 * Pi / 200.0
  val menuCornerRadius = 2.0

  override val tag = "g"
  override def enterAppend(menu: Selection[SimPost]):Unit = {
    val pie = d3.pie()
      .value(1)
      .padAngle(menuPaddingAngle)

    val arc = d3.arc()
      .innerRadius(menuInnerRadius)
      .outerRadius(menuOuterRadius)
      .cornerRadius(menuCornerRadius)

    val pieData = dragActions
    val ringMenuArc = menu.selectAll("path")
      .data(pie(pieData))
    val ringMenuLabels = menu.selectAll("text")
      .data(pie(pieData))

    ringMenuArc.enter()
      .append("path")
      .attr("d", (d: PieArcDatum[DragAction]) => arc(d))
      .attr("fill", "rgba(0,0,0,0.7)")
      .style("cursor", "pointer")
      .style("pointer-events", "all")

    ringMenuLabels.enter()
      .append("text")
      .text((d: PieArcDatum[DragAction]) => d.data.name)
      .attr("text-anchor", "middle")
      .attr("fill", "white")
      .attr("x", (d: PieArcDatum[DragAction]) => arc.centroid(d)._1)
      .attr("y", (d: PieArcDatum[DragAction]) => arc.centroid(d)._2)
  }

  override def draw(menu: Selection[SimPost]):Unit = {
    menu.attr("transform", { (p: SimPost) =>
      val x = d3State.transform.now.applyX(p.x)
      val y = d3State.transform.now.applyY(p.y)
      s"translate(${x}, ${y})"
    })
  }
}
