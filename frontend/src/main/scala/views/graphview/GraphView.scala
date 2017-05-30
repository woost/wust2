package wust.frontend.views.graphview

import org.scalajs.d3v4._
import org.scalajs.dom
import rx._
import wust.frontend.Color._
import wust.frontend.{ DevOnly, GlobalState }
import wust.graph._
import wust.util.Pipe
import scala.concurrent.ExecutionContext

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scalatags.JsDom.all._

case class MenuAction(name: String, action: SimPost => Unit, showIf: SimPost => Boolean = _ => true)
case class DropAction(name: String, action: (SimPost, SimPost) => Unit)

object KeyImplicits {
  implicit val SimPostWithKey = new WithKey[SimPost](_.id)
  implicit val SimConnectionWithKey = new WithKey[SimConnection](c => s"${c.sourceId} ${c.targetId}")
  implicit val SimRedirectedConnectionWithKey = new WithKey[SimRedirectedConnection](c => s"${c.sourceId} ${c.targetId}")
  implicit val ContainmentClusterWithKey = new WithKey[ContainmentCluster](_.id)
}

object GraphView {
  //TODO: remove disableSimulation argument, as it is only relevant for tests. Better solution?
  def apply(state: GlobalState, disableSimulation: Boolean = false)(implicit ec: ExecutionContext, ctx: Ctx.Owner) = {
    div(div().render sideEffect (new GraphView(state, _, disableSimulation)))
  }
}

class GraphView(state: GlobalState, element: dom.html.Element, disableSimulation: Boolean = false)(implicit ec: ExecutionContext, ctx: Ctx.Owner) {
  val graphState = new GraphState(state)
  val d3State = new D3State(disableSimulation)
  val postDrag = new PostDrag(graphState, d3State, onPostDrag)
  import state.{ displayGraph => rxDisplayGraph, _ }
  import graphState._

  // prepare containers where we will append elements depending on the data
  // order is important
  import KeyImplicits._
  val container = d3.select(element)
  val focusedParentsHeader = container.append(() => div(textAlign.center, marginTop := 70, fontSize := "200%", position.absolute, width := "100%").render)
  val svg = container.append("svg")
  val containmentHullSelection = SelectData.rx(ContainmentHullSelection, rxContainmentCluster)(svg.append("g"))
  val collapsedContainmentHullSelection = SelectData.rx(CollapsedContainmentHullSelection, rxCollapsedContainmentCluster)(svg.append("g"))
  val connectionLineSelection = SelectData.rx(ConnectionLineSelection, rxSimConnection)(svg.append("g"))
  val redirectedConnectionLineSelection = SelectData.rx(RedirectedConnectionLineSelection, rxSimRedirectedConnection)(svg.append("g"))

  val html = container.append("div")
  val connectionElementSelection = SelectData.rx(ConnectionElementSelection, rxSimConnection)(html.append("div"))
  val postSelection = SelectData.rx(new PostSelection(graphState, d3State, postDrag), rxSimPosts)(html.append("div"))
  val draggingPostSelection = SelectData.rxDraw(DraggingPostSelection, postDrag.draggingPosts)(html.append("div")) //TODO: place above ring menu?

  val menuSvg = container.append("svg")
  val postMenuLayer = menuSvg.append("g")
  val postMenuSelection = SelectData.rxDraw(new PostMenuSelection(graphState, d3State), rxFocusedSimPost.map(_.toJSArray))(postMenuLayer.append("g"))
  val dropMenuLayer = menuSvg.append("g")
  val dropMenuSelection = SelectData.rxDraw(new DropMenuSelection(postDrag.dropActions), postDrag.closestPosts)(dropMenuLayer.append("g"))

  val controls = container.append(() => div(
    position.absolute, left := 5, top := 100,
    button("⟳", title := "automatic layout", onclick := { () =>
      rxSimPosts.now.foreach { simPost =>
        simPost.fixedPos = js.undefined
      }
      d3State.simulation.alpha(1).restart()
      // d3State.transform = d3.zoomIdentity
      // applyZoomTransform()
    })
  ).render)

  // Arrows
  svg.append("svg:defs").append("svg:marker")
    .attr("id", "graph_arrow")
    .attr("viewBox", "0 -3 10 6") // x y w h
    .attr("refX", 35) // This is a workaround. The line is longer than displayed...
    .attr("markerWidth", 15)
    .attr("markerHeight", 9)
    .attr("orient", "auto")
    .append("svg:path")
    .attr("d", "M 0,-3 L 10,-0.5 L 10,0.5 L0,3")
    .style("fill", "#8F8F8F")

  initContainerDimensionsAndPositions()
  initEvents()

  // set the background according to focused parents
  Rx {
    val focusedParentIds = state.graphSelection().parentIds
    val parents = focusedParentIds.map(state.rawGraph().postsById)
    val parentTitles = parents.map(_.title).mkString(", ")
    println(parents + parentTitles)
    focusedParentsHeader.text(parentTitles)

    val mixedDirectParentColors = mixColors(focusedParentIds.map(baseColor))
    container
      .style("background-color", mixColors(List(mixedDirectParentColors, d3.lab("#FFFFFF"), d3.lab("#FFFFFF"))).toString)
  }

  Rx { rxDisplayGraph(); rxSimPosts(); rxSimConnection(); rxSimContainment() }.triggerLater {
    val simPosts = rxSimPosts.now
    val simConnection = rxSimConnection.now
    val simRedirectedConnection = rxSimRedirectedConnection.now
    val simContainment = rxSimContainment.now
    val simCollapsedContainment = rxSimCollapsedContainment.now

    DevOnly {
      println("    updating graph simulation")
    }

    d3State.simulation.nodes(simPosts)
    d3State.forces.connection.links(simConnection)
    d3State.forces.redirectedConnection.links(simRedirectedConnection)
    d3State.forces.containment.links(simContainment)
    d3State.forces.collapsedContainment.links(simCollapsedContainment)

    d3State.simulation.alpha(1).restart()
  }

  private def onPostDrag() {
    draggingPostSelection.draw()
  }

  private def initEvents(): Unit = {
    svg.call(
      d3.zoom()
        .on("zoom", zoomed _)
        .scaleExtent(js.Array(0.1, 10))
    )
    svg.on("click", () => focusedPostId() = None)
    d3State.simulation.on("tick", draw _)
    d3State.simulation.on("end", { () =>
      rxSimPosts.now.foreach { simPost =>
        simPost.fixedPos = simPost.pos
      }
      DevOnly { println("simulation ended.") }
    })
  }

  private def zoomed() {
    import d3State._
    transform = d3.event.asInstanceOf[ZoomEvent].transform
    applyZoomTransform()
  }

  private def applyZoomTransform() {
    import d3State._
    svg.selectAll("g").attr("transform", transform.toString)
    html.style("transform", s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})")
    postMenuLayer.attr("transform", transform.toString)
    dropMenuLayer.attr("transform", transform.toString)
  }

  private def draw() {
    postSelection.draw()
    postMenuSelection.draw()
    connectionLineSelection.draw()
    redirectedConnectionLineSelection.draw()
    connectionElementSelection.draw()
    containmentHullSelection.draw()
    collapsedContainmentHullSelection.draw()
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
}
