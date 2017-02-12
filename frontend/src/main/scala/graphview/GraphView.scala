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

class RxPosts(val rxGraph: Rx[Graph], focusedPostId: SourceVar[Option[AtomId], Option[AtomId]]) {
  val rxSimPosts: Rx[js.Array[SimPost]] = rxGraph.map { graph =>
    graph.posts.values.map { p =>
      val sp = new SimPost(p)
      postIdToSimPost.value.get(sp.id).foreach { old =>
        // preserve position, velocity and fixed position
        sp.x = old.x
        sp.y = old.y
        sp.vx = old.vx
        sp.vy = old.vy
        sp.fx = old.fx
        sp.fy = old.fy
      }

      def parents = graph.parents(p.id)
      def hasParents = parents.nonEmpty
      def mixedDirectParentColors = mixColors(parents.map((p: Post) => baseColor(p.id)))
      def hasChildren = graph.children(p.id).nonEmpty
      sp.border = (
        if (hasChildren)
          "2px solid rgba(0,0,0,0.4)"
        else { // no children
          "2px solid rgba(0,0,0,0.1)"
        }
      ).toString()
      sp.color = (
        if (hasChildren)
          baseColor(p.id)
        else { // no children
          if (hasParents)
            mixColors(mixedDirectParentColors, postDefaultColor)
          else
            postDefaultColor
        }
      ).toString()
      sp

    }.toJSArray
  }

  val postIdToSimPost: Rx[Map[AtomId, SimPost]] = rxSimPosts.map(nd => (nd: js.ArrayOps[SimPost]).by(_.id))

  //TODO: multiple menus for multi-user multi-touch interface?
  val focusedPost = for {
    idOpt <- focusedPostId
    map <- postIdToSimPost
  } yield idOpt.flatMap(map.get)

  val rxSimConnects = for {
    graph <- rxGraph
    postIdToSimPost <- postIdToSimPost
  } yield {
    val newData = graph.connections.values.map { c =>
      new SimConnects(c, postIdToSimPost(c.sourceId))
    }.toJSArray

    val connIdToSimConnects: Map[AtomId, SimConnects] = (newData: js.ArrayOps[SimConnects]).by(_.id)

    // set hyperedge targets, goes away with custom linkforce
    newData.foreach { e =>
      e.target = postIdToSimPost.getOrElse(e.targetId, connIdToSimConnects(e.targetId))
    }

    newData
  }

  val rxContainmentCluster = rxGraph.map { graph =>
    val containments = graph.containments.values
    val parents: Seq[Post] = containments.map(c => graph.posts(c.parentId)).toSeq.distinct

    // due to transitive containment visualisation,
    // inner posts should be drawn above outer ones.
    // TODO: breaks on circular containment

    val ordered = algorithm.topologicalSort(parents, (p: Post) => graph.children(p.id))

    ordered.map(p =>
      new ContainmentCluster(
        parent = postIdToSimPost.value(p.id),
        children = graph.transitiveChildren(p.id).map(p => postIdToSimPost.value(p.id))(breakOut)
      )).toJSArray
  }

  // rxSimPosts.foreach(v => println(s"post rxSimPosts update: $v"))
  // postIdToSimPost.foreach(v => println(s"postIdToSimPost update: $v"))
  // for (v <- focusedPost) { println(s"focusedSimPost update: $v") }
}

class D3State {
  //TODO: dynamic by screen size, refresh on window resize, put into centering force
  private val width = 640
  private val height = 480

  var transform: Transform = d3.zoomIdentity // stores current pan and zoom
  val forces = Forces(height, width)
  val simulation = Simulation(forces)
}

class GraphState(val rxGraph: Rx[Graph], val focusedPostId: SourceVar[Option[AtomId], Option[AtomId]]) {

  val rxPosts = new RxPosts(rxGraph, focusedPostId)
  val d3State = new D3State
  val postDrag = new PostDrag(rxPosts, d3State, onPostDrag)
  import rxPosts._, postDrag.{draggingPosts, closestPosts}

  // prepare containers where we will append elements depending on the data
  // order is important
  val container = d3.select("#here_be_d3")
  val svg = container.append("svg")
  val containmentHullSelection = SelectData.rx(ContainmentHullSelection, rxContainmentCluster)(svg.append("g"))
  val connectionLineSelection = SelectData.rx(ConnectionLineSelection, rxSimConnects)(svg.append("g"))

  val html = container.append("div")
  val connectionElementSelection = SelectData.rx(ConnectionElementSelection, rxSimConnects)(html.append("div"))
  val postSelection = SelectData.rx(new PostSelection(rxPosts, postDrag), rxSimPosts)(html.append("div"))
  val draggingPostSelection = SelectData.autoRx(DraggingPostSelection, draggingPosts)(html.append("div")) //TODO: place above ring menu?

  val menuSvg = container.append("svg")
  val postMenuLayer = menuSvg.append("g")
  val postMenuSelection = SelectData.autoRx(new PostMenuSelection(rxPosts, d3State), focusedPost.map(_.toJSArray))(postMenuLayer.append("g"))
  val dropMenuLayer = menuSvg.append("g")
  val dropMenuSelection = SelectData.autoRx(new DropMenuSelection(postDrag), closestPosts)(dropMenuLayer.append("g"))

  initContainerDimensionsAndPositions()
  initEvents()

  private def onPostDrag() {
    draggingPostSelection.draw()
  }

  private def initEvents() {
    svg.call(d3.zoom().on("zoom", zoomed _))
    svg.on("click", () => focusedPostId := None)
    d3State.simulation.on("tick", draw _)
    rxSimPosts.foreach(data => d3State.simulation.nodes(data))
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

    menuSvg
      .style("position", "absolute")
      .style("width", "100%")
      .style("height", "100%")
      .style("pointer-events", "none")
  }

  def update(newGraph: Graph) {
    import d3State._

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

    // forces.connection.links = connectionLineSelection.data
    forces.containment.links(containmentData)

    simulation.alpha(1).restart()
  }
}

object GraphView { thisEnv =>
  val component = {
    //TODO: use mhtml-onattached instead of init
    <div id="here_be_d3"></div>
  }

  val init: (Rx[Graph], SourceVar[Option[AtomId], Option[AtomId]]) => Unit = {
    var cancelable: Option[Cancelable] = None
    (rxGraph, focusedPost) => {
      cancelable.foreach(_.cancel)
      val state = new GraphState(rxGraph, focusedPost)
      cancelable = Some(rxGraph.foreach(state.update)) //TODO: foreachNext? leak?
    }
  }
}

// Uncaught TypeError: Cannot read property 'target$1' of null
// at $c_Lfrontend_graphview_GraphState.init___Lmhtml_Rx__Lfrontend_SourceVar (GraphView.scala:273)
// at $c_Lfrontend_graphview_GraphView$$anonfun$9.apply__Lmhtml_Rx__Lfrontend_SourceVar__V (GraphView.scala:281)
// at $c_Lfrontend_graphview_GraphView$$anonfun$9.apply__O__O__O (GraphView.scala:281)
// at $c_Lfrontend_Main$.main__V (Main.scala:19)
// at $c_Lfrontend_Main$.$$js$exported$meth$main__O (Main.scala:18)
// at $c_Lfrontend_Main$.main (Main.scala:18)
// at scalajsbundler-fastOptJS-launcher.js:1
