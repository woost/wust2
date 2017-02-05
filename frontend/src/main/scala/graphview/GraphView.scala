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

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import fdietze.scalajs.react.component._

import vectory._

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

object GraphView extends Playground[Graph]("GraphView") {
  val environmentFactory = new D3Environment(_)

  class D3Environment(component: HTMLElement) extends Environment { thisEnv =>
    var graph: Graph = _
    override def setProps(newGraph: Graph) { graph = newGraph }
    override def propsUpdated(oldGraph: Graph) { update(graph) }

    private implicit val implicitEnv = thisEnv

    //TODO: dynamic by screen size, refresh on window resize, put into centering force
    val width = 640
    val height = 480

    //TODO: multiple menus for multi-user multi-touch interface?
    var _focusedPost: Option[SimPost] = None
    def focusedPost = _focusedPost
    def focusedPost_=(target: Option[SimPost]) {
      postMenuSelection.update(target.toJSArray)
      _focusedPost = target
      _focusedPost match {
        case Some(post) =>
          AppCircuit.dispatch(SetFocusedPost(Some(post.id)))
        case None =>
          AppCircuit.dispatch(SetFocusedPost(None))
      }
    }

    var transform: Transform = d3.zoomIdentity // stores current pan and zoom

    // prepare containers where we will append elements depending on the data
    // order is important
    val container = d3.select(component)
    val svg = container.append("svg")
    val containmentHullSelection = new ContainmentHullSelection(svg.append("g"))
    val connectionLineSelection = new ConnectionLineSelection(svg.append("g"))

    val html = container.append("div")
    val connectionElementSelection = new ConnectionElementSelection(html.append("div"))
    val postSelection = new PostSelection(html.append("div"))
    val draggingPostSelection = new DraggingPostSelection(html.append("div")) //TODO: place above ring menu?

    val menuSvg = container.append("svg")
    val menuLayer = menuSvg.append("g")
    val postMenuSelection = new PostMenuSelection(menuLayer.append("g"))

    initContainerDimensionsAndPositions()
    initZoomEvents()
    val forces = initForces()
    val simulation = initSimulation()

    svg.on("click", () => focusedPost = None)

    /////////////////////////////

    def initForces() = {
      object forces {
        val center = d3.forceCenter[SimPost]()
        val gravityX = d3.forceX[SimPost]()
        val gravityY = d3.forceY[SimPost]()
        val repel = d3.forceManyBody[SimPost]()
        val collision = d3.forceCollide[SimPost]() //TODO: rectangle collision detection?
        val connection = d3.forceLink[ExtendedD3Node, SimConnects]()
        val containment = d3.forceLink[SimPost, SimContains]()
      }

      forces.center.x(width / 2).y(height / 2)
      forces.gravityX.x(width / 2)
      forces.gravityY.y(height / 2)

      forces.repel.strength(-1000)
      forces.collision.radius((p: SimPost) => p.collisionRadius)

      forces.connection.distance(100)
      forces.containment.distance(100)

      forces.gravityX.strength(0.1)
      forces.gravityY.strength(0.1)

      forces
    }

    def initSimulation(): Simulation[SimPost] = {
      d3.forceSimulation[SimPost]()
        .force("center", forces.center)
        .force("gravityx", forces.gravityX)
        .force("gravityy", forces.gravityY)
        .force("repel", forces.repel)
        .force("collision", forces.collision)
        .force("connection", forces.connection.asInstanceOf[Link[SimPost, SimulationLink[SimPost, SimPost]]])
        .force("containment", forces.containment)
        .on("tick", draw _)
    }

    def initZoomEvents() {
      svg.call(d3.zoom().on("zoom", zoomed _))
    }

    def zoomed() {
      transform = d3.event.asInstanceOf[ZoomEvent].transform
      svg.selectAll("g").attr("transform", transform.toString)
      html.style("transform", s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})")
      menuLayer.attr("transform", transform.toString)
    }

    def initContainerDimensionsAndPositions() {
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

    def update(graph: Graph) {
      import postSelection.postIdToSimPost

      postSelection.update(graph.posts.values)
      connectionLineSelection.update(graph.connections.values)
      connectionElementSelection.update(connectionLineSelection.data)
      containmentHullSelection.update(graph.containments.values)

      focusedPost = focusedPost.collect { case sp if postIdToSimPost.isDefinedAt(sp.id) => postIdToSimPost(sp.id) }

      //TODO: this can be removed after implementing link force which supports hyperedges
      forces.connection.strength { (e: SimConnects) =>
        import graph.fullDegree
        val targetDeg = e.target match {
          case p: SimPost => fullDegree(p.post)
          case _: SimConnects => 2
        }
        1.0 / min(fullDegree(e.source.post), targetDeg)
      }

      forces.containment.strength { (e: SimContains) =>
        import graph.fullDegree
        1.0 / min(fullDegree(e.source.post), fullDegree(e.target.post))
      }

      val containmentData = graph.containments.values.map { c =>
        new SimContains(c, postIdToSimPost(c.parentId), postIdToSimPost(c.childId))
      }.toJSArray

      simulation.nodes(postSelection.data)
      forces.connection.links(connectionLineSelection.data)
      forces.containment.links(containmentData)

      simulation.alpha(1).restart()
    }

    def draw() {
      postSelection.draw()
      connectionLineSelection.draw()
      connectionElementSelection.draw()
      containmentHullSelection.draw()
      postMenuSelection.draw()
    }
  }
}
