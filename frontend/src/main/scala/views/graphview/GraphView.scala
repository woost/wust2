package wust.frontend.views.graphview

import monix.execution.Scheduler.Implicits.global
import org.scalajs.d3v4._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.{Element, window, console}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import vectory._
import wust.frontend.Color._
import wust.frontend.views.View
import wust.frontend.{DevOnly, DevPrintln, GlobalState, PostCreatorMenu}
import wust.graph._
import wust.util.outwatchHelpers._
import wust.util.time.time

import scala.concurrent.ExecutionContext
import scala.scalajs.js



//TODO: remove disableSimulation argument, as it is only relevant for tests. Better solution?
class GraphView(disableSimulation: Boolean = false)(implicit ec: ExecutionContext, owner: Ctx.Owner) extends View {
  override val key = "graph"
  override val displayName = "Mindmap"
  override def apply(state: GlobalState) = {
    div(
      height := "100%",
      backgroundColor <-- state.pageStyle.map(_.bgColor),
      onInsert --> sideEffect(new GraphViewInstance(state, _, disableSimulation))
    )
  }
}

object GraphView {
  def postView(post: Post) = div(
    post.content,
    cls := "graphpost"
  )
}


case class MenuAction(name: String, action: SimPost => Unit, showIf: SimPost => Boolean = _ => true)
case class DragAction(name: String, action: (SimPost, SimPost) => Unit)

object KeyImplicits {
  implicit val SimPostWithKey = new WithKey[SimPost](_.id)
  implicit val SimConnectionWithKey = new WithKey[SimConnection](c => s"${c.sourceId} ${c.targetId}")
  implicit val SimRedirectedConnectionWithKey = new WithKey[SimRedirectedConnection](c => s"${c.sourceId} ${c.targetId}")
  implicit val ContainmentClusterWithKey = new WithKey[ContainmentCluster](_.id)
  implicit val postCreatorMenuWithKey = new WithKey[PostCreatorMenu](_.toString)
}

class GraphViewInstance(state: GlobalState, element: dom.Element, disableSimulation: Boolean = false)(implicit ec: ExecutionContext, ctx: Ctx.Owner) {
  val graphState = new GraphState(state)
  val d3State = new D3State(disableSimulation)
  val postDrag = new PostDrag(graphState, d3State, onPostDrag _, onPostDragEnd _)
  import state.inner.{displayGraphWithoutParents => rxDisplayGraph, _}
  import graphState._

  // prepare containers where we will append elements depending on the data
  // order is important
  import KeyImplicits._
  val container = d3.select(element)

  val svg = container.append("svg")
  val containmentClusterSelection = SelectData.rx(ContainmentClusterSelection, rxContainmentCluster)(svg.append("g"))
  val collapsedContainmentClusterSelection = SelectData.rx(CollapsedContainmentClusterSelection, rxCollapsedContainmentCluster)(svg.append("g"))
  val connectionLineSelection = SelectData.rx(ConnectionLineSelection, rxSimConnection)(svg.append("g"))
  val redirectedConnectionLineSelection = SelectData.rx(RedirectedConnectionLineSelection, rxSimRedirectedConnection)(svg.append("g"))
  // useful for simulation debugging: (also uncomment in draw())
  val postRadiusSelection = SelectData.rx(new PostRadiusSelection(graphState, d3State), rxSimPosts)(svg.append("g"))
  val postCollisionRadiusSelection = SelectData.rx(PostCollisionRadiusSelection, rxSimPosts)(svg.append("g"))
  val postContainmentRadiusSelection = SelectData.rx(PostContainmentRadiusSelection, rxSimPosts)(svg.append("g"))

  val html = container.append("div")
  val connectionElementSelection = SelectData.rx(new ConnectionElementSelection(graphState), rxSimConnection)(html.append("div"))
  val rawPostSelection = new PostSelection(graphState, d3State, postDrag, updatedNodeSizes _)
  val postSelection = SelectData.rx(rawPostSelection, rxSimPosts)(html.append("div"))
  val draggingPostSelection = SelectData.rxDraw(DraggingPostSelection, postDrag.draggingPosts)(html.append("div")) //TODO: place above ring menu?

  val postMenuLayer = container.append("div")
  // val postMenuSelection = SelectData.rxDraw(new PostMenuSelection(graphState, d3State), rxFocusedSimPost.map(_.toJSArray))(postMenuLayer.append("div"))
  // val postCreatorMenu = SelectData.rxDraw(new CreatePostMenuSelection(graphState, d3State), postCreatorMenus.map(_.toJSArray))(postMenuLayer.append("div"))

