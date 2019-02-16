package views.graphview

import monix.execution.Cancelable
import monix.execution.cancelables.CompositeCancelable
import d3v4._
import wust.webApp.BrowserDetect
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.{CanvasRenderingContext2D, html}
import outwatch.dom._
import outwatch.dom.dsl.events
import rx._
import vectory._
import views.graphview.VisualizationType.{Containment, Edge}
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.util.time.time
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, FocusState}
import wust.webApp.views.Components._
import flatland._

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
    focusState: FocusState,
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
    case _:EdgeData.LabeledProperty    => Edge
  }
  private var postSelection: Selection[Node] = _
  var simData: SimulationData = _
  private var staticData: StaticData = _
  private var planeDimension = PlaneDimension()
  private var canvasContext: CanvasRenderingContext2D = _
  var transform: Transform = d3.zoomIdentity
  var running = false
  //  val positionRequests = mutable.HashMap.empty[NodeId, (Double, Double)]

  private val backgroundElement = Var[Option[dom.html.Element]](None)
  private val canvasLayerElement = Var[Option[dom.html.Canvas]](None)
  private val nodeContainerElement = Var[Option[dom.html.Element]](None)

  var isCtrlPressed = false
  
  if (!BrowserDetect.isMobile)
    keyDown(KeyCode.Ctrl).foreach { isCtrlPressed = _ }

  var simulationCancelable:Cancelable = Cancelable()
  val component: VNode = {
    import outwatch.dom.dsl._
    import outwatch.dom.dsl.styles.extra._

    div(
      managedElement.asHtml { elem =>
        snabbdom.VNodeProxy.setDirty(elem)
        backgroundElement() = Some(elem)
        Cancelable { () => backgroundElement() = None; simulationCancelable.cancel() }
      },
      snabbdom.VNodeProxy.repairDomBeforePatch,
      position := "relative",
      width := "100%",
      height := "100%",
      overflow := "hidden",
      // Mouse events from all children pass through to backgroundElement (e.g. zoom).
      canvas(
        position := "absolute",
        managedElement.asHtml { elem =>
          snabbdom.VNodeProxy.setDirty(elem)
          canvasLayerElement() = Some(elem.asInstanceOf[dom.html.Canvas])
          Cancelable { () => canvasLayerElement() = None; simulationCancelable.cancel() }
        },
        snabbdom.VNodeProxy.repairDomBeforePatch,
      ),
      div(
        managedElement.asHtml { elem =>
          snabbdom.VNodeProxy.setDirty(elem)
          nodeContainerElement() = Some(elem)
          Cancelable { () => nodeContainerElement() = None; simulationCancelable.cancel() }
        },
        snabbdom.VNodeProxy.repairDomBeforePatch,
        width := "100%",
        height := "100%",
        position := "absolute",
        // pointerEvents := "none", // background handles mouse events
        transformOrigin := "top left" // same as svg/canvas default
      )
    )
  }

  Rx {
    (backgroundElement(), canvasLayerElement(), nodeContainerElement()) match {
      case (Some(backgroundElement), Some(canvasLayerElement), Some(nodeContainerElement)) =>
        simulationCancelable.cancel()
        simulationCancelable = initSimulation(backgroundElement, canvasLayerElement, nodeContainerElement)
      case _ =>
    }
  }


  def initSimulation(backgroundElement:dom.html.Element, canvasLayerElement: dom.html.Canvas, nodeContainerElement: dom.html.Element)(implicit ctx: Ctx.Owner):Cancelable = {
    val cancelable = CompositeCancelable()

    println(log("-------------------- init simulation"))
    val background = d3.select(backgroundElement)
    val canvasLayer = d3.select(canvasLayerElement)
    val nodeContainer = d3.select(nodeContainerElement)
    canvasContext = canvasLayerElement.getContext("2d").asInstanceOf[CanvasRenderingContext2D]

    val graphRx: Rx[Graph] = Rx {
      println(log("\n") + log(s"---- graph update[${state.graph().nodes.length}] ----"))
      time(log("filtering graph")) {
        val graph = state.graph()
        val focusedIdx = graph.idToIdx(focusState.focusedId)
        val taskChildrenSet = graph.taskChildrenIdx.toArraySet(focusedIdx)
        val tagSet = ArraySet.create(graph.size)
        graph.nodes.foreachIndexAndElement{ (i,elem) =>
          if(elem.role == NodeRole.Tag) tagSet += i
        }
        graph.filterIdx(nodeIdx => taskChildrenSet.contains(nodeIdx) || tagSet.contains(nodeIdx))
      }
    }

    // will be called, when user zoomed the view
    def zoomed(): Unit = {
      transform = d3.event.transform // since zoomed is called via d3 event, transform was set by d3
      // println(log(s"zoomed: ${transform.k}"))
      canvasContext.setTransform(transform.k, 0, 0, transform.k, transform.x, transform.y) // set transformation matrix (https://developer.mozilla.org/de/docs/Web/API/CanvasRenderingContext2D/setTransform)
      nodeContainer.style(
        "transform",
        s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})"
      )
      // assert(!simData.isNaN, s"zoomed: before draw canvas x:${simData.x(0)} vx:${simData.vx(0)}")
      drawCanvas(simData, staticData, canvasContext, planeDimension)
      // assert(!simData.isNaN, s"zoomed: after draw canvas x:${simData.x(0)} vx:${simData.vx(0)}")
      if (debugDrawEnabled) calculateAndDrawCurrentVelocities()
    }

    // Drag & Zoom example: https://bl.ocks.org/mbostock/3127661b6f13f9316be745e77fdfb084
    val zoom = d3
      .zoom()
      .scaleExtent(js.Array(0.01, 10))
      .on("zoom", () => zoomed())
      .clickDistance(10) // interpret short drags as clicks
    cancelable += Cancelable(() => zoom.on("zoom", null))

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
    cancelable += Cancelable(() => background.on("click", null:ListenerFunction0))

    cancelable += events.window.onResize.foreach { _ =>
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
      ForceSimulationForces.calculateEulerZonePolygons(simData, staticData)
      ForceSimulationForces.calculateEulerSetConnectedComponentsPolygons(simData, staticData)
      ForceSimulationForces.eulerSetCenter(simData, staticData)
      ForceSimulationForces.eulerZoneCenter(simData, staticData)
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
        // since nodeContainer had size zero, all posts also had size zero,
        // so we have to resize nodeContainer and then reinitialize the node sizes in static data
        transform = d3.zoomIdentity
        nodeContainer.style(
          "transform",
          s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})"
        )
        staticData = StaticData(graphRx.now, postSelection, transform, labelVisualization)
      }

      val arbitraryFactor = 1.3
      // TODO: handle cases:
      // - long window with big blob in the middle
      val scale = Math.sqrt((width * height) / (staticData.totalReservedArea * arbitraryFactor)) min 1.5 // scale = sqrt(ratio) because areas grow quadratically
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
      println("calling zoomed...")
      // assert(!simData.isNaN, s"resized: before zoomed x:${simData.x(0)} vx:${simData.vx(0)}")
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
      postSelection = nodeContainer.selectAll[Node]("div.graphnode")
      // First, we write x,y,vx,vy into the dom
      backupSimDataToDom(simData, postSelection)
      // The CoordinateWrappers are stored in dom and reordered by d3
      updateDomNodes(graph.nodes.toJSArray, postSelection, onClick) // d3 data join
      postSelection = nodeContainer.selectAll[Node]("div.graphnode") // update outdated postSelection
      registerDragHandlers(postSelection, dragSubject, dragStart, dragging, dropped)
      // afterwards we write the data back to our new arrays in simData
      simData = createSimDataFromDomBackup(postSelection)
      // For each node, we calculate its rendered size, radius etc.
      staticData = StaticData(graph, postSelection, transform, labelVisualization)
      ForceSimulationForces.nanToPhyllotaxis(simData, spacing = Math.sqrt(staticData.totalReservedArea / graph.size)/2) // set initial positions for new nodes
      resized() // adjust zoom to possibly changed accumulated node area

      println(log(s"Simulation and Post Data initialized. [${simData.n}]"))
      startAnimated() // this also triggers the initial simulation start
    }

    cancelable += Cancelable(() => stop())

    cancelable
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

  def startAnimated(alpha: Double = 1, alphaMin: Double = 0.01): Unit = {
    println(log("started"))

    val ticks = 150 // Default = 300
    val forceFactor = 0.3
    simData.alpha = alpha
    simData.alphaMin = alphaMin // stop simulation earlier (d3 default = 0.001)
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
    // assert(!simData.isNaN, s"before clone x:${simData.x(0)} vx:${simData.vx(0)}")
    val futureSimData = simData.clone()
    // assert(!futureSimData.isNaN, s"before calculateVelocities x:${simData.x(0)} vx:${simData.vx(0)}")
    calculateVelocities(futureSimData, staticData, planeDimension)
    drawVelocities(simData, futureSimData, staticData, canvasContext, planeDimension)
  }

  def step(alpha: Double = 1.0): Unit = {
    simData.alpha = alpha
    // assert(!simData.isNaN, s"before simualtionstep x:${simData.x(0)} vx:${simData.vx(0)}")
    simulationStep(simData, staticData, planeDimension)
    // assert(!simData.isNaN, s"after simualtionstep x:${simData.x(0)} vx:${simData.vx(0)}")
    draw()
    // assert(!simData.isNaN, s"step: after draw x:${simData.x(0)} vx:${simData.vx(0)}")
    if (debugDrawEnabled) calculateAndDrawCurrentVelocities()
  }

  def draw(): Unit = {
    ForceSimulationForces.calculateEulerSetPolygons(simData, staticData)
    ForceSimulationForces.calculateEulerZonePolygons(simData, staticData)
      ForceSimulationForces.calculateEulerSetConnectedComponentsPolygons(simData, staticData)
    applyNodePositions(simData, staticData, postSelection)
    drawCanvas(simData, staticData, canvasContext, planeDimension)
  }
}

