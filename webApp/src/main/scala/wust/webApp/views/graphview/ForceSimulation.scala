package wust.webApp.views.graphview

import d3v4._
import flatland._
import monix.execution.Cancelable
import monix.execution.cancelables.CompositeCancelable
import monix.reactive.Observable
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.{CanvasRenderingContext2D, html}
import outwatch.dom._
import outwatch.dom.dsl.events
import rx._
import vectory._
import wust.webUtil.BrowserDetect
import wust.webUtil.outwatchHelpers._
import wust.graph._
import wust.ids._
import wust.util.macros.InlineList
import wust.util.time.time
import wust.webApp.dragdrop.{DragItem, DragPayload, DragTarget}
import wust.webApp.state.{FocusPreference, FocusState, GlobalState}
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{drag, registerDragContainer}

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

final case class PlaneDimension(
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
  val nodePadding = 3
  val eulerSetPadding = nodePadding
  val minimumDragHighlightRadius = 50
  val nodeSpacing = 10
}

class ForceSimulation(
    
    focusState: FocusState,
    onDrop: (NodeId, NodeId, Boolean) => Boolean,
    roleToDragItemPayload:PartialFunction[(NodeId, NodeRole), DragPayload],
    roleToDragItemTarget:PartialFunction[(NodeId, NodeRole), DragTarget]
)(implicit ctx: Ctx.Owner) {
  //TODO: sometimes dragging parent into child crashes simulation
  import ForceSimulation._
  import ForceSimulationConstants._

  val postCreationMenus: Var[List[Vec2]] = Var(Nil)

  //TODO why partial?
  private var labelVisualization: PartialFunction[EdgeData.Type, VisualizationType] = {
    case EdgeData.Child.tpe         => VisualizationType.Containment
    case _:EdgeData.LabeledProperty => VisualizationType.Edge
  }
  private var nodeSelection: d3.Selection[Node] = _
  var simData: SimulationData = _
  private var staticData: StaticData = _
  private var planeDimension = PlaneDimension()
  private var canvasContext: CanvasRenderingContext2D = _
  var transform: Var[d3.Transform] = Var(d3.zoomIdentity)
  var running = false
  var dragStartPos:Vec2 = Vec2.zero

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
      keyed, // forces onDestroy (forbids reuse of dom node),
      // so that this dirty dom is not reused (https://github.com/OutWatch/outwatch/issues/287)
      // somehow postpatch is not triggered, so that the dom is not automatically repaired...
      // snabbdom.VNodeProxy.repairDomBeforePatch,
      managedElement.asHtml { elem =>
        // snabbdom.VNodeProxy.setDirty(elem)
        backgroundElement() = Some(elem)
        Cancelable { () => backgroundElement() = None; simulationCancelable.cancel() }
      },
      position := "relative",
      width := "100%",
      height := "100%",
      overflow := "hidden",
      // Mouse events from all children pass through to backgroundElement (e.g. zoom).
      canvas(
        position := "absolute",
        managedElement.asHtml { elem =>
          canvasLayerElement() = Some(elem.asInstanceOf[dom.html.Canvas])
          Cancelable { () => canvasLayerElement() = None; simulationCancelable.cancel() }
        },
      ),
      div(
        managedElement.asHtml { elem =>
          nodeContainerElement() = Some(elem)
          Cancelable { () => nodeContainerElement() = None; simulationCancelable.cancel() }
        },
        width := "100%",
        height := "100%",
        position := "absolute",
        // pointerEvents := "none", // background handles mouse events
        transformOrigin := "top left", // same as svg/canvas default
        registerDragContainer
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
    cancelable += Cancelable(() => scribe.info("canceling simulation"))

    scribe.info(log("-------------------- init simulation"))
    val background = d3.select(backgroundElement)
    val canvasLayer = d3.select(canvasLayerElement)
    val nodeContainer = d3.select(nodeContainerElement)
    canvasContext = canvasLayerElement.getContext("2d").asInstanceOf[CanvasRenderingContext2D]

    val graphRx: Rx[Graph] = Rx {
      scribe.info(log("\n") + log(s"---- graph update[${GlobalState.graph().nodes.length}] ----"))
      time(log("filtering graph")) {
        val graph = GlobalState.graph()
        val focusedIdx = graph.idToIdxOrThrow(focusState.focusedId)
        val taskChildrenSet = graph.taskChildrenIdx.toArraySet(focusedIdx)
        val tagSet = ArraySet.create(graph.size)
        graph.taskChildrenIdx.foreachElement(focusedIdx) { nodeIdx =>
          graph.parentsIdx.foreachElement(nodeIdx) { parentIdx =>
            val elem = graph.nodes(parentIdx)
            if(elem.role == NodeRole.Tag) tagSet += parentIdx
          }
        }
        graph.childrenIdx.foreachElement(focusedIdx) { nodeIdx =>
          val elem = graph.nodes(nodeIdx)
          if(elem.role == NodeRole.Tag) tagSet += nodeIdx
        }
        graph.filterIdx(nodeIdx => taskChildrenSet.contains(nodeIdx) || tagSet.contains(nodeIdx))
      }
    }

    // will be called, when user zoomed the view
    def zoomed(): Unit = {
      val tr = d3.event.transform // since zoomed is called via d3 event, transform was set by d3
      // scribe.info(log(s"zoomed: ${transform.k}"))
      canvasContext.setTransform(tr.k, 0, 0, tr.k, tr.x, tr.y) // set transformation matrix (https://developer.mozilla.org/de/docs/Web/API/CanvasRenderingContext2D/setTransform)
      nodeContainer.style(
        "transform",
        s"translate(${tr.x}px,${tr.y}px) scale(${tr.k})"
      )
      // assert(!simData.isNaN, s"zoomed: before draw canvas x:${simData.x(0)} vx:${simData.vx(0)}")
      drawCanvas(simData, staticData, canvasContext, planeDimension)
      // assert(!simData.isNaN, s"zoomed: after draw canvas x:${simData.x(0)} vx:${simData.vx(0)}")
      if (debugDrawEnabled) calculateAndDrawCurrentVelocities()
      transform() = tr
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
          scribe.info("clicked background")

          // if visualization was broken, fix it
          if (transform.now.k.isNaN) { // happens, when background size = 0, which happens when rendered invisibly
            // fixes visualization
            resized()
            startAnimated()
          } else {
            if (postCreationMenus.now.isEmpty) {
              val pos = transform.now.invert(d3.mouse(background.node))
              postCreationMenus() = List(Vec2(pos(0), pos(1)))
            } else {
              postCreationMenus() = Nil
            }
          }
        }
      )
    cancelable += Cancelable(() => background.on("click", null:ListenerFunction0))

    cancelable += Observable(events.window.onResize, GlobalState.rightSidebarNode.map(_.isDefined).toLazyTailObservable).merge.foreach { _ =>
      // TODO: detect element resize instead: https://www.npmjs.com/package/element-resize-detector
      resized()
      startAnimated()
    }

    def dragSubject(d: Node, i: Int): Coordinates = {
      new Coordinates(
        x = simData.x(i) * transform.now.k,
        y = simData.y(i) * transform.now.k
      )
    }

    def dragStart(n: html.Element, d: Node, dragging: Int): Unit = {
      running = false
      ForceSimulationForces.initQuadtree(simData, staticData)
      simData.quadtree.remove(dragging)
      dragStartPos = Vec2(d3.event.x, d3.event.y)
    }

    def hit(dragging: Int, minRadius: Double): Option[Int] = {
      if (simData.n <= 1) None
      else {
        val x = d3.event.x / transform.now.k
        val y = d3.event.y / transform.now.k

        val target = simData.quadtree.find(x, y) // ,staticData.maxRadius
        def distance = Vec2.length(x - simData.x(target), y - simData.y(target))
        if (target != dragging && (distance <= minRadius || distance <= staticData.radius(target))) {
          Some(target)
        } else None
      }
    }

    def dragging(n: html.Element, d: Node, dragging: Int): Unit = {
      running = false
      val x = d3.event.x / transform.now.k
      val y = d3.event.y / transform.now.k

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

      // val dragTarget = roleToDragItemPayload.lift((d.id, d.role))
      // if (dragTarget.isDefined) {
      //   hit(dragging, minimumDragHighlightRadius).foreach { target =>
      //     canvasContext.lineWidth = 1

      //     val bgColor = d3.lab(eulerBgColor(staticData.posts(target).id).toHex) //TODO: use d3.rgb or make colorado handle opacity
      //     val radius = (staticData.radius(target) + eulerSetPadding) max minimumDragHighlightRadius
      //     bgColor.opacity = 0.8
      //     canvasContext.fillStyle = bgColor
      //     canvasContext.beginPath()
      //     canvasContext.arc(
      //       simData.x(target),
      //       simData.y(target),
      //       radius,
      //       startAngle = 0,
      //       endAngle = 2 * Math.PI
      //     )
      //     canvasContext.fill()
      //     canvasContext.closePath()
      //   }
      // }

      ForceSimulationForces.clearVelocities(simData)
      simData.alpha = 1.0
      if (debugDrawEnabled) calculateAndDrawCurrentVelocities()
    }

    def dropped(n: html.Element, d: Node, dragging: Int): Unit = {
      hit(dragging, minimumDragHighlightRadius).foreach { target =>
        val successful = onDrop(staticData.posts(dragging).id, staticData.posts(target).id, isCtrlPressed)
        if(!successful) {
          simData.x(dragging) = dragStartPos.x
          simData.y(dragging) = dragStartPos.y
          draw()
        }
      }
    }

    def nodeOnClick(node: Node, i: Int): Unit = {

      scribe.info(s"clicked node[$i]")
      d3.event.stopPropagation() // prevent click from bubbling to background

      if (InlineList.contains[NodeRole](NodeRole.Task, NodeRole.Message, NodeRole.Note)(node.role)) {
        val nextNode = if (GlobalState.rightSidebarNode.now.exists(_.nodeId == node.id)) None else Some(FocusPreference(node.id))
        GlobalState.rightSidebarNode() = nextNode
      }
    }

    // should be called when the size of the visualization changed
    def resized(): Unit = {
      val rect = backgroundElement.getBoundingClientRect()
      import rect.{height, width}
      val resizedFromZero = (planeDimension.width == 0 || planeDimension.height == 0) && width > 0 && height > 0
      if (resizedFromZero) { // happens when graphview was rendered in a hidden element
        // since nodeContainer had size zero, all posts also had size zero,
        // so we have to resize nodeContainer and then reinitialize the node sizes in static data
        val tr = d3.zoomIdentity
        nodeContainer.style(
          "transform",
          s"translate(${tr.x}px,${tr.y}px) scale(${tr.k})"
        )
        staticData = StaticData(graphRx.now, nodeSelection, tr, labelVisualization)
        transform() = tr
      }

      val arbitraryFactor = 2.5
      // TODO: handle cases:
      // - long window with big blob in the middle
      val scale = Math.sqrt((width * height) / (staticData.totalReservedArea * arbitraryFactor)) min 1.5 // scale = sqrt(ratio) because areas grow quadratically
//      scribe.info(log(s"resized: $width x $height, fromZero: $resizedFromZero, scale: $scale"))
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
      scribe.info("calling zoomed...")
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

      scribe.info(
        log(
          s"updating simulation[${Option(simData).fold("_")(_.n.toString)} -> ${graph.nodes.length}]..."
        )
      )
      stop()

      // We want to let d3 do the re-ordering while keeping the old coordinates
      nodeSelection = nodeContainer.selectAll[Node]("div.graphnode")
      // First, we write x,y,vx,vy into the dom
      backupSimDataToDom(simData, nodeSelection)
      // The CoordinateWrappers are stored in dom and reordered by d3
      updateDomNodes( graph.nodes.toJSArray, nodeSelection, nodeOnClick) // d3 data join
      nodeSelection = nodeContainer.selectAll[Node]("div.graphnode") // update outdated nodeSelection
      registerDragHandlers(nodeSelection, dragSubject, dragStart, dragging, dropped)
      // afterwards we write the data back to our new arrays in simData
      simData = createSimDataFromDomBackup(nodeSelection)
      // For each node, we calculate its rendered size, radius etc.
      staticData = StaticData(graph, nodeSelection, transform.now, labelVisualization)
      initNodePositions()
      dom.window.setTimeout(() => resized(), 0) // defer size detection until rendering is finished. Else initial size can be wrong.

      scribe.info(log(s"Simulation and Post Data initialized. [${simData.n}]"))
      startAnimated() // this also triggers the initial simulation start
    }

    cancelable += Cancelable(() => stop())

    cancelable
  }

  def initNodePositions():Unit = {
    ForceSimulationForces.nanToPhyllotaxis(simData, spacing = 30) // set initial positions for new nodes
  }

  def startHidden(): Unit = {
    scribe.info(log("started"))
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
    scribe.info(log("started"))

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
    scribe.info(log("resumed"))
    if (!running) {
      running = true
      animationStep(0)
    }
  }

  def stop(): Unit = {
    scribe.info(log("stopped"))
    running = false
  }

  def reposition(): Unit = {
    stop()
    loop (simData.n) { i =>
      simData.vx(i) *= 0
      simData.vy(i) *= 0
      simData.x(i) += Double.NaN
      simData.y(i) += Double.NaN
    }
    initNodePositions()
    draw()
    if (debugDrawEnabled) calculateAndDrawCurrentVelocities()
  }

  def animationStep(timestamp: Double): Unit = {
    if (!simStep()) { // nothing happened, alpha surpassed threshold
      () // so no need to draw. stop animation
    } else {
      draw()
      dom.window.requestAnimationFrame(animationStep)
    }
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
    applyNodePositions(simData, staticData, nodeSelection)
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
    nodeSelection: d3.Selection[Node],
    nodeOnClick: (Node, Int) => Unit
  )(implicit ctx: Ctx.Owner): Unit = {
    // This is updating the dom using a D3 data join. (https://bost.ocks.org/mike/join)
    val node = nodeSelection.data(posts, (p: Node) => p.id.toString)
    time(log(s"removing old posts from dom[${node.exit().size()}]")) {
      node
        .exit()
        .remove()
    }

    time(log(s"updating staying posts[${node.size()}]")) {
      node
        .html((node: Node) => htmlNodeData(node.data))
        .style("width", (node: Node) => calcPostWidth(node).getOrElse(js.undefined)) //TODO: does not update size when editing small node and write a long content
        .on("click", nodeOnClick) //TODO: does d3 provide a wrong index?
    }

    time(log(s"adding new posts to dom[${node.enter().size()}]")) {
      node
        .enter()
        .append((node: Node) => {
          import outwatch.dom.dsl._
          // TODO: is outwatch rendering slow here? Should we use d3 instead?
          val postWidth = calcPostWidth(node)
          div(
            cls := "graphnode",
            postWidth,
            renderNodeData( node),
            // pointerEvents.auto, // re-enable mouse events
            drag(target = DragItem.Task(node.id))
          ).render
        })
        .on("click", nodeOnClick)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Nothing"))
  def registerDragHandlers(
      nodeSelection: d3.Selection[Node],
      dragSubject: (Node, Index) => Coordinates,
      dragStart: (html.Element, Node, Index) => Unit,
      dragged: (html.Element, Node, Index) => Unit,
      dropped: (html.Element, Node, Index) => Unit
  ): Unit = {
    nodeSelection.call(
      d3.drag[Node]()
        .clickDistance(10) // interpret short drags as clicks
        .subject(dragSubject) // important for drag offset
        .on("start", dragStart)
        .on("drag", dragged)
        .on("end", dropped)
    )
  }

  def backupSimDataToDom(simData: SimulationData, nodeSelection: d3.Selection[Node]): Unit = {
    time(log(s">> backupData[${if (simData != null) simData.n.toString else "None"}]")) {
      nodeSelection.each[html.Element] { (node: html.Element, _: Node, i: Int) =>
        val coordinates = new Coordinates
        node.asInstanceOf[js.Dynamic].__databackup__ = coordinates // yay javascript!
        coordinates.x = simData.x(i)
        coordinates.y = simData.y(i)
        coordinates.vx = simData.vx(i)
        coordinates.vy = simData.vy(i)
      }
    }
  }

  def createSimDataFromDomBackup(nodeSelection: d3.Selection[Node]): SimulationData = {
    time(log(s"<< createSimDataFromBackup[${nodeSelection.size()}]")) {
      val n = nodeSelection.size()
      val simData = new SimulationData(n)
      nodeSelection.each[html.Element] { (node: html.Element, _: Node, i: Int) =>
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
    if (alpha < alphaMin) false
    else {
      alpha += (alphaTarget - alpha) * alphaDecay
      //    scribe.info(log("step: " + alpha))
      true
    }
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
        stepped
      } else {

        // assert(!simData.isNaN, s"before calculateVelocities x:${simData.x(0)} vx:${simData.vx(0)}")
        calculateVelocities(simData, staticData, planeDimension)
        // assert(!simData.isNaN, s"before applyVelocities x:${simData.x(0)} vx:${simData.vx(0)}")
        applyVelocities(simData)
      }
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

    rectBound(simData, staticData, planeDimension, strength = 0.5)
    // assert(!simData.isNaN)
    keepMinimumNodeDistance(simData, staticData, distance = nodeSpacing, strength = 0.5)
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
      nodeSelection: d3.Selection[Node]
  ): Unit = {
    nodeSelection
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

    if (debugDrawEnabled) DebugDraw.draw(simData, staticData, canvasContext, planeDimension)
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
}