  val menuSvg = container.append("svg")
  val dragMenuLayer = menuSvg.append("g")
  // val dragMenuSelection = SelectData.rxDraw(new DragMenuSelection(postDrag.dragActions, d3State), postDrag.closestPosts)(dragMenuLayer.append("g"))

//  val controls = {
//    val iconButton = button(width := "2.5em", padding := "5px 10px")
//    container.append(() => div(
//      position.absolute, left := "5px", top := "100px",
//      iconButton("⟳", title := "automatic layout",
//        onMouseDown --> { () =>
//          rxSimPosts.now.foreach { simPost =>
//            simPost.fixedPos = js.undefined
//          }
//          d3State.simulation.alpha(1).alphaTarget(1).restart()
//        },
//        onMouseUp --> { () =>
//          d3State.simulation.alphaTarget(0)
//        }), br(),
//      DevOnly {
//        div(
//          button("tick", onClick --> {
//            rxSimPosts.now.foreach { simPost =>
//              simPost.fixedPos = js.undefined
//            }
//            d3State.simulation.tick()
//            draw()
//          }), br(),
//          button("stop", onClick --> {
//            d3State.simulation.stop()
//            draw()
//          }), br(),
//          button("draw", onClick --> {
//            rxSimPosts.now.foreach { simPost =>
//              simPost.fixedPos = js.undefined
//            }
//            draw()
//          }), br(),
//          iconButton("+", title := "zoom in", onClick --> {
//            svg.call(d3State.zoom.scaleBy _, 1.2) //TODO: transition for smooth animation, zoomfactor in global constant
//          }), br(),
//          iconButton("0", title := "reset zoom", onClick --> {
//            recalculateBoundsAndZoom()
//          }), br(),
//          iconButton("-", title := "zoom out", onClick --> {
//            svg.call(d3State.zoom.scaleBy _, 1 / 1.2) //TODO: transition for smooth animation, zoomfactor in global constant
//          })
//        )
//      }
//    ).render)
//  }

  // Arrows
  svg.append("svg:defs").append("svg:marker")
    .attr("id", "graph_arrow")
    .attr("viewBox", "0 -3 10 6") // x y w h
    .attr("refX", 35) // This is a workaround. The line is longer than displayed...
    .attr("markerWidth", 15)
    .attr("markerHeight", 9)
    .attr("orient", "auto")
    .append("svg:path")
    .attr("d", "M 0,-3 L 10,-0.5 L 10,0.5 L0,3")
    .style("fill", "#666")

  initContainerDimensionsAndPositions()
  initEvents()

  state.jsErrors.foreach { _ => d3State.simulation.stop() }

  //TODO: react on size of div
  // https://marcj.github.io/css-element-queries/
  events.window.onResize.foreach(_ => recalculateBoundsAndZoom())

  def recalculateBoundsAndZoom(): Unit = {
    import Math._
    // val padding = 15
    time("recalculateBoundsAndZoom") {
      val rect = container.node.asInstanceOf[HTMLElement].getBoundingClientRect
      val width = rect.width
      val height = rect.height
      if (width > 0 && height > 0 && rxSimPosts.now.size > 0 && rxSimPosts.now.head.radius > 0) {
        // DevPrintln("    updating bounds and zoom")
        val graph = rxDisplayGraph.now.graph
        // DevPrintln(graph.allParentIds.map(postId => rxPostIdToSimPost.now(postId).containmentArea).toString)
        // DevPrintln(rxSimPosts.now.map(_.collisionArea).toString)
        // val postsArea = graph.toplevelPostIds.map( postId => rxPostIdToSimPost.now(postId).containmentArea ).sum
        val arbitraryFactor = 1.3
        val postsArea = rxSimPosts.now.foldLeft(0.0)((sum,post) => sum + post.collisionBoundingSquareArea) * arbitraryFactor
        val scale = sqrt((width * height) / postsArea)  min 1.5   // scale = sqrt(ratio) because areas grow quadratically
        // DevPrintln(s"    parentsArea: $postsArea, window: ${width * height}")
        // DevPrintln(s"    scale: $scale")

        svg.call(d3State.zoom.transform _, d3.zoomIdentity
          .translate(width / 2, height / 2)
          .scale(scale))

        d3State.forces.meta.rectBound.xOffset = -width / 2 / scale
        d3State.forces.meta.rectBound.yOffset = -height / 2 / scale
        d3State.forces.meta.rectBound.width = width / scale
        d3State.forces.meta.rectBound.height = height / scale
        d3State.forces.meta.gravity.width = width / scale
        d3State.forces.meta.gravity.height = height / scale
        InitialPosition.width = width / scale
        InitialPosition.height = height / scale

        // rxSimPosts.now.foreach { simPost =>
        //   simPost.fixedPos = js.undefined
        // }
        d3State.simulation.alpha(1).restart()
      }
    }
  }

