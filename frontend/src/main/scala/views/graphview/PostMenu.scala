package frontend.views.graphview

import frontend._

import graph._
import math._
import mhtml._

import scalajs.js
import js.JSConverters._
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import org.scalajs.dom.console
import org.scalajs.dom.raw.HTMLElement
import vectory._
import org.scalajs.d3v4._
import util.collectionHelpers._
import autowire._
import boopickle.Default._
import com.outr.scribe._

class PostMenuSelection(graphState: GraphState, d3State: D3State) extends DataSelection[SimPost] {
  val menuOuterRadius = 100.0
  val menuInnerRadius = 30.0
  val menuPaddingAngle = 2.0 * Pi / 200.0
  val menuCornerRadius = 2.0

  val menuActions = (
    MenuAction("Edit", { (p: SimPost, s: Simulation[SimPost]) => graphState.editedPostId := Some(p.id) }) ::
    MenuAction("Split", { (p: SimPost, s: Simulation[SimPost]) => logger.info(s"Split: ${p.id}") }) ::
    MenuAction("Delete", { (p: SimPost, s: Simulation[SimPost]) => Client.api.deletePost(p.id).call() }) ::
    MenuAction("Autopos", { (p: SimPost, s: Simulation[SimPost]) => p.fixedPos = js.undefined; s.restart() }) :: //TODO:  hide or on/off when already auto positioned
    Nil
  )

  override val tag = "g"
  override def enter(menu: Selection[SimPost]) {
    import graphState.focusedPost
    import d3State.simulation

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
      .on("click", (d: PieArcDatum[MenuAction]) => focusedPost.value.foreach(d.data.action(_, simulation)))

    ringMenuLabels.enter()
      .append("text")
      .text((d: PieArcDatum[MenuAction]) => d.data.symbol)
      .attr("text-anchor", "middle")
      .attr("fill", "white")
      .attr("x", (d: PieArcDatum[MenuAction]) => arc.centroid(d)(0))
      .attr("y", (d: PieArcDatum[MenuAction]) => arc.centroid(d)(1))
  }

  override def draw(menu: Selection[SimPost]) {
    menu.attr("transform", (p: SimPost) => s"translate(${p.x}, ${p.y})")
  }
}
