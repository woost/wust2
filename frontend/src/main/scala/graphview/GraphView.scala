package frontend.graphview

import frontend._

import scalajs.js
import js.JSConverters._
import scala.scalajs.js.annotation._
import org.scalajs.dom
import org.scalajs.dom.console
import dom.raw.HTMLElement
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.outr.scribe._

import vectory._
import mhtml._

import graph._
import Color._
import collection.breakOut
import math._

import org.scalajs.d3v4._
import util.collectionHelpers._
import autowire._
import boopickle.Default._

case class MenuAction(symbol: String, action: (SimPost, Simulation[SimPost]) => Unit)
case class DropAction(symbol: String, color: String, action: (SimPost, SimPost) => Unit)

class Forces {
  val center = d3.forceCenter[SimPost]()
  val gravityX = d3.forceX[SimPost]()
  val gravityY = d3.forceY[SimPost]()
  val repel = d3.forceManyBody[SimPost]()
  val collision = d3.forceCollide[SimPost]() //TODO: rectangle collision detection?
  // val connection = d3.forceLink[ExtendedD3Node, SimConnects]()
  val connection = new CustomLinkForce[ExtendedD3Node, SimConnects]
  val containment = d3.forceLink[SimPost, SimContains]()
  //TODO: push posts out of containment clusters they don't belong to
}
object Forces {
  def apply(height: Int, width: Int) = {
    val forces = new Forces

    forces.center.x(width / 2).y(height / 2)
    forces.gravityX.x(width / 2)
    forces.gravityY.y(height / 2)

    forces.repel.strength(-1000)
    forces.collision.radius((p: SimPost) => p.collisionRadius)

    // forces.connection.distance(100)
    forces.containment.distance(100)

    forces.gravityX.strength(0.1)
    forces.gravityY.strength(0.1)

    forces
  }
}

object Simulation {
  def apply(forces: Forces): Simulation[SimPost] = d3.forceSimulation[SimPost]()
    .force("center", forces.center)
    .force("gravityx", forces.gravityX)
    .force("gravityy", forces.gravityY)
    .force("repel", forces.repel)
    .force("collision", forces.collision)
    // .force("connection", forces.connection.asInstanceOf[Link[SimPost, SimulationLink[SimPost, SimPost]]])
    .force("connection", forces.connection.forJavaScriptIdiots().asInstanceOf[Force[SimPost]])
    .force("containment", forces.containment)
}

class GraphState(rxGraph: Rx[Graph], val focusedPostId: Var[Option[AtomId]]) { thisEnv =>
  def graph = rxGraph.value
  private implicit val stateEnv = thisEnv

  //TODO: dynamic by screen size, refresh on window resize, put into centering force
  private val width = 640
  private val height = 480

  //TODO: multiple menus for multi-user multi-touch interface?
  focusedPostId.foreach(_ foreach (id => postMenuSelection.update(js.Array(postSelection.postIdToSimPost(id)))))
  val focusedPost = focusedPostId.map(_.flatMap(id => postSelection.postIdToSimPost.get(id)))

  var transform: Transform = d3.zoomIdentity // stores current pan and zoom

  // prepare containers where we will append elements depending on the data
  // order is important
  val container = d3.select("#here_be_d3")
  val svg = container.append("svg")
  val containmentHullSelection = new ContainmentHullSelection(svg.append("g"))
  val connectionLineSelection = new ConnectionLineSelection(svg.append("g"))

  val html = container.append("div")
  val connectionElementSelection = new ConnectionElementSelection(html.append("div"))
  val postSelection = new PostSelection(html.append("div"))
  val draggingPostSelection = new DraggingPostSelection(html.append("div")) //TODO: place above ring menu?

  val menuSvg = container.append("svg")
  val postMenuLayer = menuSvg.append("g")
  val postMenuSelection = new PostMenuSelection(postMenuLayer.append("g"))
  val dropMenuLayer = menuSvg.append("g")
  val dropMenuSelection = new DropMenuSelection(dropMenuLayer.append("g"))

  val forces = Forces(height, width)
  val simulation = Simulation(forces)

  initContainerDimensionsAndPositions()
  initEvents()

  private def initEvents() {
    svg.call(d3.zoom().on("zoom", zoomed _))
    svg.on("click", () => focusedPostId := None)
    simulation.on("tick", draw _)
  }

  private def zoomed() {
    transform = d3.event.asInstanceOf[ZoomEvent].transform
    svg.selectAll("g").attr("transform", transform.toString)
    html.style("transform", s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})")
    postMenuLayer.attr("transform", transform.toString)
    dropMenuLayer.attr("transform", transform.toString)
  }

  private def draw() {
    postSelection.draw()
    connectionLineSelection.draw()
    connectionElementSelection.draw()
    containmentHullSelection.draw()
    postMenuSelection.draw()
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

    menuSvg
      .style("position", "absolute")
      .style("width", "100%")
      .style("height", "100%")
      .style("pointer-events", "none")
  }

  def update(newGraph: Graph) {
    import postSelection.postIdToSimPost

    postSelection.update(newGraph.posts.values)
    connectionLineSelection.update(newGraph.connections.values)
    connectionElementSelection.update(connectionLineSelection.data)
    containmentHullSelection.update(newGraph.containments.values)

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
      new SimContains(c, postIdToSimPost(c.parentId), postIdToSimPost(c.childId))
    }.toJSArray

    simulation.nodes(postSelection.data)
    forces.connection.links = connectionLineSelection.data
    forces.containment.links(containmentData)

    simulation.alpha(1).restart()
  }
}

object GraphView { thisEnv =>
  val component = {
    //TODO: use mhtml-onattached instead of init
    <div id="here_be_d3"></div>
  }

  val init: (Rx[Graph], Var[Option[AtomId]]) => Unit = {
    var cancelable: Option[Cancelable] = None
    (rxGraph, focusedPost) => {
      cancelable.foreach(_.cancel)
      val state = new GraphState(rxGraph, focusedPost)
      cancelable = Some(rxGraph.foreach(state.update)) //TODO: foreachNext? leak?
    }
  }
}
