// package wust.utilWeb.views

// import org.scalatest._
// import org.scalatest.prop._
// import rx.Ctx.Owner.Unsafe._
// import wust.utilWeb._
// import wust.utilWeb.views.graphview.GraphView
// import wust.ids._
// import wust.graph._
// import scala.concurrent.ExecutionContext.Implicits.global

// import scala.collection.immutable._

// class ViewsExamples extends Tables {
//   DevOnly.enabled = false
//   def views = Table(
//     "views",
//     // ("Tree", TreeView(_)),
//     ("Article", ArticleView(_)),
//     ("Chat", ChatView(_)),
//     // ("Graph", GraphView(_: GlobalState, disableSimulation = true)),
//     ("Main", MainView(_: GlobalState))
//   )
// }

// class ViewAntiCrashSpec extends FreeSpec with TableDrivenPropertyChecks with MustMatchers with LocalStorageMock {
//   "focusing post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         state.rawGraph() = Graph(List(Post("heinz", "wust")))
//         view(state).render
//         state.focusedNodeId() = Option("heinz")
//     }
//   }

//   "unfocusing post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         state.rawGraph() = Graph(List(Post("heinz", "wust")))
//         state.focusedNodeId() = Option("heinz")
//         view(state).render
//         state.focusedNodeId() = None
//     }
//   }

//   "adding post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         state.rawGraph() = Graph()
//         view(state).render
//         state.rawGraph.updatef(_ + Post("heinz", "wurst"))
//     }
//   }

//   "updating post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         state.rawGraph() = Graph(List(Post("heinz", "wust")))
//         view(state).render
//         state.rawGraph.updatef(_ + Post("heinz", "wurst"))
//     }
//   }

//   "deleting post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         state.rawGraph() = Graph(List(Post("heinz", "bewustsein")))
//         view(state).render
//         state.rawGraph.updatef(_ - NodeId("heinz"))
//     }
//   }

//   "deleting connected source post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")), List(Connection("heinz", "kalla")))
//         view(state).render
//         state.rawGraph.updatef(_ - NodeId("heinz"))
//     }
//   }

//   "deleting connected target post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")), List(Connection("heinz", "kalla")))
//         view(state).render
//         state.rawGraph.updatef(_ - NodeId("kalla"))
//     }
//   }

//   "deleting containment parent post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")), containments = List(Containment("heinz", "kalla")))
//         view(state).render
//         state.rawGraph.updatef(_ - NodeId("heinz"))
//     }
//   }

//   "deleting containment child post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")), containments = List(Containment(NodeId("heinz"), NodeId("kalla"))))
//         view(state).render
//         state.rawGraph.updatef(_ - NodeId("kalla"))
//     }
//   }

//   "deleting focused post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         state.rawGraph() = Graph(List(Post("heinz", "bewustlos")))
//         state.focusedNodeId() = Option("heinz")
//         view(state).render
//         state.rawGraph.updatef(_ - NodeId("heinz"))
//     }
//   }

//   "updating focused post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         state.rawGraph() = Graph(List(Post("heinz", "bewustlos")))
//         state.focusedNodeId() = Option("heinz")
//         view(state).render
//         state.rawGraph.updatef(_ + Post("heinz", "wurst"))
//     }
//   }

//   "adding connection" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")))
//         view(state).render
//         state.rawGraph.updatef(_ + Connection(NodeId("heinz"), NodeId("kalla")))
//     }
//   }

//   "deleting connection" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         val conn = Connection(NodeId("heinz"), NodeId("kalla"))
//         state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")), List(conn))
//         view(state).render
//         state.rawGraph.updatef(_ - conn)
//     }
//   }

//   "deleting containment" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")), containments = List(Containment(NodeId("heinz"), NodeId("kalla"))))
//         view(state).render
//         state.rawGraph.updatef(_ - Containment("heinz", "kalla"))
//     }
//   }

//   "adding containment" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = new GlobalState
//         state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")))
//         view(state).render
//         state.rawGraph.updatef(_ + Containment(NodeId("heinz"), NodeId("kalla")))
//     }
//   }
// }