object ForceSimulation {
  private val debugDrawEnabled = false
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
        .style("width", (node: Node) => calcPostWidth(node).getOrElse(js.undefined)) //TODO: does not update size when editing small node and write a long content
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

      // assert(!simData.isNaN, s"before calculateVelocities x:${simData.x(0)} vx:${simData.vx(0)}")
      calculateVelocities(simData, staticData, planeDimension)
      // assert(!simData.isNaN, s"before applyVelocities x:${simData.x(0)} vx:${simData.vx(0)}")
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
    // assert(!simData.isNaN, s"x:${simData.x(0)} vx:${simData.vx(0)}")
    initQuadtree(simData, staticData)
    eulerSetCenter(simData, staticData)
    eulerZoneCenter(simData, staticData)
    calculateEulerSetPolygons(simData, staticData)
    calculateEulerZonePolygons(simData, staticData)
    calculateEulerSetConnectedComponentsPolygons(simData, staticData)

    rectBound(simData, staticData, planeDimension, strength = 0.1)
    // assert(!simData.isNaN)
    keepMinimumNodeDistance(simData, staticData, distance = nodeSpacing, strength = 0.2)
    // assert(!simData.isNaN)
//    edgeLength(simData, staticData)

    eulerSetClustering(simData, staticData, strength = 0.05)
    eulerZoneClustering(simData, staticData, strength = 0.1)
    // eulerZoneAttraction(simData, staticData, strength = 0.1)
    // assert(!simData.isNaN)
    separateOverlappingEulerSets(simData, staticData, strength = 0.1)
    separateOverlappingEulerZones(simData, staticData, strength = 0.1)
    separateZonesFromSets(simData, staticData, strength = 0.1)
    separateZonesFromSetConnectedComponents(simData, staticData, strength = 0.1)

