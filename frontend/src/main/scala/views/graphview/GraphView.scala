package wust.frontend.views.graphview

import io.circe.Decoder.state
import wust.util.time.time
import wust.frontend.DevPrintln
import org.scalajs.d3v4._
import org.scalajs.dom
import org.scalajs.dom.window
import org.scalajs.dom.raw.HTMLElement
import vectory._
import wust.frontend.Color._
import wust.frontend.PostCreatorMenu
import wust.frontend.{DevOnly, GlobalState}
import org.scalajs.dom.console
import wust.graph._
import wust.util.Pipe
import outwatch.dom._
import wust.frontend.views.View
import wust.util.outwatchHelpers._

import scala.concurrent.ExecutionContext
import scala.scalajs.js.timers.setTimeout
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

// case class MenuAction(name: String, action: SimPost => Unit, showIf: SimPost => Boolean = _ => true)
// case class DragAction(name: String, action: (SimPost, SimPost) => Unit)

// object KeyImplicits {
//   implicit val SimPostWithKey = new WithKey[SimPost](_.id)
//   implicit val SimConnectionWithKey = new WithKey[SimConnection](c => s"${c.sourceId} ${c.targetId}")
//   implicit val SimRedirectedConnectionWithKey = new WithKey[SimRedirectedConnection](c => s"${c.sourceId} ${c.targetId}")
//   implicit val ContainmentClusterWithKey = new WithKey[ContainmentCluster](_.id)
//   implicit val postCreatorMenuWithKey = new WithKey[PostCreatorMenu](_.toString)
// }

//TODO: remove disableSimulation argument, as it is only relevant for tests. Better solution?
class GraphView(disableSimulation: Boolean = false)(implicit ec: ExecutionContext) extends View {
    override val key = "graph"
    override val displayName = "Mindmap"
    override def apply(state: GlobalState) = {
    div("GraphView")
    // div(
    //   height := "100%",

    //   // div().render sideEffect (new GraphView(state, _, disableSimulation))
    // ).render
  }

  // def postView(post: Post) = div(
    // post.title,
    // cls := "graphpost"
  // )
}

//class GraphView(state: GlobalState, element: dom.html.Element, disableSimulation: Boolean = false)(implicit ec: ExecutionContext, ctx: Ctx.Owner) {
//  val graphState = new GraphState(state)
//  val d3State = new D3State(disableSimulation)
//  val postDrag = new PostDrag(graphState, d3State, onPostDrag _, onPostDragEnd _)
//  import state.{displayGraphWithoutParents => rxDisplayGraph, _}
//  import graphState._

//  // prepare containers where we will append elements depending on the data
//  // order is important
//  import KeyImplicits._
//  val container = d3.select(element)
//  val focusedParentsHeader = container.append(() => div(textAlign.center, marginTop := 10, fontSize := "200%", position.absolute, width := "100%").render)

//  val svg = container.append("svg")
//  val containmentClusterSelection = SelectData.rx(ContainmentClusterSelection, rxContainmentCluster)(svg.append("g"))
//  val collapsedContainmentClusterSelection = SelectData.rx(CollapsedContainmentClusterSelection, rxCollapsedContainmentCluster)(svg.append("g"))
//  val connectionLineSelection = SelectData.rx(ConnectionLineSelection, rxSimConnection)(svg.append("g"))
//  val redirectedConnectionLineSelection = SelectData.rx(RedirectedConnectionLineSelection, rxSimRedirectedConnection)(svg.append("g"))
//  // useful for simulation debugging: (also uncomment in draw())
//  val postRadiusSelection = SelectData.rx(new PostRadiusSelection(graphState, d3State), rxSimPosts)(svg.append("g"))
//  val postCollisionRadiusSelection = SelectData.rx(PostCollisionRadiusSelection, rxSimPosts)(svg.append("g"))
//  val postContainmentRadiusSelection = SelectData.rx(PostContainmentRadiusSelection, rxSimPosts)(svg.append("g"))

