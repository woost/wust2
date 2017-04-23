package wust.frontend.views.graphview

import autowire._
import boopickle.Default._
import org.scalajs.d3v4._
import rxext._
import wust.frontend._
import wust.util.collection._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.math._
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

class PostMenuSelection(graphState: GraphState, d3State: D3State) extends DataSelection[SimPost] {
  val menuOuterRadius = 100.0
  val menuInnerRadius = 30.0
  val menuPaddingAngle = 2.0 * Pi / 200.0
  val menuCornerRadius = 2.0

  val menuActions = // TODO indication for toggle button? switch string/appearance on basis of value?
    MenuAction("Collapse", { (p: SimPost, _: Simulation[SimPost]) => graphState.rxCollapsedPostIds.updatef(_.toggle(p.id)) }) ::
      MenuAction("Edit", { (p: SimPost, _: Simulation[SimPost]) => graphState.rxEditedPostId() = Option(p.id) }) ::
      // MenuAction("Split", { (p: SimPost, s: Simulation[SimPost]) => logger.info(s"Split: ${p.id}") }) ::
      MenuAction("Delete", { (p: SimPost, _: Simulation[SimPost]) => Client.api.deletePost(p.id).call() }) ::
      MenuAction("Autopos", { (p: SimPost, s: Simulation[SimPost]) => p.fixedPos = js.undefined; s.restart() }) :: //TODO:  hide or on/off when already auto positioned
      MenuAction("Focus", { (p: SimPost, _: Simulation[SimPost]) => graphState.state.graphSelection.updatef(_.add(p.id)) }) ::
      Nil

  override val tag = "g"
  override def enter(menu: Enter[SimPost]) {
    menu.append { (simPost: SimPost) =>
      import d3State.simulation
      import graphState.rxFocusedSimPost

      import scalatags.JsDom.svgTags._

      val menu = d3.select(g().render)

      val pie = d3.pie()
        .value(1)
        .padAngle(menuPaddingAngle)

      val pieData = menuActions.toJSArray
      val ringMenuArc = menu.selectAll("path")
        .data(pie(pieData))
      val ringMenuLabels = menu.selectAll("text")
        .data(pie(pieData))

      val arc = d3.arc()
        .innerRadius(menuInnerRadius)
        .outerRadius(menuOuterRadius)
        .cornerRadius(menuCornerRadius)

      ringMenuArc.enter()
        .append("path")
        .attr("d", (d: PieArcDatum[MenuAction]) => arc(d))
        .attr("fill", "rgba(0,0,0,0.7)")
        .style("cursor", "pointer")
        .style("pointer-events", "all")
        .on("click", { (d: PieArcDatum[MenuAction]) =>
          DevOnly {
            println(s"\nMenu ${d.data.name}: [${simPost.id}]${simPost.title}")
          }
          d.data.action(simPost, simulation)
          rxFocusedSimPost() = None
        })
        .on("mousedown", (_: PieArcDatum[MenuAction]) => d3.event.asInstanceOf[org.scalajs.dom.Event].preventDefault()) // disable selecting text in menu

      ringMenuLabels.enter()
        .append("text")
        .text((d: PieArcDatum[MenuAction]) => d.data.name)
        .attr("text-anchor", "middle")
        .attr("fill", "white")
        .attr("x", (d: PieArcDatum[MenuAction]) => arc.centroid(d)(0))
        .attr("y", (d: PieArcDatum[MenuAction]) => arc.centroid(d)(1))

      menu.node()
    }

  }

  override def draw(menu: Selection[SimPost]) {
    menu.attr("transform", (p: SimPost) => s"translate(${p.x}, ${p.y})")
  }
}
