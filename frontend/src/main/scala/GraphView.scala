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

object D3Dynamic {
  //TODO: write more facade types instead of using dynamic
  val d3js = js.Dynamic.global.d3
}
import D3Dynamic.d3js

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

  var color = "red"

  var dragClosest: Option[SimPost] = None
  var isClosest = false
  var dropAngle = 0.0
  def dropIndex(n: Int) = {
    val positiveAngle = (dropAngle + 2 * Pi) % (2 * Pi)
    val stepSize = 2 * Pi / n
    val index = (positiveAngle / stepSize).toInt
    index
  }

  def newGhost = {
    val g = new SimPost(post)
    g.x = x
    g.y = y
    g.size = size
    g.centerOffset = centerOffset
    g.color = color
    g
  }
  var ghost: Option[SimPost] = None
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
  def convexHull: js.Array[js.Array[Double]] = {
    val hull = d3.polygonHull(positions)
    //TODO: how to correctly handle scalajs union type?
    if (hull == null) positions
    else hull.asInstanceOf[js.Array[js.Array[Double]]]
  }
}

object GraphView extends CustomComponent[Graph]("GraphView") {
  //TODO: dynamic by screen size, refresh on window resize, put into centering force
  val width = 640
  val height = 480

  val menuOuterRadius = 100
  val menuInnerRadius = 50
  val menuRadius = (menuOuterRadius + menuInnerRadius) / 2
  val menuThickness = menuOuterRadius - menuInnerRadius

  val dragHitDetectRadius = 200
  val postDefaultColor = d3js.lab("#f8f8f8")
  def baseHue(id: AtomId) = (id * 137) % 360
  def baseColor(id: AtomId) = d3js.hcl(baseHue(id), 50, 70)

  class Backend($: Scope) extends CustomBackend($) {
    var graph: Graph = _

    lazy val container = d3js.select(component)
    lazy val svg = container.append("svg")
    lazy val html = container.append("div")
    lazy val postElements = html.append("div")
    lazy val ghostPostElements = html.append("div")
    lazy val connectionLines = svg.append("g")
    lazy val connectionElements = html.append("div")
    lazy val containmentHulls = svg.append("g")
    lazy val menuSvg = container.append("svg")
    lazy val menuLayer = menuSvg.append("g")
    lazy val ringMenu = menuLayer.append("g")

