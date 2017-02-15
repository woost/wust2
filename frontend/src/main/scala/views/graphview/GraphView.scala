package frontend.views.graphview

import scalajs.js
import js.JSConverters._
import org.scalajs.d3v4._
import mhtml.Cancelable
import math._

import frontend.GlobalState
import graph._
import frontend.Color._

case class MenuAction(symbol: String, action: (SimPost, Simulation[SimPost]) => Unit)
case class DropAction(symbol: String, color: String, action: (SimPost, SimPost) => Unit)

object KeyImplicits {
  implicit val SimPostWithKey = new WithKey[SimPost](_.id)
  implicit val SimConnectsWithKey = new WithKey[SimConnects](_.id)
  implicit val ContainmentClusterWithKey = new WithKey[ContainmentCluster](_.id)
}

class GraphView(state: GlobalState) {
  val graphState = new GraphState(state)
  val d3State = new D3State
  val postDrag = new PostDrag(graphState, d3State, onPostDrag)
  import state._

  // prepare containers where we will append elements depending on the data
  // order is important
  import KeyImplicits._
  val container = d3.select("#here_be_d3")
  val svg = container.append("svg")
  val containmentHullSelection = SelectData.rx(ContainmentHullSelection, graphState.rxContainmentCluster)(svg.append("g"))
  val connectionLineSelection = SelectData.rx(ConnectionLineSelection, graphState.rxSimConnects)(svg.append("g"))

  val html = container.append("div")
  val connectionElementSelection = SelectData.rx(ConnectionElementSelection, graphState.rxSimConnects)(html.append("div"))
  val postSelection = SelectData.rx(new PostSelection(graphState, postDrag), graphState.rxSimPosts)(html.append("div"))
  val draggingPostSelection = SelectData.rxDraw(DraggingPostSelection, postDrag.draggingPosts)(html.append("div")) //TODO: place above ring menu?

  val menuSvg = container.append("svg")
  val postMenuLayer = menuSvg.append("g")
  val postMenuSelection = SelectData.rxDraw(new PostMenuSelection(graphState, d3State), graphState.focusedPost.map(_.toJSArray))(postMenuLayer.append("g"))
  val dropMenuLayer = menuSvg.append("g")
  val dropMenuSelection = SelectData.rxDraw(DropMenuSelection, postDrag.closestPosts)(dropMenuLayer.append("g"))

  initContainerDimensionsAndPositions()
  val eventCancel = initEvents()
  val updateCancel = graph.foreach(update)

  private def cancel() {
    eventCancel.cancel()
    updateCancel.cancel()
  }

  private def onPostDrag() {
    draggingPostSelection.draw()
  }

  private def initEvents(): Cancelable = {
    svg.call(d3.zoom().on("zoom", zoomed _))
    svg.on("click", () => focusedPostId := None)
    d3State.simulation.on("tick", draw _)
    graphState.rxSimPosts.foreach(data => d3State.simulation.nodes(data))
    //TODO: currently produces NaNs: graphState.rxSimConnects.foreach { data => d3State.forces.connection.links = data }
  }

  private def zoomed() {
    import d3State._
    transform = d3.event.asInstanceOf[ZoomEvent].transform
    svg.selectAll("g").attr("transform", transform.toString)
    html.style("transform", s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})")
    postMenuLayer.attr("transform", transform.toString)
    dropMenuLayer.attr("transform", transform.toString)
  }

  private def draw() {
    postSelection.draw()
    postMenuSelection.draw()
    connectionLineSelection.draw()
    connectionElementSelection.draw()
    containmentHullSelection.draw()
  }

  private def initContainerDimensionsAndPositions() {
    container
      .style("position", "absolute")
      .style("top", "0")
      .style("left", "0")
      .style("z-index", "-1")
      .style("width", "100%")
      .style("height", "100%")
      .style("overflow", "hidden")

    svg
      .style("position", "absolute")
      .style("width", "100%")
      .style("height", "100%")

    html
      .style("position", "absolute")
      .style("pointer-events", "none") // pass through to svg (e.g. zoom)
      .style("transform-origin", "top left") // same as svg default
      .style("width", "100%")
      .style("height", "100%")

    menuSvg
      .style("position", "absolute")
      .style("width", "100%")
      .style("height", "100%")
      .style("pointer-events", "none")
  }

  private def update(newGraph: Graph) {
    import d3State._, graphState._

    //TODO: this can be removed after implementing link force which supports hyperedges
    forces.connection.strength = { (e: SimConnects, _: Int, _: js.Array[SimConnects]) =>
      val targetDeg = e.target match {
        case p: SimPost => newGraph.fullDegree(p.post)
        case _: SimConnects => 2
      }
      1.0 / min(newGraph.fullDegree(e.source.post), targetDeg)
    }

    forces.containment.strength { (e: SimContains) =>
      1.0 / min(newGraph.fullDegree(e.source.post), newGraph.fullDegree(e.target.post))
    }

    val containmentData = newGraph.containments.values.map { c =>
      new SimContains(c, postIdToSimPost.value(c.parentId), postIdToSimPost.value(c.childId))
    }.toJSArray

    forces.connection.links = rxSimConnects.value
    forces.containment.links(containmentData)

    simulation.alpha(1).restart()
  }
}

object GraphView { thisEnv =>
  val component = {
    //TODO: use mhtml-onattached instead of init
    <div id="here_be_d3"></div>
  }

  val init: GlobalState => Unit = {
    var view: Option[GraphView] = None
    state => {
      view.foreach(_.cancel)
      view = Some(new GraphView(state))
    }
  }
}
