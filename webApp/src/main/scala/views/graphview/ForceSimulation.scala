package views.graphview

import d3v4._
import wust.webApp.BrowserDetect
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.{CanvasRenderingContext2D, html}
import outwatch.dom._
import outwatch.dom.dsl.events
import rx._
import vectory.Vec2
import views.graphview.VisualizationType.{Containment, Edge}
import wust.graph._
import wust.ids.{EdgeData, NodeId}
import wust.sdk.NodeColor._
import wust.util.time.time
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._

import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

case class PlaneDimension(
    xOffset: Double = 50,
    yOffset: Double = 50,
    simWidth: Double = 300,
    simHeight: Double = 300,
    width: Double = 300,
    height: Double = 300
)

sealed trait VisualizationType
object VisualizationType {
  case object Edge extends VisualizationType
  case object Containment extends VisualizationType
  case object Tag extends VisualizationType
}

object ForceSimulationConstants {
  val nodePadding = 10
  val eulerSetPadding = nodePadding
  val minimumDragHighlightRadius = 50
  val nodeSpacing = 20
}

class ForceSimulation(
    state: GlobalState,
    onDrop: (NodeId, NodeId) => Unit,
    onDropWithCtrl: (NodeId, NodeId) => Unit
)(implicit ctx: Ctx.Owner) {
  //TODO: sometimes dragging parent into child crashes simulation
  import ForceSimulation._
  import ForceSimulationConstants._

  val postCreationMenus: Var[List[Vec2]] = Var(Nil)
  val selectedNodeId: Var[Option[(Vec2, NodeId)]] = Var(None)

  //TODO why partial?
  private var labelVisualization: PartialFunction[EdgeData.Type, VisualizationType] = {
    case EdgeData.Parent.tpe => Containment
    case _:EdgeData.Label    => Edge
  }
  private var postSelection: Selection[Node] = _
  private var simData: SimulationData = _
  private var staticData: StaticData = _
  private var planeDimension = PlaneDimension()
  private var canvasContext: CanvasRenderingContext2D = _
  var transform: Transform = d3.zoomIdentity
  var running = false
  //  val positionRequests = mutable.HashMap.empty[NodeId, (Double, Double)]

  private val backgroundElement = Promise[dom.html.Element]
  private val canvasLayerElement = Promise[dom.html.Canvas]
  private val postContainerElement = Promise[dom.html.Element]

  var isCtrlPressed = false
  
  if (!BrowserDetect.isMobile)
    keyDown(KeyCode.Ctrl).foreach { isCtrlPressed = _ }

  val component: VNode = {
    import outwatch.dom.dsl._
    import outwatch.dom.dsl.styles.extra._

    div(
      onDomMount.asHtml foreach { e =>
        backgroundElement.success(e)
      },
      position := "relative",
      width := "100%",
      height := "100%",
      overflow := "hidden",
      // Mouse events from all children pass through to backgroundElement (e.g. zoom).
      canvas(
        position := "absolute",
        onDomMount.map(_.asInstanceOf[dom.html.Canvas]) foreach { (e) =>
          canvasLayerElement.success(e)
        },
        // pointerEvents := "none" // background handles mouse events
      ),
      div(
        onDomMount.asHtml foreach { e =>
          postContainerElement.success(e); ()
        },
        width := "100%",
        height := "100%",
        position := "absolute",
        // pointerEvents := "none", // background handles mouse events
        transformOrigin := "top left" // same as svg/canvas default
      )
    )
  }

  for {
    backgroundElement <- backgroundElement.future
    canvasLayerElement <- canvasLayerElement.future
    postContainerElement <- postContainerElement.future
  } {
    println(log("-------------------- init simulation"))
    val background = d3.select(backgroundElement)
    val canvasLayer = d3.select(canvasLayerElement)
    val postContainer = d3.select(postContainerElement)
    canvasContext = canvasLayerElement.getContext("2d").asInstanceOf[CanvasRenderingContext2D]

    val graphRx: Rx[Graph] = Rx {
      //val rawGraph = state.rawGraph().consistent
      println(log("\n") + log(s"---- graph update[${state.graph().nodes.length}] ----"))
      time(log("graph to wrapper arrays")) {
        state.graph().pageContent(state.page())
      }
    }

    // will be called, when user zoomed the view
    def zoomed(): Unit = {
      transform = d3.event.transform // since zoomed is called via d3 event, transform was set by d3
      // println(log(s"zoomed: ${transform.k}"))
      canvasContext.setTransform(transform.k, 0, 0, transform.k, transform.x, transform.y) // set transformation matrix (https://developer.mozilla.org/de/docs/Web/API/CanvasRenderingContext2D/setTransform)
      postContainer.style(
        "transform",
        s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})"
      )
      drawCanvas(simData, staticData, canvasContext, planeDimension)
      if (debugDrawEnabled) calculateAndDrawCurrentVelocities()
    }

    // Drag & Zoom example: https://bl.ocks.org/mbostock/3127661b6f13f9316be745e77fdfb084
    val zoom = d3
      .zoom()
      .scaleExtent(js.Array(0.01, 10))
      .on("zoom", () => zoomed())
      .clickDistance(10) // interpret short drags as clicks

    background
      .call(zoom) // mouse events only get catched in background layer, then trigger zoom events, which in turn trigger zoomed()
      .on(
        "click", { () =>
          println("clicked background")

          // if visualization was broken, fix it
          if (transform.k.isNaN) { // happens, when background size = 0, which happens when rendered invisibly
            // fixes visualization
            resized()
            startAnimated()
          } else {
            if (postCreationMenus.now.isEmpty && selectedNodeId.now.isEmpty) {
              val pos = transform.invert(d3.mouse(background.node))
              postCreationMenus() = List(Vec2(pos(0), pos(1)))
            } else {
              // TODO:
              // Var.set(
              //   Var.Assignment(postCreationMenus, Nil),
              //   Var.Assignment(selectedNodeId, None)
              // )
              postCreationMenus() = Nil
              selectedNodeId() = None
            }
          }
        }
      )

    events.window.onResize.foreach { _ =>
      // TODO: detect element resize instead: https://www.npmjs.com/package/element-resize-detector
      resized()
      startAnimated()
    }

    def dragSubject(d: Node, i: Int): Coordinates = {
      new Coordinates(
        x = simData.x(i) * transform.k,
        y = simData.y(i) * transform.k
      )
    }

    def dragStart(n: html.Element, d: Node, dragging: Int): Unit = {
      running = false
      ForceSimulationForces.initQuadtree(simData, staticData)
      simData.quadtree.remove(dragging)
    }

    def hit(dragging: Int, minRadius: Double): Option[Int] = {
      if (simData.n <= 1) return None

      val x = d3.event.x / transform.k
      val y = d3.event.y / transform.k

      val target = simData.quadtree.find(x, y) // ,staticData.maxRadius
      def distance = Vec2.length(x - simData.x(target), y - simData.y(target))
      if (target != dragging && (distance <= minRadius || distance <= staticData.radius(target))) {
        Some(target)
      } else None
    }

    def dragging(n: html.Element, d: Node, dragging: Int): Unit = {
      running = false
      val x = d3.event.x / transform.k
      val y = d3.event.y / transform.k

      d3.select(n)
        .style("transform", {
          val xOff = x + staticData.centerOffsetX(dragging)
          val yOff = y + staticData.centerOffsetY(dragging)
          s"translate(${xOff}px,${yOff}px)"
        })

      simData.x(dragging) = x
      simData.y(dragging) = y

      ForceSimulationForces.calculateEulerSetPolygons(simData, staticData)
      ForceSimulationForces.eulerSetGeometricCenter(simData, staticData)
      drawCanvas(simData, staticData, canvasContext, planeDimension)

      hit(dragging, minimumDragHighlightRadius).foreach { target =>
        canvasContext.lineWidth = 1

        val bgColor = d3.lab(eulerBgColor(staticData.posts(target).id).toHex) //TODO: use d3.rgb or make colorado handle opacity
        val radius = (staticData.radius(target) + eulerSetPadding) max minimumDragHighlightRadius
        bgColor.opacity = 0.8
        canvasContext.fillStyle = bgColor
        canvasContext.beginPath()
        canvasContext.arc(
          simData.x(target),
          simData.y(target),
          radius,
          startAngle = 0,
          endAngle = 2 * Math.PI
        )
        canvasContext.fill()
        canvasContext.closePath()
      }

      ForceSimulationForces.clearVelocities(simData)
      simData.alpha = 1.0
      if (debugDrawEnabled) calculateAndDrawCurrentVelocities()
    }

    def dropped(n: html.Element, d: Node, dragging: Int): Unit = {
      hit(dragging, minimumDragHighlightRadius).foreach { target =>
        if (isCtrlPressed)
          onDropWithCtrl(staticData.posts(dragging).id, staticData.posts(target).id)
        else
          onDrop(staticData.posts(dragging).id, staticData.posts(target).id)
      }
      //TODO: if nothing was changed, jump back to drag start with animation
    }

    def onClick(node: Node, i: Int): Unit = {

      println(s"clicked node[$i]")
      d3.event.stopPropagation() // prevent click from bubbling to background

      //TODO:
      //   // Var.set(
      //   //   VarTuple(rxFocusedSimPost, rxFocusedSimPost.now.map(_.id).setOrToggle(p.id)),
      //     //   VarTuple(graphState.state.postCreatorMenus, Nil)
      //   // )
      val pos = Vec2(simData.x(i), simData.y(i))
      selectedNodeId() = Some((pos, node.id))
      postCreationMenus() = Nil
    }

    // should be called when the size of the visualization changed
    def resized(): Unit = {
      val rect = backgroundElement.getBoundingClientRect()
      import rect.{height, width}
      val resizedFromZero = (planeDimension.width == 0 || planeDimension.height == 0) && width > 0 && height > 0
      if (resizedFromZero) { // happens when graphview was rendered in a hidden element
        // since postContainer had size zero, all posts also had size zero,
        // so we have to resize postContainer and then reinitialize the node sizes in static data
        transform = d3.zoomIdentity
        postContainer.style(
          "transform",
          s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})"
        )
        staticData = StaticData(graphRx.now, postSelection, transform, labelVisualization)
      }

      val arbitraryFactor = 1.3
      // TODO: handle cases:
      // - long window with big blob in the middle
      val scale = Math.sqrt((width * height) / (staticData.reservedArea * arbitraryFactor)) min 1.5 // scale = sqrt(ratio) because areas grow quadratically
