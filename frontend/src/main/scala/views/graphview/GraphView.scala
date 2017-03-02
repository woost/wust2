// package frontend.views.graphview

// import scalajs.js
// import org.scalajs.dom
// import scala.xml.Node
// import js.JSConverters._
// import org.scalajs.d3v4._
// import rx._
// import math._

// import frontend.GlobalState
// import graph._
// import frontend.Color._

// case class MenuAction(symbol: String, action: (SimPost, Simulation[SimPost]) => Unit)
// case class DropAction(symbol: String, action: (SimPost, SimPost) => Unit)

// object KeyImplicits {
//   implicit val SimPostWithKey = new WithKey[SimPost](_.id)
//   implicit val SimConnectsWithKey = new WithKey[SimConnects](_.id)
//   implicit val ContainmentClusterWithKey = new WithKey[ContainmentCluster](_.id)
// }

// class GraphView(state: GlobalState, element: dom.html.Element) {
//   val graphState = new GraphState(state)
//   val d3State = new D3State
//   val postDrag = new PostDrag(graphState, d3State, onPostDrag)
//   import state._

//   // prepare containers where we will append elements depending on the data
//   // order is important
//   import KeyImplicits._
//   val container = d3.select(element)
//   val svg = container.append("svg")
//   val containmentHullSelection = SelectData.rx(ContainmentHullSelection, graphState.rxContainmentCluster)(svg.append("g"))
//   val connectionLineSelection = SelectData.rx(ConnectionLineSelection, graphState.rxSimConnects)(svg.append("g"))

//   val html = container.append("div")
//   val connectionElementSelection = SelectData.rx(ConnectionElementSelection, graphState.rxSimConnects)(html.append("div"))
//   val postSelection = SelectData.rx(new PostSelection(graphState, postDrag), graphState.rxSimPosts)(html.append("div"))
//   val draggingPostSelection = SelectData.rxDraw(DraggingPostSelection, postDrag.draggingPosts)(html.append("div")) //TODO: place above ring menu?

//   val menuSvg = container.append("svg")
//   val postMenuLayer = menuSvg.append("g")
//   val postMenuSelection = SelectData.rxDraw(new PostMenuSelection(graphState, d3State), graphState.focusedPost.map(_.toJSArray))(postMenuLayer.append("g"))
//   val dropMenuLayer = menuSvg.append("g")
//   val dropMenuSelection = SelectData.rxDraw(DropMenuSelection, postDrag.closestPosts)(dropMenuLayer.append("g"))

//   initContainerDimensionsAndPositions()
//   initEvents()
//   val updateCancel = graph.foreach(update)

//   private def cancel() {
//     updateCancel.cancel()
//   }

//   private def onPostDrag() {
//     draggingPostSelection.draw()
//   }

//   private def initEvents(): Unit = {
//     svg.call(d3.zoom().on("zoom", zoomed _))
//     svg.on("click", () => focusedPostId := None)
//     d3State.simulation.on("tick", draw _)
//     //TODO: currently produces NaNs: graphState.rxSimConnects.foreach { data => d3State.forces.connection.links = data }
//   }

//   private def zoomed() {
//     import d3State._
//     transform = d3.event.asInstanceOf[ZoomEvent].transform
//     svg.selectAll("g").attr("transform", transform.toString)
//     html.style("transform", s"translate(${transform.x}px,${transform.y}px) scale(${transform.k})")
//     postMenuLayer.attr("transform", transform.toString)
//     dropMenuLayer.attr("transform", transform.toString)
//   }

//   private def draw() {
//     postSelection.draw()
//     postMenuSelection.draw()
//     connectionLineSelection.draw()
//     connectionElementSelection.draw()
//     containmentHullSelection.draw()
//   }

//   private def initContainerDimensionsAndPositions() {
//     container
//       .style("position", "absolute")
//       .style("top", "0")
//       .style("left", "0")
//       .style("z-index", "-1")
//       .style("width", "100%")
//       .style("height", "100%")
//       .style("overflow", "hidden")

//     svg
//       .style("position", "absolute")
//       .style("width", "100%")
//       .style("height", "100%")

//     html
//       .style("position", "absolute")
//       .style("pointer-events", "none") // pass through to svg (e.g. zoom)
//       .style("transform-origin", "top left") // same as svg default
//       .style("width", "100%")
//       .style("height", "100%")

//     menuSvg
//       .style("position", "absolute")
//       .style("width", "100%")
//       .style("height", "100%")
//       .style("pointer-events", "none")
//   }

//   private def update(newGraph: Graph) {
//     import d3State._, graphState._

//     //TODO: this can be removed after implementing link force which supports hyperedges
//     forces.connection.strength { (e: SimConnects) =>
//       val targetDeg = e.target match {
//         case p: SimPost => newGraph.fullDegree(p.post.id)
//         case _: SimConnects => 2
//       }
//       1.0 / min(newGraph.fullDegree(e.source.post.id), targetDeg)
//     }

//     forces.containment.strength { (e: SimContains) =>
//       1.0 / min(newGraph.fullDegree(e.source.post.id), newGraph.fullDegree(e.target.post.id))
//     }

//     val containmentData = newGraph.containments.values.map { c =>
//       new SimContains(c, postIdToSimPost.value(c.parentId), postIdToSimPost.value(c.childId))
//     }.toJSArray

//     forces.connection.links(rxSimConnects.value)
//     forces.containment.links(containmentData)
//     d3State.simulation.nodes(graphState.rxSimPosts.value)

//     simulation.alpha(1).restart()
//   }
// }

// object GraphView { thisEnv =>
//   def component(state: GlobalState) = {
//     var view: Option[GraphView] = None
//     def mount(e: dom.html.Element) { view = Some(new GraphView(state, e)) }
//     def unmount(e: dom.html.Element) { view.foreach(_.cancel); view = None }
//     <div mhtml-onmount={ mount _ } mhtml-onunmount={ unmount _ }></div>
//   }
// }
