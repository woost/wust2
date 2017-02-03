package frontend.graphview

import frontend._

import graph._
import math._

import scalajs.js
import js.JSConverters._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import vectory._
import org.scalajs.d3v4._
import util.collectionHelpers._

class PostMenuSelection(container: Selection[dom.EventTarget])(implicit env: GraphView.D3Environment)
  extends DataSelection[SimPost](container, "g", keyFunction = Some((p: SimPost) => p.id)) {
  import env._

  override def enter(menu: Selection[SimPost]) {
    val pie = d3.pie()
      .value(1)
      .padAngle(menuPaddingAngle)

    val arc = d3.arc()
      .innerRadius(menuInnerRadius)
      .outerRadius(menuOuterRadius)
      .cornerRadius(menuCornerRadius)

    val pieData = menuActions.toJSArray
    val ringMenuArc = menu.selectAll("path")
      .data(pie(pieData))
    val ringMenuLabels = menu.selectAll("text")
      .data(pie(pieData))

    ringMenuArc.enter()
      .append("path")
      .attr("d", (d: PieArcDatum[MenuAction]) => arc(d))
      .attr("fill", "rgba(0,0,0,0.7)")
      .style("cursor", "pointer")
      .style("pointer-events", "all")
      .on("click", (d: PieArcDatum[MenuAction]) => focusedPost.foreach(d.data.action(_, simulation)))

    ringMenuLabels.enter()
      .append("text")
      .text((d: PieArcDatum[MenuAction]) => d.data.symbol)
      .attr("text-anchor", "middle")
      .attr("fill", "white")
      .attr("x", (d: PieArcDatum[MenuAction]) => arc.centroid(d)(0))
      .attr("y", (d: PieArcDatum[MenuAction]) => arc.centroid(d)(1))
  }

  override def drawCall(menu: Selection[SimPost]) {
    menu.attr("transform", (p: SimPost) => s"translate(${p.x}, ${p.y})")
  }
}