//      println(log(s"resized: $width x $height, fromZero: $resizedFromZero, scale: $scale"))
      planeDimension = PlaneDimension(
        xOffset = -width / 2 / scale,
        yOffset = -height / 2 / scale,
        simWidth = width / scale,
        simHeight = height / scale,
        width = width,
        height = height
      )

      canvasLayer
        .attr("width", width)
        .attr("height", height)

      // this triggers zoomed()
      background.call(
        zoom.transform _,
        d3.zoomIdentity
          .translate(width / 2, height / 2)
          .scale(scale)
      )
    }

    //  val t = d3.transition().duration(750) TODO
    // whenever the graph changes
    graphRx.foreach { graph =>

      // The set of posts has changed,
      // we have to update the indices of the simulation data arrays

      println(
        log(
          s"updating simulation[${Option(simData).fold("_")(_.n.toString)} -> ${graph.nodes.length}]..."
        )
      )
      stop()

      // We want to let d3 do the re-ordering while keeping the old coordinates
      postSelection = postContainer.selectAll[Node]("div.graphnode")
      // First, we write x,y,vx,vy into the dom
      backupSimDataToDom(simData, postSelection)
      // The CoordinateWrappers are stored in dom and reordered by d3
      updateDomNodes(graph.nodes.toJSArray, postSelection, onClick) // d3 data join
      postSelection = postContainer.selectAll[Node]("div.graphnode") // update outdated postSelection
      registerDragHandlers(postSelection, dragSubject, dragStart, dragging, dropped)
      // afterwards we write the data back to our new arrays in simData
      simData = createSimDataFromDomBackup(postSelection)
      // For each node, we calculate its rendered size, radius etc.
      staticData = StaticData(graph, postSelection, transform, labelVisualization)
      resized() // adjust zoom to possibly changed accumulated node area
      ForceSimulationForces.nanToPhyllotaxis(simData, spacing = 20) // set initial positions for new nodes

      println(log(s"Simulation and Post Data initialized. [${simData.n}]"))
      startAnimated() // this also triggers the initial simulation start
    }
  }

  def startHidden(): Unit = {
    println(log("started"))
    simData.alpha = 1
    if (!running) {
      running = true
    }
    while (running) {
      running = simStep()
    }
    draw()
  }

  def startAnimated(alpha: Double = 1, alphaMin: Double = 0.7): Unit = {
    println(log("started"))

    val ticks = 100 // Default = 300
    val forceFactor = 0.4
    simData.alpha = alpha
    simData.alphaMin = alphaMin // stop simulation earlier (default = 0.001)
    simData.alphaDecay = 1 - Math.pow(alphaMin, 1.0 / ticks)
    simData.velocityDecay = 1 - forceFactor // (1 - velocityDecay) is multiplied before the velocities get applied to the positions https://github.com/d3/d3-force/issues/100
    if (!running) {
      running = true
      animationStep(0)
    }
  }

  def resumeAnimated(): Unit = {
    println(log("resumed"))
    if (!running) {
      running = true
      animationStep(0)
    }
  }

  def stop(): Unit = {
    println(log("stopped"))
    running = false
  }

  def animationStep(timestamp: Double): Unit = {
    if (!simStep()) { // nothing happened, alpha surpassed threshold
      return // so no need to draw. stop animation
    }
    draw()
    dom.window.requestAnimationFrame(animationStep)
  }

  private def simStep(): Boolean = {
    if (!running) return false
    if (!simulationStep(simData, staticData, planeDimension)) {
      // nothing happened, alpha surpassed threshold
      stop()
      return false
    }
    true
  }

  def calculateAndDrawCurrentVelocities(): Unit = {
    val futureSimData = simData.clone()
    calculateVelocities(futureSimData, staticData, planeDimension)
    drawVelocities(simData, futureSimData, staticData, canvasContext, planeDimension)
  }

  def step(alpha: Double = 1.0): Unit = {
    simData.alpha = alpha
    simulationStep(simData, staticData, planeDimension)
    draw()
    if (debugDrawEnabled) calculateAndDrawCurrentVelocities()
  }

  def draw(): Unit = {
    ForceSimulationForces.calculateEulerSetPolygons(simData, staticData) // TODO: separate display polygon from collision polygon?
    applyNodePositions(simData, staticData, postSelection)
    drawCanvas(simData, staticData, canvasContext, planeDimension)
  }
}

