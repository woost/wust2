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
import collection.breakOut
import math._

import org.scalajs.d3v4._
import util.collectionHelpers._

object GraphView extends CustomComponent[Graph]("GraphView") {

  //TODO: dynamic by screen size, refresh on window resize, put into centering force
  val width = 640
  val height = 480

  val menuOuterRadius = 100
  val menuInnerRadius = 50

  val dragHitDetectRadius = 200
  val postDefaultColor = d3.lab("#f8f8f8")
  def baseHue(id: AtomId) = (id * 137) % 360
  def baseColor(id: AtomId) = d3.hcl(baseHue(id), 50, 70)

  case class MenuAction(symbol: String, action: (SimPost, Simulation[SimPost]) => Unit)
  val menuActions = {
    import autowire._
    import boopickle.Default._
    (
      MenuAction("Split", { (p: SimPost, s: Simulation[SimPost]) => logger.info(s"Split: ${p.id}") }) ::
      MenuAction("Del", { (p: SimPost, s: Simulation[SimPost]) => Client.api.deletePost(p.id).call() }) ::
      MenuAction("Unfix", { (p: SimPost, s: Simulation[SimPost]) => p.fixedPos = js.undefined; s.restart() }) ::
      Nil
    )
  }

  val dropActions = {
    import autowire._
    import boopickle.Default._
    (
      ("Connect", "green", { (dropped: SimPost, target: SimPost) => Client.api.connect(dropped.id, target.id).call() }) ::
      ("Contain", "blue", { (dropped: SimPost, target: SimPost) => Client.api.contain(target.id, dropped.id).call() }) ::
      ("Merge", "red", { (dropped: SimPost, target: SimPost) => /*Client.api.merge(target.id, dropped.id).call()*/ }) ::
      Nil
    ).toArray
  }
  val dropColors = dropActions.map(_._2)

  class Backend($: Scope) extends CustomBackend($) {
    var graph: Graph = _

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

    lazy val container = d3.select(component)
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

    override def init() {
      // init lazy vals to set drawing order
      container

      svg
      containmentHulls
      connectionLines

      html
      connectionElements
      postElements
      ghostPostElements //TODO: place above ring menu?

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

      initRingMenu()

      svg.call(d3.zoom().on("zoom", zoomed _))
      svg.on("click", () => menuTarget = None)

      //TODO: store forces in individual variables to avoid acessing them by simulation.forceAs[Type Cast]
      simulation.forceAs[Centering[SimPost]]("center").x(width / 2).y(height / 2)
      simulation.forceAs[PositioningX[SimPost]]("gravityx").x(width / 2)
      simulation.forceAs[PositioningY[SimPost]]("gravityy").y(height / 2)

      simulation.forceAs[ManyBody[SimPost]]("repel").strength(-1000)
      simulation.forceAs[Collision[SimPost]]("collision").radius((p: SimPost) => p.collisionRadius)

      simulation.asInstanceOf[Simulation[SimulationNode]].forceAs[Link[SimulationNode, SimConnects]]("connection").distance(100)
      simulation.forceAs[Link[SimPost, SimContains]]("containment").distance(100)

      simulation.forceAs[PositioningX[SimPost]]("gravityx").strength(0.1)
      simulation.forceAs[PositioningY[SimPost]]("gravityy").strength(0.1)
    }