  Rx { rxDisplayGraph(); rxSimPosts(); rxSimConnection(); rxSimContainment(); rxContainmentCluster() }.foreach { _ =>
    val simPosts = rxSimPosts.now
    val simConnection = rxSimConnection.now
    val simRedirectedConnection = rxSimRedirectedConnection.now
    val simContainment = rxSimContainment.now
    val simCollapsedContainment = rxSimCollapsedContainment.now
    val graph = rxDisplayGraph.now.graph

    DevPrintln("    updating graph simulation")

    d3State.simulation.nodes(simPosts) // also sets initial positions if NaN
    d3State.forces.connection.links(simConnection)
    d3State.forces.redirectedConnection.links(simRedirectedConnection)
    d3State.forces.containment.links(simContainment)
    d3State.forces.collapsedContainment.links(simCollapsedContainment)

    d3State.forces.meta.setConnections(rxSimConnection.now)
    d3State.forces.meta.setContainments(rxSimContainment.now)
    d3State.forces.meta.setContainmentClusters(rxContainmentCluster.now)

    draw() // triggers updating node sizes
    // wait for the drawcall and start simulation
    window.requestAnimationFrame{ (_) =>
      recalculateBoundsAndZoom()
      d3State.simulation.alpha(1).restart()
    }
  }

  def updatedNodeSizes(): Unit = {
    DevPrintln("    updating node sizes")
    d3State.forces.connection.initialize(rxSimPosts.now)
    d3State.forces.redirectedConnection.initialize(rxSimPosts.now)
    d3State.forces.containment.initialize(rxSimPosts.now)
    d3State.forces.collapsedContainment.initialize(rxSimPosts.now)
    rxContainmentCluster.now.foreach(_.recalculateConvexHull())

    calculateRecursiveContainmentRadii()
    d3State.forces.meta.updatedNodeSizes()
    recalculateBoundsAndZoom()
  }

  def calculateRecursiveContainmentRadii(): Unit = {
    DevPrintln("       calculateRecursiveContainmentRadii")
    def circleAreaToRadius(a: Double) = Math.sqrt(a / Math.PI) // a = PI*r^2 solved by r
    def circleArea(r: Double) = Math.PI * r * r

    val graph = rxDisplayGraph.now.graph
    println("need-----------------")
    for (postId <- graph.postIdsTopologicalSortedByParents) {
      val post = rxPostIdToSimPost.now(postId)
      val children = graph.children(postId)
      if (children.nonEmpty) {
        var childRadiusMax = 0.0
        val childrenArea:Double = children.foldLeft(0.0){ (sum,childId) =>
          val child = rxPostIdToSimPost.now(childId)
          if (child.containmentRadius > childRadiusMax) {
            childRadiusMax = child.containmentRadius
            println(s"max: $childRadiusMax by ${child.content}")
          }
          // sum + child.containmentArea
          val arbitraryFactor = 1.5 // 1.5 is arbitrary to have more space
          sum + child.containmentBoundingSquareArea * arbitraryFactor
        }

        println(s"need children: ${children} ${children.map(rxPostIdToSimPost.now).map(_.containmentArea)}")
        println(s"need sum: $childrenArea")

        val neededArea = post.containmentArea + childrenArea
        println(s"neededArea = ${post.containmentArea} + ${childrenArea} = $neededArea (${children.size} children)")
        val neededRadius = post.containmentRadius + childRadiusMax * 2 // so that the largest node still fits in the bounding radius of the cluster
        post.containmentRadius = circleAreaToRadius(neededArea) max neededRadius
      }
    }
  }