object ForceSimulation {
  private val debugDrawEnabled = true
  import ForceSimulationConstants._
  @inline def log(msg: String) = s"ForceSimulation: $msg"

  def calcPostWidth(node: Node) = {
    import outwatch.dom.dsl._
    val arbitraryFactor = 2.4
    val contentWidth = node.data.str.length // TODO: wrong with markdown rendering
    val calcWidth = if (contentWidth > 10) {
      val sqrtWidth = (math.sqrt(contentWidth) * arbitraryFactor) min 60
      Some(width := s"${sqrtWidth}ch")
    } else None
    calcWidth
  }

  def updateDomNodes(
      posts: js.Array[Node],
      postSelection: Selection[Node],
      onClick: (Node, Int) => Unit
  ): Unit = {
    // This is updating the dom using a D3 data join. (https://bost.ocks.org/mike/join)
    val node = postSelection.data(posts, (p: Node) => p.id.toString)
    time(log(s"removing old posts from dom[${node.exit().size()}]")) {
      node
        .exit()
        .remove()
    }

    time(log(s"updating staying posts[${node.size()}]")) {
      node
        .html((node: Node) => htmlNodeData(node.data))
        .style("width", (node: Node) => calcPostWidth(node).getOrElse(js.undefined))
        .on("click", onClick) //TODO: does d3 provide a wrong index?
    }

    time(log(s"adding new posts to dom[${node.enter().size()}]")) {
      node
        .enter()
        .append((node: Node) => {
          import outwatch.dom.dsl._
          // TODO: is outwatch rendering slow here? Should we use d3 instead?
          val postWidth = calcPostWidth(node)
          div(
            postWidth,
            renderNodeData(node.data),
            cls := "graphnode",
            // pointerEvents.auto, // re-enable mouse events
            cursor.default
          ).render
        })
        .on("click", onClick)
    }
  }