    def initRingMenu() {
      val pie = d3.pie()
        .value(1)
        .padAngle(2 * Pi / 100)

      val arc = d3.arc()
        .innerRadius(menuInnerRadius)
        .outerRadius(menuOuterRadius)
        .cornerRadius(3)

      val pieData = menuActions.toJSArray
      val ringMenuArc = ringMenu.selectAll("path")
        .data(pie(pieData))
      val ringMenuLabels = ringMenu.selectAll("text")
        .data(pie(pieData))

      ringMenuArc.enter()
        .append("path")
        .attr("d", (d: PieArcDatum[MenuAction]) => arc(d))
        .attr("fill", "rgba(0,0,0,0.7)")
        .style("cursor", "pointer")
        .style("pointer-events", "all")
        .on("click", (d: PieArcDatum[MenuAction]) => menuTarget.foreach(d.data.action(_, simulation)))

      ringMenuLabels.enter()
        .append("text")
        .text((d: PieArcDatum[MenuAction]) => d.data.symbol)
        .attr("text-anchor", "middle")
        .attr("fill", "white")
        .attr("x", (d: PieArcDatum[MenuAction]) => arc.centroid(d)(0))
        .attr("y", (d: PieArcDatum[MenuAction]) => arc.centroid(d)(1))
    }

    override def update(p: Props, oldProps: Option[Props] = None) {
      logger.info(s"update: " + oldProps.map(_ != p).getOrElse(true))
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
        val parentColors: Seq[Hcl] = parents.map((p: Post) => baseColor(p.id))
        val colors: Seq[Color] = (if (graph.children(p.id).nonEmpty) baseColor(p.id) else postDefaultColor) +: parentColors
        val labColors = colors.map(d3.lab(_))
        val colorSum = labColors.reduce((c1, c2) => d3.lab(c1.l + c2.l, c1.a + c2.a, c1.b + c2.b))
        val colorCount = labColors.size
        val colorAvg = d3.lab(colorSum.l / colorCount, colorSum.a / colorCount, colorSum.b / colorCount)
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
        .on("click", { (p: SimPost) =>
          //TODO: click should not trigger drag
          if (menuTarget.isEmpty || menuTarget.get != p)
            menuTarget = Some(p)
          else
            menuTarget = None
          draw()
        })
        .call(d3.drag[SimPost]()
          .on("start", postDragStarted _)
          .on("drag", postDragged _)
          .on("end", postDragEnded _))

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
      })

      simulation.asInstanceOf[Simulation[SimulationNode]].forceAs[Link[SimulationNode, SimConnects]]("connection").strength { (e: SimConnects) =>
        import p.fullDegree
        val targetDeg = e.target match {
          case p: SimPost => fullDegree(p.post)
          case _: SimConnects => 2
        }
        1.0 / min(fullDegree(e.source.post), targetDeg)
      }

      simulation.forceAs[Link[SimPost, SimContains]]("containment").strength { (e: SimContains) =>
        import p.fullDegree
        1.0 / min(fullDegree(e.source.post), fullDegree(e.target.post))
      }

      simulation.nodes(postData)
      simulation.asInstanceOf[Simulation[SimulationNode]].forceAs[Link[SimulationNode, SimConnects]]("connection").links(connectionData)
      simulation.forceAs[Link[SimPost, SimContains]]("containment").links(containmentData)
      simulation.alpha(1).restart()
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

    def zoomed() {
      transform = d3.event.asInstanceOf[ZoomEvent].transform
      svg.selectAll("g").attr("transform", transform.toString)
      html.style("transform", s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})")
      menuLayer.attr("transform", transform.toString)
    }

    def postDragStarted(p: SimPost) {
      val ghost = p.newGhost
      p.ghost = Some(ghost)
      updateGhosts()

      val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
      p.dragStart = eventPos
      ghost.pos = eventPos
      drawGhosts()

      simulation.stop()
    }

    def postDragged(p: SimPost) {
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

    def postDragEnded(p: SimPost) {
      logger.info("postDragEnded")
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

    def draw() {
      drawPosts()
      drawRelations()
      drawPostMenu()
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

    def drawRelations() {
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
          // val curve = d3.curveCardinalClosed
          val curve = d3.curveCatmullRomClosed.alpha(0.5)
          // val curve = d3.curveNatural

          d3.line().curve(curve)(points)
        })
    }

    def drawPostMenu() {
      menuTarget.foreach { post =>
        ringMenu.attr("transform", s"translate(${post.x}, ${post.y})")
      }
    }

  }

  val backendFactory = new Backend(_)
}