    pushOutOfWrongEulerSet(simData,staticData, strength = 0.1)
    pushOutOfWrongEulerZone(simData,staticData, strength = 0.1)
    // assert(!simData.isNaN)
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
    val eulerSetCount = simData.eulerSetCollisionPolygon.length
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
    canvasContext.lineWidth = 3
    loop (eulerSetCount) { i =>
      val convexHull = simData.eulerSetConvexHull(i)
      if(convexHull.size > 1) {
        val tangents = simData.eulerSetConvexHullTangents(i)

        val n = convexHull.size * 2
        canvasContext.fillStyle = staticData.eulerSetColor(i)
        canvasContext.strokeStyle = staticData.eulerSetStrokeColor(i)
        canvasContext.beginPath()
        var j = 0
        while (j < n) {
          val circle = convexHull(j / 2)
          val startPoint = tangents(((j-1)+n)%n)
          val endPoint = tangents(j)
          val startAngle = (startPoint - circle.center).angle
          val endAngle = (endPoint - circle.center).angle
          canvasContext.arc(circle.center.x, circle.center.y, circle.r, startAngle, endAngle, anticlockwise = false)
          canvasContext.lineTo(tangents(j+1).x, tangents(j+1).y)

          j += 2
        }
        canvasContext.fill()
        canvasContext.stroke()
        canvasContext.closePath()
      }
    }