  def registerDragHandlers(
      postSelection: Selection[Node],
      dragSubject: (Node, Index) => Coordinates,
      dragStart: (html.Element, Node, Index) => Unit,
      dragged: (html.Element, Node, Index) => Unit,
      dropped: (html.Element, Node, Index) => Unit
  ): Unit = {
    postSelection.call(
      d3.drag[Node]()
        .clickDistance(10) // interpret short drags as clicks
        .subject(dragSubject) // important for drag offset
        .on("start", dragStart)
        .on("drag", dragged)
        .on("end", dropped)
    )
  }

  def backupSimDataToDom(simData: SimulationData, postSelection: Selection[Node]): Unit = {
    time(log(s">> backupData[${if (simData != null) simData.n.toString else "None"}]")) {
      postSelection.each[html.Element] { (node: html.Element, _: Node, i: Int) =>
        val coordinates = new Coordinates
        node.asInstanceOf[js.Dynamic].__databackup__ = coordinates // yay javascript!
        coordinates.x = simData.x(i)
        coordinates.y = simData.y(i)
        coordinates.vx = simData.vx(i)
        coordinates.vy = simData.vy(i)
      }
    }
  }

  def createSimDataFromDomBackup(postSelection: Selection[Node]): SimulationData = {
    time(log(s"<< createSimDataFromBackup[${postSelection.size()}]")) {
      val n = postSelection.size()
      val simData = new SimulationData(n)
      postSelection.each[html.Element] { (node: html.Element, _: Node, i: Int) =>
        if (node
              .asInstanceOf[js.Dynamic]
              .__databackup__
              .asInstanceOf[js.UndefOr[Coordinates]] != js.undefined) {
          val coordinates = node.asInstanceOf[js.Dynamic].__databackup__.asInstanceOf[Coordinates]
          simData.x(i) = coordinates.x
          simData.y(i) = coordinates.y
          simData.vx(i) = coordinates.vx
          simData.vy(i) = coordinates.vy
        }
      }
      simData
    }
  }