    var postData: js.Array[SimPost] = js.Array()
    var postIdToSimPost: Map[AtomId, SimPost] = Map.empty
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
        // ("Split", { (p: SimPost) => logger.debug(s"C: $p") }) ::
        ("Del", { (p: SimPost) => Client.api.deletePost(p.id).call() }) ::
        ("Unfix", { (p: SimPost) => p.fixedPos = js.undefined; simulation.restart() }) ::
        Nil
      )
    }

    val dropActions = {
      import autowire._
      import boopickle.Default._
      (
        ("Connect", "green", { (dropped: SimPost, target: SimPost) => Client.api.connect(dropped.id, target.id).call() }) ::
        ("Contain", "blue", { (dropped: SimPost, target: SimPost) => Client.api.contain(target.id, dropped.id).call() }) ::
        Nil
      ).toArray
    }
    val dropColors = dropActions.map(_._2)

    override def init() {
      // init lazy vals to set drawing order
      container

      svg
      containmentHulls
      connectionLines

      html
      connectionElements
      postElements
      ghostPostElements

      menuSvg
      menuLayer
      ringMenu

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

      ringMenu
        .style("visibility", "hidden")

      ringMenu
        .append("circle")
        .attr("r", menuRadius)
        .attr("stroke-width", menuThickness)
        .attr("fill", "none")
        .attr("stroke", "rgba(0,0,0,0.7)")

      for (((symbol, action), i) <- menuActions.zipWithIndex) {
        val angle = i * 2 * Pi / menuActions.size
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

      //TODO: store forces in individual variables to avoid acessing them by simulation.force[Type Cast]
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

    def updateGhosts() {
      val posts = graph.posts.values
      val ghosts = posts.flatMap(p => postIdToSimPost(p.id).ghost).toJSArray
      val post = ghostPostElements.selectAll("div")
        .data(ghosts, (p: SimPost) => p.id)

      post.enter().append("div")
        .text((post: SimPost) => post.title)
        .style("opacity", "0.5")
        .style("background-color", (post: SimPost) => post.color)
        .style("padding", "3px 5px")
        .style("border-radius", "5px")
        .style("border", "1px solid #AAA")
        .style("max-width", "100px")
        .style("position", "absolute")
        .style("cursor", "move")

      post.exit().remove()
    }

    def postDragStarted(node: HTMLElement, p: SimPost) {
      val ghost = p.newGhost
      p.ghost = Some(ghost)
      updateGhosts()

      val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
      p.dragStart = eventPos
      ghost.pos = eventPos
      drawGhosts()

      simulation.stop()
    }

    def postDragged(node: HTMLElement, p: SimPost) {
      val ghost = p.ghost.get
      val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
      val transformedEventPos = p.dragStart + (eventPos - p.dragStart) / transform.k
      val closest = simulation.find(transformedEventPos.x, transformedEventPos.y, dragHitDetectRadius).toOption

      p.dragClosest.foreach(_.isClosest = false)
      closest match {
        case Some(target) if target != p =>
          val dir = ghost.pos.get - target.pos.get
          target.isClosest = true
          target.dropAngle = dir.angle
        case _ =>
      }
      p.dragClosest = closest

      ghost.pos = transformedEventPos
      drawGhosts()
      drawPosts() // for highlighting closest
    }

    def postDragEnded(node: HTMLElement, p: SimPost) {
      val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
      val transformedEventPos = p.dragStart + (eventPos - p.dragStart) / transform.k

      val closest = simulation.find(transformedEventPos.x, transformedEventPos.y, dragHitDetectRadius).toOption
      closest match {
        case Some(target) if target != p =>
          import autowire._
          import boopickle.Default._

          dropActions(target.dropIndex(dropActions.size))._3(p, target)

          target.isClosest = false
          p.fixedPos = js.undefined
        case _ =>
          p.pos = transformedEventPos
          p.fixedPos = transformedEventPos
      }

      p.ghost = None
      updateGhosts()
      drawGhosts()

      simulation.alpha(1).restart()
    }

    override def update(p: Props, oldProps: Option[Props] = None) {
      graph = p

      postData = graph.posts.values.map { p =>
        val sp = new SimPost(p)
        postIdToSimPost.get(sp.id).foreach { old =>
          // preserve position
          sp.x = old.x
          sp.y = old.y
        }
        //TODO: d3-color Facades!
        val parents = graph.parents(p.id)
        val parentColors: Seq[js.Dynamic] = parents.map((p: Post) => baseColor(p.id))
        val colors: Seq[js.Dynamic] = (if (graph.children(p.id).nonEmpty) baseColor(p.id) else postDefaultColor) +: parentColors
        val labColors = colors.map((c: js.Dynamic) => d3js.lab(c))
        val colorSum = labColors.reduce((c1, c2) => d3js.lab(c1.l + c2.l, c1.a + c2.a, c1.b + c2.b))
        val colorCount = labColors.size.asInstanceOf[js.Dynamic]
        val colorAvg = d3js.lab(colorSum.l / colorCount, colorSum.a / colorCount, colorSum.b / colorCount)
        sp.color = colorAvg.toString()
        sp
      }.toJSArray
      postIdToSimPost = (postData: js.ArrayOps[SimPost]).by(_.id)

      menuTarget = menuTarget.collect { case sp if postIdToSimPost.isDefinedAt(sp.id) => postIdToSimPost(sp.id) }

      val post = postElements.selectAll("div")
        .data(postData, (p: SimPost) => p.id)

      connectionData = graph.connections.values.map { c =>
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

      containmentData = graph.containments.values.map { c =>
        new SimContains(c, postIdToSimPost(c.parentId), postIdToSimPost(c.childId))
      }.toJSArray

      containmentClusters = {
        val parents: Seq[Post] = graph.containments.values.map(c => graph.posts(c.parentId)).toSeq.distinct
        parents.map(p => new ContainmentCluster(postIdToSimPost(p.id), graph.children(p.id).map(p => postIdToSimPost(p.id))(breakOut))).toJSArray
      }
      val containmentHull = containmentHulls.selectAll("path")
        .data(containmentClusters, (c: ContainmentCluster) => c.parent.id)

      post.exit().remove()
      post.enter().append("div")
        .text((post: SimPost) => post.title)
        .style("background-color", (post: SimPost) => post.color)
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

      containmentHull.enter().append("path")
        .style("fill", (cluster: ContainmentCluster) => cluster.parent.color)
        .style("stroke", (cluster: ContainmentCluster) => cluster.parent.color)
        .style("stroke-width", "70px")
        .style("stroke-linejoin", "round")
        .style("opacity", "0.7")
      containmentHull.exit().remove()

      postElements.selectAll("div").each({ (node: HTMLElement, p: SimPost) =>
        //TODO: if this fails, because post is not rendered yet, recalculate it lazyly
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

    def drawGhosts() {
      ghostPostElements.selectAll("div")
        .style("left", (p: SimPost) => s"${p.x.get + p.centerOffset.x}px")
        .style("top", (p: SimPost) => s"${p.y.get + p.centerOffset.y}px")
    }

    def drawPosts() {
      postElements.selectAll("div")
        .style("left", (p: SimPost) => s"${p.x.get + p.centerOffset.x}px")
        .style("top", (p: SimPost) => s"${p.y.get + p.centerOffset.y}px")
        .style("border", (p: SimPost) => if (p.isClosest) s"5px solid ${dropColors(p.dropIndex(dropActions.size))}" else "1px solid #AAA")
    }

    def draw() {
      drawPosts()

      connectionElements.selectAll("div")
        .style("left", (e: SimConnects) => s"${e.x.get}px")
        .style("top", (e: SimConnects) => s"${e.y.get}px")

      connectionLines.selectAll("line")
        .attr("x1", (e: SimConnects) => e.source.x)
        .attr("y1", (e: SimConnects) => e.source.y)
        .attr("x2", (e: SimConnects) => e.target.x)
        .attr("y2", (e: SimConnects) => e.target.y)

      containmentHulls.selectAll("path")
        .attr("d", { (cluster: ContainmentCluster) =>
          // https://codeplea.com/introduction-to-splines
          // https://github.com/d3/d3-shape#curves
          val points = cluster.convexHull
          // val curve = d3js.curveCardinalClosed
          val curve = d3js.curveCatmullRomClosed.alpha(0.5)
          // val curve = d3js.curveNatural
          d3js.line().curve(curve)(points)
        })

      menuTarget.foreach { post =>
        ringMenu.attr("transform", s"translate(${post.x}, ${post.y})")
      }
    }
  }

  val backendFactory = new Backend(_)
}