    // for every connection
    canvasContext.lineWidth = 3
    canvasContext.strokeStyle = "#333"
    canvasContext.beginPath()
    loop (edgeCount) { i =>
      val source = staticData.source(i)
      val target = staticData.target(i)
      canvasContext.moveTo(simData.x(source), simData.y(source))
      canvasContext.lineTo(simData.x(target), simData.y(target))
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
    import staticData._
    import simData._

    val polyLine = d3.line().curve(d3.curveLinearClosed).context(canvasContext)

    // count in simData can be zero
    val eulerSetCount = simData.eulerSetCollisionPolygon.length
    val eulerZoneCount = simData.eulerZoneCollisionPolygon.length
    val eulerSetConnectedComponentCount = simData.eulerSetConnectedComponentCollisionPolygon.length

    val nodeCount = simData.n

    // for every node
    canvasContext.lineWidth = 1
    loop (nodeCount) { i =>
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
    loop (eulerSetCount) { i =>
      canvasContext.strokeStyle = "rgba(128,128,128,0.6)"
      canvasContext.lineWidth = 3
      canvasContext.beginPath()
      polyLine(simData.eulerSetCollisionPolygon(i).map(v => js.Tuple2(v.x, v.y)).toJSArray)
      canvasContext.stroke()

      // tangents
      // canvasContext.strokeStyle = "rgba(128,128,0,0.6)"
      // canvasContext.lineWidth = 2
      // simData.eulerSetConvexHullTangents(i).sliding(2,2).foreach { case Seq(a,b) =>
      //   canvasContext.beginPath()
      //   canvasContext.moveTo(a.x,a.y)
      //   canvasContext.lineTo(b.x,b.y)
      //   canvasContext.stroke()
      // }

      // eulerSet geometricCenter
      canvasContext.strokeStyle = "#000"
      canvasContext.lineWidth = 3
      canvasContext.beginPath()
      canvasContext.arc(
        simData.eulerSetGeometricCenter(i).x,
        simData.eulerSetGeometricCenter(i).y,
        10,
        startAngle = 0,
        endAngle = fullCircle
      )
      canvasContext.stroke()
      canvasContext.closePath()

      // Axis aligned bounding box
      drawAARect(canvasContext, rect = simData.eulerSetCollisionPolygonAABB(i), color = "rgba(0,0,0,0.1)", lineWidth = 3)
    }

    loop (eulerSetConnectedComponentCount) { i =>
      canvasContext.strokeStyle = "rgba(0,0,255,0.6)"
      canvasContext.lineWidth = 3
      canvasContext.beginPath()
      polyLine(simData.eulerSetConnectedComponentCollisionPolygon(i).map(v => js.Tuple2(v.x, v.y)).toJSArray)
      canvasContext.stroke()

      // Axis aligned bounding box
      drawAARect(canvasContext, rect = simData.eulerSetConnectedComponentCollisionPolygonAABB(i), color = "rgba(0,0,255,0.1)", lineWidth = 3)
    }

    loop (eulerZoneCount) { i =>
      canvasContext.fillStyle = "rgba(128,128,128,0.4)"
      canvasContext.lineWidth = 1
      canvasContext.beginPath()
      polyLine(simData.eulerZoneCollisionPolygon(i).map(v => js.Tuple2(v.x, v.y)).toJSArray)
      canvasContext.fill()

      // tangents
      // canvasContext.strokeStyle = "rgba(128,128,0,0.6)"
      // canvasContext.lineWidth = 2
      // simData.eulerZoneConvexHullTangents(i).sliding(2,2).foreach { case Seq(a,b) =>
      //   canvasContext.beginPath()
      //   canvasContext.moveTo(a.x,a.y)
      //   canvasContext.lineTo(b.x,b.y)
      //   canvasContext.stroke()
      // }

      // eulerZone geometricCenter
      // canvasContext.strokeStyle = "#000"
      // canvasContext.lineWidth = 3
      // canvasContext.beginPath()
      // canvasContext.arc(
      //   simData.eulerZoneGeometricCenter(i).x,
      //   simData.eulerZoneGeometricCenter(i).y,
      //   10,
      //   startAngle = 0,
      //   endAngle = fullCircle
      // )
      // canvasContext.stroke()
      // canvasContext.closePath()

      // Axis aligned bounding box
      drawAARect(canvasContext, rect = simData.eulerZoneCollisionPolygonAABB(i), color = "rgba(0,0,0,0.1)", lineWidth = 1)
    }

  }

  def drawAARect(context: CanvasRenderingContext2D, rect:AARect, color:String, lineWidth:Int):Unit = {
    context.strokeStyle = color
    context.lineWidth = lineWidth
    context.beginPath()
    val vertices = rect.verticesCCW
    context.moveTo(vertices(0).x, vertices(0).y)
    context.lineTo(vertices(1).x, vertices(1).y)
    context.lineTo(vertices(2).x, vertices(2).y)
    context.lineTo(vertices(3).x, vertices(3).y)
    context.lineTo(vertices(0).x, vertices(0).y)
    context.stroke()
  }
}