  def alphaStep(simData: SimulationData): Boolean = {
    import simData._
    if (alpha < alphaMin) return false
    alpha += (alphaTarget - alpha) * alphaDecay
    //    println(log("step: " + alpha))
    true
  }

  def simulationStep(
      simData: SimulationData,
      staticData: StaticData,
      planeDimension: PlaneDimension
  ): Boolean = {
    import ForceSimulationForces._

    /*time("simulation step")*/
    {
      val stepped = alphaStep(simData)
      if (!stepped) {
        return stepped
      }

      calculateVelocities(simData, staticData, planeDimension)
      applyVelocities(simData)
    }

    true
  }

  def calculateVelocities(
      simData: SimulationData,
      staticData: StaticData,
      planeDimension: PlaneDimension
  ): Unit = {
    import ForceSimulationForces._

    //    console.log(staticData.asInstanceOf[js.Any])
    initQuadtree(simData, staticData)
    eulerSetGeometricCenter(simData, staticData)
    calculateEulerSetPolygons(simData, staticData)

    rectBound(simData, staticData, planeDimension, strength = 0.1)
    keepDistance(simData, staticData, distance = nodeSpacing, strength = 0.2)
//    edgeLength(simData, staticData)

    eulerSetClustering(simData, staticData, strength = 0.1)
    // pushOutOfWrongEulerSet(simData,staticData)
  }

  def applyNodePositions(
      simData: SimulationData,
      staticData: StaticData,
      postSelection: Selection[Node]
  ): Unit = {
    postSelection
      .style("transform", { (_: Node, i: Int) =>
        val x = simData.x(i) + staticData.centerOffsetX(i)
        val y = simData.y(i) + staticData.centerOffsetY(i)
        s"translate(${x}px,${y}px)"
      })
  }

