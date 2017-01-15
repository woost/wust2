package frontend

import scalajs.js
import js.JSConverters._
import scala.scalajs.js.annotation._
import org.scalajs.dom
import org.scalajs.dom.console
import dom.raw.HTMLElement
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import fdietze.scalajs.react.component._

import vectory._

import graph._
import collection.breakOut
import math._

import org.scalajs.d3v4._
import org.scalajs.d3v4.force._
import org.scalajs.d3v4.zoom._
import org.scalajs.d3v4.selection._
import org.scalajs.d3v4.polygon._
import org.scalajs.d3v4.drag._

case class ContainmentCluster(parent: Post, children: IndexedSeq[Post]) {
  def positions: js.Array[js.Array[Double]] = (children :+ parent).map(post => js.Array(post.x.asInstanceOf[Double], post.y.asInstanceOf[Double]))(breakOut)
  def convexHull: Option[js.Array[js.Array[Double]]] = {
    val hull = d3.polygonHull(positions)
    //TODO: how to correctly handle scalajs union type?
    if (hull == null) None
    else Some(hull.asInstanceOf[js.Array[js.Array[Double]]])
  }
}

object GraphView extends CustomComponent[Graph]("GraphView") {
  val d3js = js.Dynamic.global.d3 //TODO: write more facade types instead of using dynamic

  val width = 640
  val height = 480

  val menuOuterRadius = 100
  val menuInnerRadius = 50
  val menuRadius = (menuOuterRadius + menuInnerRadius) / 2
  val menuThickness = menuOuterRadius - menuInnerRadius

  val dragHitRadius = 50

  class Backend($: Scope) extends CustomBackend($) {
    lazy val container = d3js.select(component)
    lazy val svg = container.append("svg")
    lazy val html = container.append("div")
    lazy val postElements = html.append("div")
    lazy val connectionElements = svg.append("g")
    lazy val containmentElements = svg.append("g")
    lazy val containmentHulls = svg.append("g")
    lazy val menuSvg = container.append("svg")
    lazy val menuLayer = menuSvg.append("g")
    lazy val ringMenu = menuLayer.append("g")

    var postData: js.Array[Post] = js.Array()
    var connectionData: js.Array[Connects] = js.Array()
    var containmentData: js.Array[Contains] = js.Array()
    var containmentClusters: js.Array[ContainmentCluster] = js.Array()

    var _menuTarget: Option[Post] = None
    def menuTarget = _menuTarget
    def menuTarget_=(target: Option[Post]) {
      _menuTarget = target

      _menuTarget match {
        case Some(post) =>
          ringMenu.style("visibility", "visible")
          AppCircuit.dispatch(SetRespondingTo(Some(post.id)))
        case None =>
          ringMenu.style("visibility", "hidden")
          AppCircuit.dispatch(SetRespondingTo(None))
      }
    }

    val simulation = d3.forceSimulation[Post]()
      .force("center", d3.forceCenter())
      .force("gravityx", d3.forceX())
      .force("gravityy", d3.forceY())
      .force("repel", d3.forceManyBody())
      .force("collision", d3.forceCollide()) //TODO: rectangle collision detection?
      .force("connection", d3.forceLink())
      .force("containment", d3.forceLink())

    simulation.on("tick", draw _)

    var transform: Transform = d3.zoomIdentity // stores current pan and zoom

    val menuActions = {
      import autowire._
      import boopickle.Default._

      (
        ("A", { (p: Post) => println(s"A: $p") }) ::
        ("B", { (p: Post) => println(s"B: $p") }) ::
        ("C", { (p: Post) => println(s"C: $p") }) ::
        ("Del", { (p: Post) => Client.wireApi.deletePost(p.id).call() }) ::
        ("Unfix", { (p: Post) => p.fixedPos = js.undefined; simulation.restart() }) ::
        Nil
      )
    }