//  val html = container.append("div")
//  val connectionElementSelection = SelectData.rx(new ConnectionElementSelection(graphState), rxSimConnection)(html.append("div"))
//  val rawPostSelection = new PostSelection(graphState, d3State, postDrag, updatedNodeSizes _)
//  val postSelection = SelectData.rx(rawPostSelection, rxSimPosts)(html.append("div"))
//  val draggingPostSelection = SelectData.rxDraw(DraggingPostSelection, postDrag.draggingPosts)(html.append("div")) //TODO: place above ring menu?

//  val postMenuLayer = container.append("div")
//  val postMenuSelection = SelectData.rxDraw(new PostMenuSelection(graphState, d3State), rxFocusedSimPost.map(_.toJSArray))(postMenuLayer.append("div"))
//  val postCreatorMenu = SelectData.rxDraw(new CreatePostMenuSelection(graphState, d3State), postCreatorMenus.map(_.toJSArray))(postMenuLayer.append("div"))

//  val menuSvg = container.append("svg")
//  val dragMenuLayer = menuSvg.append("g")
//  val dragMenuSelection = SelectData.rxDraw(new DragMenuSelection(postDrag.dragActions, d3State), postDrag.closestPosts)(dragMenuLayer.append("g"))

//  val controls = {
//    val iconButton = button(width := "2.5em", padding := "5px 10px")
//    container.append(() => div(
//      position.absolute, left := 5, top := 100,
//      iconButton("⟳", title := "automatic layout",
//        onmousedown := { () =>
//          rxSimPosts.now.foreach { simPost =>
//            simPost.fixedPos = js.undefined
//          }
//          d3State.simulation.alpha(1).alphaTarget(1).restart()
//        },
//        onmouseup := { () =>
//          d3State.simulation.alphaTarget(0)
//        }), br(),
//      DevOnly {
//        div(
//          button("tick", onclick := { () =>
//            rxSimPosts.now.foreach { simPost =>
//              simPost.fixedPos = js.undefined
//            }
//            d3State.simulation.tick()
//            draw()
//          }), br(),
//          button("stop", onclick := { () =>
//            d3State.simulation.stop()
//            draw()
//          }), br(),
//          button("draw", onclick := { () =>
//            rxSimPosts.now.foreach { simPost =>
//              simPost.fixedPos = js.undefined
//            }
//            draw()
//          }), br(),
//          iconButton("+", title := "zoom in", onclick := { () =>
//            svg.call(d3State.zoom.scaleBy _, 1.2) //TODO: transition for smooth animation, zoomfactor in global constant
//          }), br(),
//          iconButton("0", title := "reset zoom", onclick := { () =>
//            recalculateBoundsAndZoom()
//          }), br(),
//          iconButton("-", title := "zoom out", onclick := { () =>
//            svg.call(d3State.zoom.scaleBy _, 1 / 1.2) //TODO: transition for smooth animation, zoomfactor in global constant
//          })
//        )
//      }
//    ).render)
//  }

//  // Arrows
//  svg.append("svg:defs").append("svg:marker")
//    .attr("id", "graph_arrow")
//    .attr("viewBox", "0 -3 10 6") // x y w h
//    .attr("refX", 35) // This is a workaround. The line is longer than displayed...
//    .attr("markerWidth", 15)
//    .attr("markerHeight", 9)
//    .attr("orient", "auto")
//    .append("svg:path")
//    .attr("d", "M 0,-3 L 10,-0.5 L 10,0.5 L0,3")
//    .style("fill", "#666")

//  initContainerDimensionsAndPositions()
//  initEvents()

//  setTimeout(100) { recalculateBoundsAndZoom() }

//  state.jsError.foreach { _ => d3State.simulation.stop() }

//  // set the background and headings according to focused parents
//  Rx {
//    val focusedParentIds = state.graphSelection().parentIds
//    val parents = focusedParentIds.map(state.rawGraph().postsById)
//    val parentTitles = parents.map(_.title).mkString(", ")
//    focusedParentsHeader.text(parentTitles)

