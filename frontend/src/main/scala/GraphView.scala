package frontend

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
import collection.breakOut
import math._

import org.scalajs.d3v4._
import org.scalajs.d3v4.force._
import org.scalajs.d3v4.force.{SimulationNode => D3Node, SimulationLink => D3Link, SimulationNodeImpl => D3NodeImpl, SimulationLinkImpl => D3LinkImpl}
import org.scalajs.d3v4.zoom._
import org.scalajs.d3v4.selection._
import org.scalajs.d3v4.polygon._
import org.scalajs.d3v4.drag._
import util.collectionHelpers._

trait ExtendedD3Node extends D3Node {
  def pos = for (x <- x; y <- y) yield Vec2(x, y)
  def pos_=(newPos: js.UndefOr[Vec2]) {
    if (newPos.isDefined) {
      x = newPos.get.x
      y = newPos.get.y
    } else {
      x = js.undefined
      y = js.undefined
    }
  }
  def vel = for (vx <- vx; vy <- vy) yield Vec2(vx, vy)
  def vel_=(newVel: js.UndefOr[Vec2]) {
    if (newVel.isDefined) {
      vx = newVel.get.x
      vy = newVel.get.y
    } else {
      vx = js.undefined
      vy = js.undefined
    }
  }
  def fixedPos = for (fx <- fx; fy <- fy) yield Vec2(fx, fy)
  def fixedPos_=(newFixedPos: js.UndefOr[Vec2]) {
    if (newFixedPos.isDefined) {
      fx = newFixedPos.get.x
      fy = newFixedPos.get.y
    } else {
      fx = js.undefined
      fy = js.undefined
    }
  }

  var size: Vec2 = Vec2(0, 0)
  def rect = pos.map { pos => AARect(pos, size) }
  var centerOffset: Vec2 = Vec2(0, 0)
  var radius: Double = 0
  var collisionRadius: Double = 0

  var dragStart = Vec2(0, 0)
}

class SimPost(val post: Post) extends ExtendedD3Node with SimulationNodeImpl {
  //TODO: delegert!
  def id = post.id
  def title = post.title

  var dragClosest: Option[SimPost] = None
  var isClosest = false
}

class SimConnects(val connects: Connects, val source: SimPost) extends D3Link[SimPost, ExtendedD3Node] with ExtendedD3Node with D3LinkImpl[SimPost, ExtendedD3Node] {
  //TODO: delegert!
  def id = connects.id
  def sourceId = connects.sourceId
  def targetId = connects.targetId

  // this is necessary because target can be a SimConnects itself
  var target: ExtendedD3Node = _

  // propagate d3 gets/sets to incident posts
  def x = for (sx <- source.x; tx <- target.x) yield (sx + tx) / 2
  def x_=(newX: js.UndefOr[Double]) {
    val diff = for (x <- x; newX <- newX) yield (newX - x) / 2
    source.x = for (x <- source.x; diff <- diff) yield x + diff
    target.x = for (x <- target.x; diff <- diff) yield x + diff
  }
  def y = for (sy <- source.y; ty <- target.y) yield (sy + ty) / 2
  def y_=(newY: js.UndefOr[Double]) {
    val diff = for (y <- y; newY <- newY) yield (newY - y) / 2
    source.y = for (y <- source.y; diff <- diff) yield y + diff
    target.y = for (y <- target.y; diff <- diff) yield y + diff
  }
  def vx = for (svx <- source.vx; tvx <- target.vx) yield (svx + tvx) / 2
  def vx_=(newVX: js.UndefOr[Double]) {
    val diff = for (vx <- vx; newVX <- newVX) yield (newVX - vx) / 2
    source.vx = for (vx <- source.vx; diff <- diff) yield vx + diff
    target.vx = for (vx <- target.vx; diff <- diff) yield vx + diff
  }
  def vy = for (svy <- source.vy; tvy <- target.vy) yield (svy + tvy) / 2
  def vy_=(newVY: js.UndefOr[Double]) {
    val diff = for (vy <- vy; newVY <- newVY) yield (newVY - vy) / 2
    source.vy = for (vy <- source.vy; diff <- diff) yield vy + diff
    target.vy = for (vy <- target.vy; diff <- diff) yield vy + diff
  }
  def fx: js.UndefOr[Double] = ???
  def fx_=(newFX: js.UndefOr[Double]): Unit = ???
  def fy: js.UndefOr[Double] = ???
  def fy_=(newFX: js.UndefOr[Double]): Unit = ???
}

class SimContains(val contains: Contains, val parent: SimPost, val child: SimPost) extends D3LinkImpl[SimPost, SimPost] {
  //TODO: delegert!
  def id = contains.id
  def parentId = contains.parentId
  def childId = contains.childId