  def drawCanvas(
      simData: SimulationData,
      staticData: StaticData,
      canvasContext: CanvasRenderingContext2D,
      planeDimension: PlaneDimension
  ): Unit = {
    val edgeCount = staticData.edgeCount
    val containmentCount = staticData.containmentCount
    val eulerSetCount = simData.eulerSetPolygons.length
    val nodeCount = simData.n
    val fullCircle = 2 * Math.PI

    // clear entire canvas
    canvasContext.save()
    canvasContext.setTransform(1, 0, 0, 1, 0,
      0) // identity (https://developer.mozilla.org/de/docs/Web/API/CanvasRenderingContext2D/setTransform)
    canvasContext.clearRect(0, 0, planeDimension.width, planeDimension.height)
    canvasContext.restore()

    // for every node
    //    i = 0
    //    canvasContext.lineWidth = 1
    //    canvasContext.strokeStyle = "#333"
    //    while(i < nodeCount) {
    //      val x = simData.x(i)
    //      val y = simData.y(i)
    //
    //      i += 1
    //    }

    //     for every containment cluster
    var i = 0
    //    val catmullRom = d3.line().curve(d3.curveCatmullRomClosed).context(canvasContext)
    while (i < eulerSetCount) {
      val polygon = simData.eulerSetPolygons(i)
      assert(polygon.length % 2 == 0)
      val midpoints: Array[Vec2] = polygon.toSeq
        .sliding(2, 2)
        .map { case Seq(a, b) => (Vec2(a._1, a._2) + Vec2(b._1, b._2)) * 0.5 }
        .toArray
      var j = 0
      val n = midpoints.length
      val start = midpoints((j + n - 1) % n)
      canvasContext.fillStyle = staticData.eulerSetColor(i)
      canvasContext.beginPath()
      canvasContext.moveTo(start.x, start.y)
      while (j < n) {
        val start = midpoints((j + n - 1) % n)
        val startNode = polygon(((j - 1 + n) % n) * 2)._3
        val end = midpoints(j)
        val endNode = polygon((j) * 2)._3

        val p1 = polygon((((j - 1 + n) % n) * 2 + 1) % polygon.length)
        val p2 = polygon(((j) * 2) % polygon.length)
        val cp1 = start + (Vec2(p1._1, p1._2) - start).normalized * staticData.radius(startNode) * 2
        val cp2 = end + (Vec2(p2._1, p2._2) - end).normalized * staticData.radius(endNode) * 2
        //        canvasContext.lineTo(midpoints(j).x, midpoints(j).y)
        canvasContext.bezierCurveTo(cp1.x, cp1.y, cp2.x, cp2.y, end.x, end.y)

        //        // start radius
        //        canvasContext.lineWidth = 1
        //        canvasContext.fillStyle = "rgba(182,96,242,4.0)"
        //        canvasContext.beginPath()
        //        canvasContext.arc(start.x, start.y, staticData.radius(startNode), startAngle = 0, endAngle = fullCircle)
        //        canvasContext.fill()
        //        canvasContext.closePath()
        //
        //        // end radius
        //        canvasContext.lineWidth = 1
        //        canvasContext.fillStyle = "rgba(182,242,96,4.0)"
        //        canvasContext.beginPath()
        //        canvasContext.arc(end.x, end.y, staticData.radius(endNode), startAngle = 0, endAngle = fullCircle)
        //        canvasContext.fill()
        //        canvasContext.closePath()
        //
        //        // cp1
        //        canvasContext.lineWidth = 1
        //        canvasContext.fillStyle = "rgba(96,182,242,9.0)"
        //        canvasContext.beginPath()
        //        canvasContext.arc(cp1.x, cp1.y, 5, startAngle = 0, endAngle = fullCircle)
        //        canvasContext.fill()
        //        canvasContext.closePath()
        ////
        ////        // cp2
        //        canvasContext.lineWidth = 1
        //        canvasContext.fillStyle = "rgba(96,182,242,9.0)"
        //        canvasContext.beginPath()
        //        canvasContext.arc(cp2.x, cp2.y, 10, startAngle = 0, endAngle = fullCircle)
        //        canvasContext.fill()
        //        canvasContext.closePath()

        j += 1
      }
      canvasContext.fill()
      canvasContext.closePath()
      //      catmullRom(simData.eulerSetPolygons(i))
      i += 1
    }

    // for every connection
    i = 0
    canvasContext.lineWidth = 3
    canvasContext.strokeStyle = "#333"
    canvasContext.beginPath()
    while (i < edgeCount) {
      val source = staticData.source(i)
      val target = staticData.target(i)
      canvasContext.moveTo(simData.x(source), simData.y(source))
      canvasContext.lineTo(simData.x(target), simData.y(target))
      i += 1
    }
    canvasContext.stroke()
    canvasContext.closePath()

    if (debugDrawEnabled) debugDraw(simData, staticData, canvasContext, planeDimension)
  }