  // def calculateRecursiveContainmentRadii() {
  //   DevPrintln("       calculateRecursiveContainmentRadii")
  //   def circleAreaToRadius(a: Double) = Math.sqrt(a / (2 * Math.PI)) // a = 2*PI*r^2 solved by r
  //   def circleArea(r: Double) = 2 * Math.PI * r * r

  //   val graph = rxDisplayGraph.now.graph
  //   val radii = for (rawPost <- graph.allParents) yield {
  //     val post = rxPostIdToSimPost.now(rawPost.id)
  //     val children = graph.transitiveChildren(post.id)
  //     val childRadiusSum = children.foldLeft(0.0){ (sum,childId) =>
  //       val child = rxPostIdToSimPost.now(childId)
  //       sum + child.collisionRadius
  //     }

  //     val childDiameterSum = childRadiusSum * 2
  //     val childCircumferenceSum = childDiameterSum// * Math.PI
  //     val childDiameterAvg = childDiameterSum / children.size
  //     import Math.{ceil, PI, sqrt, pow}

  //     val d = childDiameterAvg
  //     val D = childCircumferenceSum
  //     val r = post.collisionRadius
  //     // https://www.wolframalpha.com/input/?i=solve+D+%3D+sum(i%3D1,n)((i*d%2Br)*2*pi)+for+n
  //     val n = (-d * PI - 2 * PI * r + sqrt(4 * d * D * PI + pow(-d * PI - 2 * PI * r, 2))) / (2 * d * PI)

  //     println(s"nnn ${post.content}: $n")
  //     val containmentRadius = post.collisionRadius + ceil(n) * childDiameterAvg

  //     post -> containmentRadius
  //   }

  //   for ((post, radius) <- radii) {
  //     post.containmentRadius = radius
  //     post.containmentArea = circleArea(radius)
  //   }
  // }

  private def onPostDrag(): Unit = {
   draggingPostSelection.draw()
  }

  private def onPostDragEnd(): Unit = {
    rxContainmentCluster.now.foreach{ _.recalculateConvexHull() }
    draw()
  }

  private def initEvents(): Unit = {
    d3State.zoom
      .on("zoom", () => zoomed())
      .clickDistance(10) // interpret short drags as clicks
    DevOnly { svg.call(d3State.zoom) } // activate pan + zoom on svg

    svg.on("click", { () =>
      if (state.inner.postCreatorMenus.now.size == 0 && focusedPostId.now == None) {
        val pos = d3State.transform.invert(d3.mouse(svg.node))
        state.inner.postCreatorMenus() = List(PostCreatorMenu(Vec2(pos(0), pos(1))))
      } else {
        // Var.set(
        //   VarTuple(state.postCreatorMenus, Nil),
        //   VarTuple(focusedPostId, None)
        // )
        state.inner.postCreatorMenus() = Nil
        focusedPostId() = None
      }
    })

    val staticForceLayout = false
    var inInitialSimulation = staticForceLayout
    if (inInitialSimulation) container.style("visibility", "hidden")
    d3State.simulation.on("tick", () => if (!inInitialSimulation) draw())
    d3State.simulation.on("end", { () =>
      // rxSimPosts.now.foreach { simPost =>
      //   simPost.fixedPos = simPost.pos
      // }
      if (inInitialSimulation) {
        container.style("visibility", "visible")
        draw()
        inInitialSimulation = false
      }
      DevOnly { println("simulation ended.") }
    })
  }

  private def zoomed(): Unit = {
    import d3State.transform
    val htmlTransformString = s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})"
    svg.selectAll("g").attr("transform", transform.toString)
    html.style("transform", htmlTransformString)
//    postMenuSelection.draw()
//    postCreatorMenu.draw()
  }

  private def draw(): Unit = {
    d3State.forces.meta.updateClusterConvexHulls()

    postSelection.draw()
    connectionLineSelection.draw()
    redirectedConnectionLineSelection.draw()
    connectionElementSelection.draw()
    containmentClusterSelection.draw()
    collapsedContainmentClusterSelection.draw()

    // debug draw:
    postRadiusSelection.draw()
    postCollisionRadiusSelection.draw()
    postContainmentRadiusSelection.draw()
  }

  private def initContainerDimensionsAndPositions(): Unit = {
    container
      .style("position", "relative")
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
      .style("width", "100%")
      .style("height", "100%")

    menuSvg
      .style("position", "absolute")
      .style("width", "100%")
      .style("height", "100%")
      .style("pointer-events", "none")
  }
}