//    val mixedDirectParentColors = mixColors(focusedParentIds.map(baseColor))
//    container
//      .style("background-color", mixColors(List(mixedDirectParentColors, d3.lab("#FFFFFF"), d3.lab("#FFFFFF"))).toString)
//  }

//  val windowDimensions = Var(Vec2(window.innerWidth, window.innerHeight))
//  window.addEventListener("resize", { _: dom.Event =>
//    windowDimensions() = Vec2(window.innerWidth, window.innerHeight)
//  })

//  Rx {
//    windowDimensions();
//    recalculateBoundsAndZoom()
//  }

//  def recalculateBoundsAndZoom() {
//    import Math._
//    //TODO: react on size of div
//    // https://marcj.github.io/css-element-queries/
//    // val padding = 15
//    time("recalculateBoundsAndZoom") {
//      val rect = container.node.asInstanceOf[HTMLElement].getBoundingClientRect
//      val width = rect.width
//      val height = rect.height
//      if (width > 0 && height > 0 && rxSimPosts.now.size > 0 && rxSimPosts.now.head.radius > 0) {
//        // DevPrintln("    updating bounds and zoom")
//        val graph = rxDisplayGraph.now.graph
//        // DevPrintln(graph.allParentIds.map(postId => rxPostIdToSimPost.now(postId).containmentArea).toString)
//        // DevPrintln(rxSimPosts.now.map(_.collisionArea).toString)
//        // val postsArea = graph.toplevelPostIds.map( postId => rxPostIdToSimPost.now(postId).containmentArea ).sum
//        val arbitraryFactor = 1.3
//        val postsArea = rxSimPosts.now.foldLeft(0.0)((sum,post) => sum + post.collisionBoundingSquareArea) * arbitraryFactor
//        val scale = sqrt((width * height) / postsArea)  min 1.5   // scale = sqrt(ratio) because areas grow quadratically
//        // DevPrintln(s"    parentsArea: $postsArea, window: ${width * height}")
//        // DevPrintln(s"    scale: $scale")

//        svg.call(d3State.zoom.transform _, d3.zoomIdentity
//          .translate(width / 2, height / 2)
//          .scale(scale))

//        d3State.forces.meta.rectBound.xOffset = -width / 2 / scale
//        d3State.forces.meta.rectBound.yOffset = -height / 2 / scale
//        d3State.forces.meta.rectBound.width = width / scale
//        d3State.forces.meta.rectBound.height = height / scale
//        d3State.forces.meta.gravity.width = width / scale
//        d3State.forces.meta.gravity.height = height / scale
//        InitialPosition.width = width / scale
//        InitialPosition.height = height / scale

//        // rxSimPosts.now.foreach { simPost =>
//        //   simPost.fixedPos = js.undefined
//        // }
//        d3State.simulation.alpha(1).restart()
//      }
//    }
//  }

//  Rx { rxDisplayGraph(); rxSimPosts(); rxSimConnection(); rxSimContainment(); rxContainmentCluster() }.foreach { _ =>
//    val simPosts = rxSimPosts.now
//    val simConnection = rxSimConnection.now
//    val simRedirectedConnection = rxSimRedirectedConnection.now
//    val simContainment = rxSimContainment.now
//    val simCollapsedContainment = rxSimCollapsedContainment.now
//    val graph = rxDisplayGraph.now.graph

//    DevPrintln("    updating graph simulation")

//    d3State.simulation.nodes(simPosts) // also sets initial positions if NaN
//    d3State.forces.connection.links(simConnection)
//    d3State.forces.redirectedConnection.links(simRedirectedConnection)
//    d3State.forces.containment.links(simContainment)
//    d3State.forces.collapsedContainment.links(simCollapsedContainment)