    override def init() {
      // init lazy vals to set drawing order
      container

      svg
      containmentHulls
      containmentElements
      connectionElements

      html
      postElements

      menuSvg
      menuLayer
      ringMenu

      container
        .style("position", "relative")
        .style("width", s"${width}px")
        .style("height", s"${height}px")
        .style("border", "1px solid #DDDDDD")
        .style("overflow", "hidden")

      svg
        .style("position", "absolute")
        .attr("width", width)
        .attr("height", height)

      html
        .style("position", "absolute")
        .style("width", s"${width}px")
        .style("height", s"${height}px")
        .style("pointer-events", "none") // pass through to svg (e.g. zoom)
        .style("transform-origin", "top left") // same as svg default

      menuSvg
        .style("position", "absolute")
        .attr("width", width)
        .attr("height", height)
        .style("pointer-events", "none")

      ringMenu
        .style("visibility", "hidden")

      ringMenu
        .append("circle")
        .attr("r", menuRadius)
        .attr("stroke-width", menuThickness)
        .attr("fill", "none")
        .attr("stroke", "rgba(0,0,0,0.7)")

      for (((symbol, action), i) <- menuActions.zipWithIndex) {
        val angle = i * 2 * Math.PI / menuActions.size
        ringMenu
          .append("text")
          .text(symbol)
          .attr("fill", "white")
          .attr("x", cos(angle) * menuRadius)
          .attr("y", sin(angle) * menuRadius)
          .style("pointer-events", "all")
          .style("cursor", "pointer")
          .on("click", { () => menuTarget foreach action })
      }

      svg.call(d3js.zoom().on("zoom", zoomed _))
      svg.on("click", () => menuTarget = None)

      simulation.force[Centering[Post]]("center").x(width / 2).y(height / 2)
      simulation.force[PositioningX[Post]]("gravityx").x(width / 2)
      simulation.force[PositioningY[Post]]("gravityy").y(height / 2)

      simulation.force[ManyBody[Post]]("repel").strength(-1000)
      simulation.force[Collision[Post]]("collision").radius((p: Post) => p.collisionRadius)

      simulation.asInstanceOf[Simulation[SimulationNode]].force[force.Link[SimulationNode, Connects]]("connection").distance(100)
      simulation.force[force.Link[Post, Contains]]("containment").distance(100)

      simulation.force[PositioningX[Post]]("gravityx").strength(0.1)
      simulation.force[PositioningY[Post]]("gravityy").strength(0.1)
    }

    def zoomed() {
      transform = d3.event.asInstanceOf[ZoomEvent].transform
      svg.selectAll("g").attr("transform", transform)
      html.style("transform", s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})")
      menuLayer.attr("transform", transform)
    }

    def postDragStarted(node: HTMLElement, p: Post) {
      val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
      p.dragStart = eventPos
      p.fixedPos = eventPos
      p.pos = eventPos

      d3js.select(node).style("cursor", "move")
      simulation.stop()
    }

    def postDragged(node: HTMLElement, p: Post) {
      val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)

      p.pos = p.dragStart // prevents finding the dragged post as closest post
      val closest = simulation.find(eventPos.x, eventPos.y, dragHitRadius).toOption

      if (closest != p.dragClosest) {
        p.dragClosest.foreach(_.isClosest = false)
        closest match {
          case Some(target) if target != p =>
            target.isClosest = true
          case _ =>
        }
        p.dragClosest = closest
      }