  def drawVelocities(
      simData: SimulationData,
      futureSimData: SimulationData,
      staticData: StaticData,
      canvasContext: CanvasRenderingContext2D,
      planeDimension: PlaneDimension
  ): Unit = {
    val fullCircle = 2 * Math.PI

    val nodeCount = futureSimData.n

    var i = 0

    while (i < nodeCount) {
      val remainingVel = Vec2(simData.vx(i), simData.vy(i))
      val currentVel = Vec2(futureSimData.vx(i), futureSimData.vy(i))
      val newVel = currentVel - remainingVel
      val resultVel = currentVel * futureSimData.velocityDecay
      if (resultVel.length > 0) {
        val center = Vec2(futureSimData.x(i), futureSimData.y(i))
        val onRing = center + resultVel.normalized * staticData.radius(i)
        val currentVelFromRing = onRing + currentVel
        val resultVelFromRing = onRing + resultVel
        val remainingVelFromRing = onRing + remainingVel
        val nextCenter = center + resultVel

        canvasContext.lineCap = "round"
        canvasContext.lineWidth = 4
        canvasContext.strokeStyle = "#777777"
        canvasContext.beginPath()
        canvasContext.moveTo(onRing.x, onRing.y)
        canvasContext.lineTo(currentVelFromRing.x, currentVelFromRing.y)
        canvasContext.stroke()
        canvasContext.closePath()

        canvasContext.lineWidth = 4
        canvasContext.strokeStyle = "#777777"
        canvasContext.beginPath()
        canvasContext.moveTo(onRing.x, onRing.y)
        canvasContext.lineTo(remainingVelFromRing.x, remainingVelFromRing.y)
        canvasContext.stroke()
        canvasContext.closePath()

        canvasContext.lineWidth = 4
        canvasContext.strokeStyle = "#F260B6"
        canvasContext.beginPath()
        canvasContext.moveTo(remainingVelFromRing.x, remainingVelFromRing.y)
        canvasContext.lineTo(currentVelFromRing.x, currentVelFromRing.y)
        canvasContext.stroke()
        canvasContext.closePath()

        canvasContext.lineCap = "butt"
        canvasContext.lineWidth = 4
        canvasContext.strokeStyle = "#60B6F2"
        canvasContext.beginPath()
        canvasContext.moveTo(onRing.x, onRing.y)
        canvasContext.lineTo(resultVelFromRing.x, resultVelFromRing.y)
        canvasContext.stroke()
        canvasContext.closePath()

        // next radius
        canvasContext.lineWidth = 1
        canvasContext.strokeStyle = "rgba(96,182,242,1.0)"
        canvasContext.beginPath()
        canvasContext.arc(
          nextCenter.x,
          nextCenter.y,
          staticData.radius(i),
          startAngle = 0,
          endAngle = fullCircle
        )
        canvasContext.stroke()
        canvasContext.closePath()

        // next collisionradius
        canvasContext.lineWidth = 1
        canvasContext.strokeStyle = "rgba(96,182,242,1.0)"
        canvasContext.beginPath()
        canvasContext.arc(
          nextCenter.x,
          nextCenter.y,
          staticData.collisionRadius(i),
          startAngle = 0,
          endAngle = fullCircle
        )
        canvasContext.stroke()
        canvasContext.closePath()
      }

      i += 1
    }
  }