//    d3State.forces.meta.setConnections(rxSimConnection.now)
//    d3State.forces.meta.setContainments(rxSimContainment.now)
//    d3State.forces.meta.setContainmentClusters(rxContainmentCluster.now)

//    draw() // triggers updating node sizes
//    // wait for the drawcall and start simulation
//    window.requestAnimationFrame{ (_) =>
//      recalculateBoundsAndZoom()
//      d3State.simulation.alpha(1).restart()
//    }
//  }

//  def updatedNodeSizes() {
//    DevPrintln("    updating node sizes")
//    d3State.forces.connection.initialize(rxSimPosts.now)
//    d3State.forces.redirectedConnection.initialize(rxSimPosts.now)
//    d3State.forces.containment.initialize(rxSimPosts.now)
//    d3State.forces.collapsedContainment.initialize(rxSimPosts.now)
//    rxContainmentCluster.now.foreach(_.recalculateConvexHull())

//    calculateRecursiveContainmentRadii()
//    d3State.forces.meta.updatedNodeSizes()
//    recalculateBoundsAndZoom()
//  }

//  def calculateRecursiveContainmentRadii() {
//    DevPrintln("       calculateRecursiveContainmentRadii")
//    def circleAreaToRadius(a: Double) = Math.sqrt(a / Math.PI) // a = PI*r^2 solved by r
//    def circleArea(r: Double) = Math.PI * r * r

//    val graph = rxDisplayGraph.now.graph
//    println("need-----------------")
//    for (postId <- graph.postIdsTopologicalSortedByParents) {
//      val post = rxPostIdToSimPost.now(postId)
//      val children = graph.children(postId)
//      if (children.nonEmpty) {
//        var childRadiusMax = 0.0
//        val childrenArea:Double = children.foldLeft(0.0){ (sum,childId) =>
//          val child = rxPostIdToSimPost.now(childId)
//          if (child.containmentRadius > childRadiusMax) {
//            childRadiusMax = child.containmentRadius
//            println(s"max: $childRadiusMax by ${child.title}")
//          }
//          // sum + child.containmentArea
//          val arbitraryFactor = 1.5 // 1.5 is arbitrary to have more space
//          sum + child.containmentBoundingSquareArea * arbitraryFactor
//        }

//        println(s"need children: ${children} ${children.map(rxPostIdToSimPost.now).map(_.containmentArea)}")
//        println(s"need sum: $childrenArea")

//        val neededArea = post.containmentArea + childrenArea
//        println(s"neededArea = ${post.containmentArea} + ${childrenArea} = $neededArea (${children.size} children)")
//        val neededRadius = post.containmentRadius + childRadiusMax * 2 // so that the largest node still fits in the bounding radius of the cluster
//        post.containmentRadius = circleAreaToRadius(neededArea) max neededRadius
//      }
//    }
//  }

//  // def calculateRecursiveContainmentRadii() {
//  //   DevPrintln("       calculateRecursiveContainmentRadii")
//  //   def circleAreaToRadius(a: Double) = Math.sqrt(a / (2 * Math.PI)) // a = 2*PI*r^2 solved by r
//  //   def circleArea(r: Double) = 2 * Math.PI * r * r

//  //   val graph = rxDisplayGraph.now.graph
//  //   val radii = for (rawPost <- graph.allParents) yield {
//  //     val post = rxPostIdToSimPost.now(rawPost.id)
//  //     val children = graph.transitiveChildren(post.id)
//  //     val childRadiusSum = children.foldLeft(0.0){ (sum,childId) =>
//  //       val child = rxPostIdToSimPost.now(childId)
//  //       sum + child.collisionRadius
//  //     }

//  //     val childDiameterSum = childRadiusSum * 2
//  //     val childCircumferenceSum = childDiameterSum// * Math.PI
//  //     val childDiameterAvg = childDiameterSum / children.size
//  //     import Math.{ceil, PI, sqrt, pow}