      p.fixedPos = p.dragStart + (eventPos - p.dragStart) / transform.k
      p.pos = p.fixedPos
      draw()
    }

    def postDragEnded(node: HTMLElement, p: Post) {
      val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
      p.pos = p.dragStart // prevents finding the dragged post as closest post
      val closest = simulation.find(eventPos.x, eventPos.y, dragHitRadius).toOption
      closest match {
        case Some(target) if target != p =>
          import autowire._
          import boopickle.Default._

          Client.wireApi.connect(p.id, target.id).call()
          target.isClosest = false
          p.fixedPos = js.undefined
        case _ =>
          p.pos = eventPos
          p.fixedPos = eventPos
      }
      d3js.select(node).style("cursor", "default")
      simulation.alpha(1).restart()
    }

    override def update(p: Props, oldProps: Option[Props] = None) {
      val graph = p
      import graph.posts
      import graph.connections
      import graph.containments

      menuTarget match {
        case Some(post) if !posts.isDefinedAt(post.id) =>
          menuTarget = None
        case _ =>
      }

      postData = p.posts.values.toJSArray
      val post = postElements.selectAll("div")
        .data(postData, (p: Post) => p.id)

      connectionData = p.connections.values.map { e =>
        e.source = posts(e.sourceId)
        e.target = posts.getOrElse(e.targetId, connections(e.targetId))
        e
      }.toJSArray
      val connection = connectionElements.selectAll("line")
        .data(connectionData, (r: Connects) => r.id)

      containmentData = p.containments.values.map { e =>
        e.source = posts(e.parent)
        e.target = posts(e.child)
        e
      }.toJSArray
      val contains = containmentElements.selectAll("line")
        .data(containmentData, (r: Contains) => r.id)

      containmentClusters = {
        val parents: Seq[Post] = containments.values.map(c => posts(c.parent)).toSeq.distinct
        parents.map(p => new ContainmentCluster(p, graph.children(p).toIndexedSeq)).toJSArray
      }
      val containmentHull = containmentHulls.selectAll("path")
        .data(containmentClusters, (c: ContainmentCluster) => c.parent.id)

      post.enter().append("div")
        .text((post: Post) => post.title)
        .style("background-color", "#EEEEEE")
        .style("border", "1px solid #DDDDDD")
        .style("max-width", "100px")
        .style("position", "absolute")
        .style("cursor", "default")
        .style("pointer-events", "auto") // reenable
        .call(d3js.drag()
          .on("start", postDragStarted _: js.ThisFunction)
          .on("drag", postDragged _: js.ThisFunction)
          .on("end", postDragEnded _: js.ThisFunction))
        .on("click", { (p: Post) =>
          if (menuTarget.isEmpty || menuTarget.get != p)
            menuTarget = Some(p)
          else
            menuTarget = None

          draw()
        })
      post.exit().remove()

      connection.enter().append("line")
        .style("stroke", "#8F8F8F")
      connection.exit().remove()

      contains.enter().append("line")
        .style("stroke", "blue")
      contains.exit().remove()

      containmentHull.enter().append("path")
        .style("stroke", "#0075B8")
        .style("fill", "#00C1FF")
      containmentHull.exit().remove()

      // write rendering data into posts
      // helps for centering and collision
      postElements.selectAll("div").each({ (node: HTMLElement, p: Post) =>
        val rect = node.getBoundingClientRect
        p.size = Vec2(rect.width, rect.height)
        p.centerOffset = p.size / -2
        p.radius = p.size.length / 2
        p.collisionRadius = p.radius
      }: js.ThisFunction)

      simulation.asInstanceOf[Simulation[SimulationNode]].force[force.Link[SimulationNode, Connects]]("connection").strength { (e: Connects) =>
        import p.fullDegree
        val targetDeg = e.target match {
          case p: Post => fullDegree(p)
          case _: Connects => 2
        }
        1.0 / min(fullDegree(e.source), targetDeg)
      }

      simulation.force[force.Link[Post, Contains]]("containment").strength { (e: Contains) =>
        import p.fullDegree
        1.0 / min(fullDegree(e.source), fullDegree(e.target))
      }

      simulation.nodes(postData)
      simulation.asInstanceOf[Simulation[SimulationNode]].force[force.Link[SimulationNode, Connects]]("connection").links(connectionData)
      simulation.force[force.Link[Post, Contains]]("containment").links(containmentData)
      simulation.alpha(1).restart()
    }

    def draw() {
      postElements.selectAll("div")
        .style("left", (p: Post) => s"${p.x.get + p.centerOffset.x}px")
        .style("top", (p: Post) => s"${p.y.get + p.centerOffset.y}px")
        .style("border", (p: Post) => if (p.isClosest) "5px solid blue" else "none")

      connectionElements.selectAll("line")
        .attr("x1", (e: Connects) => e.source.x)
        .attr("y1", (e: Connects) => e.source.y)
        .attr("x2", (e: Connects) => e.target.x)
        .attr("y2", (e: Connects) => e.target.y)

      containmentElements.selectAll("line")
        .attr("x1", (e: Contains) => e.source.x)
        .attr("y1", (e: Contains) => e.source.y)
        .attr("x2", (e: Contains) => e.target.x)
        .attr("y2", (e: Contains) => e.target.y)

      containmentHulls.selectAll("path")
        .attr("d", (cluster: ContainmentCluster) => cluster.convexHull.map(_.map(p => s"${p(0)} ${p(1)}").mkString("M", "L", "Z")).getOrElse(""))

      menuTarget.foreach { post =>
        ringMenu.attr("transform", s"translate(${post.x}, ${post.y})")
      }
    }
  }

  val backendFactory = new Backend(_)
}