  def source = parent
  def target = child
}

class ContainmentCluster(val parent: SimPost, val children: IndexedSeq[SimPost]) {
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
    lazy val connectionLines = svg.append("g")
    lazy val connectionElements = html.append("div")
    lazy val containmentElements = svg.append("g")
    lazy val containmentHulls = svg.append("g")
    lazy val menuSvg = container.append("svg")
    lazy val menuLayer = menuSvg.append("g")
    lazy val ringMenu = menuLayer.append("g")

    var postData: js.Array[SimPost] = js.Array()
    var connectionData: js.Array[SimConnects] = js.Array()
    var containmentData: js.Array[SimContains] = js.Array()
    var containmentClusters: js.Array[ContainmentCluster] = js.Array()

    var _menuTarget: Option[SimPost] = None
    def menuTarget = _menuTarget
    def menuTarget_=(target: Option[SimPost]) {
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

    val simulation = d3.forceSimulation[SimPost]()
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
        ("Split", { (p: SimPost) => logger.debug(s"C: $p") }) ::
        ("Del", { (p: SimPost) => Client.api.deletePost(p.id).call() }) ::
        ("Unfix", { (p: SimPost) => p.fixedPos = js.undefined; simulation.restart() }) ::
        Nil
      )
    }

    override def init() {
      // init lazy vals to set drawing order
      container

      svg
      containmentHulls
      containmentElements
      connectionLines

      html
      connectionElements
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

      //TODO: type cast necessary? we only put constants in here...
      simulation.force[Centering[SimPost]]("center").x(width / 2).y(height / 2)
      simulation.force[PositioningX[SimPost]]("gravityx").x(width / 2)
      simulation.force[PositioningY[SimPost]]("gravityy").y(height / 2)

      simulation.force[ManyBody[SimPost]]("repel").strength(-1000)
      simulation.force[Collision[SimPost]]("collision").radius((p: SimPost) => p.collisionRadius)

      simulation.asInstanceOf[Simulation[SimulationNode]].force[force.Link[SimulationNode, SimConnects]]("connection").distance(100)
      simulation.force[force.Link[SimPost, SimContains]]("containment").distance(100)

      simulation.force[PositioningX[SimPost]]("gravityx").strength(0.1)
      simulation.force[PositioningY[SimPost]]("gravityy").strength(0.1)
    }

    def zoomed() {
      transform = d3.event.asInstanceOf[ZoomEvent].transform
      svg.selectAll("g").attr("transform", transform)
      html.style("transform", s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})")
      menuLayer.attr("transform", transform)
    }

    def postDragStarted(node: HTMLElement, p: SimPost) {
      val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
      p.dragStart = eventPos
      p.fixedPos = eventPos
      p.pos = eventPos

      d3js.select(node).style("cursor", "move")
      simulation.stop()
    }

    def postDragged(node: HTMLElement, p: SimPost) {
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

    def postDragEnded(node: HTMLElement, p: SimPost) {
      val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
      p.pos = p.dragStart // prevents finding the dragged post as closest post
      val closest = simulation.find(eventPos.x, eventPos.y, dragHitRadius).toOption
      closest match {
        case Some(target) if target != p =>
          import autowire._
          import boopickle.Default._

          Client.api.connect(p.id, target.id).call()
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

      postData = p.posts.values.map(new SimPost(_)).toJSArray
      val postIdToSimPost: Map[AtomId, SimPost] = (postData: js.ArrayOps[SimPost]).by(_.id)
      val post = postElements.selectAll("div")
        .data(postData, (p: SimPost) => p.id)

      connectionData = p.connections.values.map { c =>
        new SimConnects(c, postIdToSimPost(c.sourceId))
      }.toJSArray
      val connIdToSimConnects: Map[AtomId, SimConnects] = (connectionData: js.ArrayOps[SimConnects]).by(_.id)
      connectionData.foreach { e =>
        e.target = postIdToSimPost.getOrElse(e.targetId, connIdToSimConnects(e.targetId))
      }
      val connectionLine = connectionLines.selectAll("line")
        .data(connectionData, (r: SimConnects) => r.id)
      val connectionElement = connectionElements.selectAll("div")
        .data(connectionData, (r: SimConnects) => r.id)

      containmentData = p.containments.values.map { c =>
        new SimContains(c, postIdToSimPost(c.parentId), postIdToSimPost(c.childId))
      }.toJSArray
      val contains = containmentElements.selectAll("line")
        .data(containmentData, (r: SimContains) => r.id)

      containmentClusters = {
        val parents: Seq[Post] = containments.values.map(c => posts(c.parentId)).toSeq.distinct
        parents.map(p => new ContainmentCluster(postIdToSimPost(p.id), graph.children(p).map(p => postIdToSimPost(p.id))(breakOut))).toJSArray
      }
      val containmentHull = containmentHulls.selectAll("path")
        .data(containmentClusters, (c: ContainmentCluster) => c.parent.id)

      post.enter().append("div")
        .text((post: SimPost) => post.title)
        .style("background-color", "#f8f8f8")
        .style("padding", "3px 5px")
        .style("border-radius", "5px")
        .style("border", "1px solid #AAA")
        .style("max-width", "100px")
        .style("position", "absolute")
        .style("cursor", "default")
        .style("pointer-events", "auto") // reenable
        .call(d3js.drag()
          .on("start", postDragStarted _: js.ThisFunction)
          .on("drag", postDragged _: js.ThisFunction)
          .on("end", postDragEnded _: js.ThisFunction))
        .on("click", { (p: SimPost) =>
          if (menuTarget.isEmpty || menuTarget.get != p)
            menuTarget = Some(p)
          else
            menuTarget = None

          draw()
        })
      post.exit().remove()

      connectionLine.enter().append("line")
        .style("stroke", "#8F8F8F")
      connectionLine.exit().remove()

      connectionElement.enter().append("div")
        .style("position", "absolute")
        .style("font-size", "20px")
        .style("margin-left", "-0.5ex")
        .style("margin-top", "-0.5em")
        .text("\u00d7")
        .style("pointer-events", "auto") // reenable
        .style("cursor", "pointer")
        .on("click", { (e: SimConnects) =>
          logger.debug("delete edge")
          import autowire._
          import boopickle.Default._

          Client.api.deleteConnection(e.id).call()
        })
      connectionElement.exit().remove()

      contains.enter().append("line")
        .style("stroke", "blue")
      contains.exit().remove()

      containmentHull.enter().append("path")
        .style("stroke", "#0075B8")
        .style("fill", "#00C1FF")
      containmentHull.exit().remove()

      postElements.selectAll("div").each({ (node: HTMLElement, p: SimPost) =>
        val rect = node.getBoundingClientRect
        p.size = Vec2(rect.width, rect.height)
        p.centerOffset = p.size / -2
        p.radius = p.size.length / 2
        p.collisionRadius = p.radius
      }: js.ThisFunction)

      simulation.asInstanceOf[Simulation[SimulationNode]].force[force.Link[SimulationNode, SimConnects]]("connection").strength { (e: SimConnects) =>
        import p.fullDegree
        val targetDeg = e.target match {
          case p: SimPost => fullDegree(p.post)
          case _: SimConnects => 2
        }
        1.0 / min(fullDegree(e.source.post), targetDeg)
      }

      simulation.force[force.Link[SimPost, SimContains]]("containment").strength { (e: SimContains) =>
        import p.fullDegree
        1.0 / min(fullDegree(e.source.post), fullDegree(e.target.post))
      }

      simulation.nodes(postData)
      simulation.asInstanceOf[Simulation[SimulationNode]].force[force.Link[SimulationNode, SimConnects]]("connection").links(connectionData)
      simulation.force[force.Link[SimPost, SimContains]]("containment").links(containmentData)
      simulation.alpha(1).restart()
    }

    def draw() {
      postElements.selectAll("div")
        .style("left", (p: SimPost) => s"${p.x.get + p.centerOffset.x}px")
        .style("top", (p: SimPost) => s"${p.y.get + p.centerOffset.y}px")
        .style("border", (p: SimPost) => if (p.isClosest) "5px solid blue" else "1px solid #AAA")

      connectionElements.selectAll("div")
        .style("left", (e: SimConnects) => s"${e.x.get}px")
        .style("top", (e: SimConnects) => s"${e.y.get}px")

      connectionLines.selectAll("line")
        .attr("x1", (e: SimConnects) => e.source.x)
        .attr("y1", (e: SimConnects) => e.source.y)
        .attr("x2", (e: SimConnects) => e.target.x)
        .attr("y2", (e: SimConnects) => e.target.y)

      containmentElements.selectAll("line")
        .attr("x1", (e: SimContains) => e.source.x)
        .attr("y1", (e: SimContains) => e.source.y)
        .attr("x2", (e: SimContains) => e.target.x)
        .attr("y2", (e: SimContains) => e.target.y)

      containmentHulls.selectAll("path")
        .attr("d", (cluster: ContainmentCluster) => cluster.convexHull.map(_.map(p => s"${p(0)} ${p(1)}").mkString("M", "L", "Z")).getOrElse(""))

      menuTarget.foreach { post =>
        ringMenu.attr("transform", s"translate(${post.x}, ${post.y})")
      }
    }
  }

  val backendFactory = new Backend(_)
}