//  //     val d = childDiameterAvg
//  //     val D = childCircumferenceSum
//  //     val r = post.collisionRadius
//  //     // https://www.wolframalpha.com/input/?i=solve+D+%3D+sum(i%3D1,n)((i*d%2Br)*2*pi)+for+n
//  //     val n = (-d * PI - 2 * PI * r + sqrt(4 * d * D * PI + pow(-d * PI - 2 * PI * r, 2))) / (2 * d * PI)

//  //     println(s"nnn ${post.title}: $n")
//  //     val containmentRadius = post.collisionRadius + ceil(n) * childDiameterAvg

//  //     post -> containmentRadius
//  //   }

//  //   for ((post, radius) <- radii) {
//  //     post.containmentRadius = radius
//  //     post.containmentArea = circleArea(radius)
//  //   }
//  // }

//  private def onPostDrag() {
//    draggingPostSelection.draw()
//  }

//  private def onPostDragEnd() {
//    rxContainmentCluster.now.foreach{ _.recalculateConvexHull() }
//    draw()
//  }

//  private def initEvents(): Unit = {
//    d3State.zoom
//      .on("zoom", () => zoomed())
//      .clickDistance(10) // interpret short drags as clicks
//    DevOnly { svg.call(d3State.zoom) } // activate pan + zoom on svg

//    svg.on("click", { () =>
//      if (state.postCreatorMenus.now.size == 0 && focusedPostId.now == None) {
//        val pos = d3State.transform.invert(d3.mouse(svg.node))
//        state.postCreatorMenus() = List(PostCreatorMenu(Vec2(pos(0), pos(1))))
//      } else {
//        // Var.set(
//        //   VarTuple(state.postCreatorMenus, Nil),
//        //   VarTuple(focusedPostId, None)
//        // )
//        state.postCreatorMenus() = Nil
//        focusedPostId() = None
//      }
//    })

//    val staticForceLayout = false
//    var inInitialSimulation = staticForceLayout
//    if (inInitialSimulation) container.style("visibility", "hidden")
//    d3State.simulation.on("tick", () => if (!inInitialSimulation) draw())
//    d3State.simulation.on("end", { () =>
//      // rxSimPosts.now.foreach { simPost =>
//      //   simPost.fixedPos = simPost.pos
//      // }
//      if (inInitialSimulation) {
//        container.style("visibility", "visible")
//        draw()
//        inInitialSimulation = false
//      }
//      DevOnly { println("simulation ended.") }
//    })
//  }

//  private def zoomed() {
//    import d3State.transform
//    val htmlTransformString = s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})"
//    svg.selectAll("g").attr("transform", transform.toString)
//    html.style("transform", htmlTransformString)
//    postMenuSelection.draw()
//    postCreatorMenu.draw()
//  }

//  private def draw() {
//    d3State.forces.meta.updateClusterConvexHulls()

//    postSelection.draw()
//    connectionLineSelection.draw()
//    redirectedConnectionLineSelection.draw()
//    connectionElementSelection.draw()
//    containmentClusterSelection.draw()
//    collapsedContainmentClusterSelection.draw()

//    // debug draw:
//    postRadiusSelection.draw()
//    postCollisionRadiusSelection.draw()
//    postContainmentRadiusSelection.draw()
//  }

//  private def initContainerDimensionsAndPositions() {
//    container
//      .style("position", "relative")
//      .style("width", "100%")
//      .style("height", "100%")
//      .style("overflow", "hidden")

//    svg
//      .style("position", "absolute")
//      .style("width", "100%")
//      .style("height", "100%")

//    html
//      .style("position", "absolute")
//      .style("pointer-events", "none") // pass through to svg (e.g. zoom)
//      .style("transform-origin", "top left") // same as svg default
//      .style("width", "100%")
//      .style("height", "100%")

//    menuSvg
//      .style("position", "absolute")
//      .style("width", "100%")
//      .style("height", "100%")
//      .style("pointer-events", "none")
//  }
//}