  def debugDraw(
      simData: SimulationData,
      staticData: StaticData,
      canvasContext: CanvasRenderingContext2D,
      planeDimension: PlaneDimension
  ): Unit = {
    val fullCircle = 2 * Math.PI

    val edgeCount = staticData.edgeCount
    val containmentCount = staticData.containmentCount
    val eulerSetCount = simData.eulerSetPolygons.length

    val nodeCount = simData.n

    // for every node
    var i = 0
    canvasContext.lineWidth = 1
    while (i < nodeCount) {
      val x = simData.x(i)
      val y = simData.y(i)

      // radius
      canvasContext.strokeStyle = "rgba(0,0,0,1.0)"
      canvasContext.beginPath()
      canvasContext.arc(x, y, staticData.radius(i), startAngle = 0, endAngle = fullCircle)
      canvasContext.stroke()
      canvasContext.closePath()

      // collision radius
      canvasContext.strokeStyle = "rgba(0,0,0,0.3)"
      canvasContext.beginPath()
      canvasContext.arc(x, y, staticData.collisionRadius(i), startAngle = 0, endAngle = fullCircle)
      canvasContext.stroke()
      canvasContext.closePath()

      i += 1
    }

    // for every containment
    //    i = 0
    //    canvasContext.lineWidth = 20
    //    while (i < containmentCount) {
    //      val child = staticData.child(i)
    //      val parent = staticData.parent(i)
    //      val grad= canvasContext.createLinearGradient(simData.x(child), simData.y(child), simData.x(parent), simData.y(parent))
    //      grad.addColorStop(0, "rgba(255,255,255,0.1)") // child
    //      grad.addColorStop(1, "rgba(255,255,255,0.5)") // parent
    //      canvasContext.strokeStyle = grad
    //      canvasContext.beginPath()
    //      canvasContext.moveTo(simData.x(child), simData.y(child))
    //      canvasContext.lineTo(simData.x(parent), simData.y(parent))
    //      canvasContext.stroke()
    //      canvasContext.closePath()
    //      i += 1
    //    }

    //     for every containment cluster
    i = 0
    val polyLine = d3.line().curve(d3.curveLinearClosed).context(canvasContext)
    while (i < eulerSetCount) {
      canvasContext.strokeStyle = "rgba(255,255,255,0.5)"
      canvasContext.lineWidth = 5
      canvasContext.beginPath()
      polyLine(simData.eulerSetPolygons(i).asInstanceOf[js.Array[js.Tuple2[Double, Double]]])
      canvasContext.stroke()

      // eulerSet geometricCenter
      canvasContext.strokeStyle = "#000"
      canvasContext.lineWidth = 3
      canvasContext.beginPath()
      canvasContext.arc(
        simData.eulerSetGeometricCenterX(i),
        simData.eulerSetGeometricCenterY(i),
        10,
        startAngle = 0,
        endAngle = fullCircle
      )
      canvasContext.stroke()
      canvasContext.closePath()

      // eulerSet radius
      canvasContext.strokeStyle = staticData.eulerSetColor(i)
      canvasContext.lineWidth = 3
      canvasContext.beginPath()
      canvasContext.arc(
        simData.eulerSetGeometricCenterX(i),
        simData.eulerSetGeometricCenterY(i),
        staticData.eulerSetRadius(i),
        startAngle = 0,
        endAngle = fullCircle
      )
      canvasContext.stroke()
      canvasContext.closePath()

      // Axis aligned bounding box
      canvasContext.strokeStyle = "rgba(0,0,0,0.5)"
      canvasContext.lineWidth = 3
      canvasContext.beginPath()
      canvasContext.moveTo(simData.eulerSetPolygonMinX(i), simData.eulerSetPolygonMinY(i))
      canvasContext.lineTo(simData.eulerSetPolygonMinX(i), simData.eulerSetPolygonMaxY(i))
      canvasContext.lineTo(simData.eulerSetPolygonMaxX(i), simData.eulerSetPolygonMaxY(i))
      canvasContext.lineTo(simData.eulerSetPolygonMaxX(i), simData.eulerSetPolygonMinY(i))
      canvasContext.lineTo(simData.eulerSetPolygonMinX(i), simData.eulerSetPolygonMinY(i))
      canvasContext.stroke()

      i += 1
    }

  }
}
