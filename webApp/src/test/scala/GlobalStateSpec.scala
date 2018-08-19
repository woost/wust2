//package wust.webApp

//TODO
// class GlobalStateSpec extends FreeSpec with MustMatchers with LocalStorageMock {
//   //TODO: test the number of rx updates

//   "raw graph" - {
//     "be not consistent" in {
//       val state = new GlobalState
//       val graph = Graph(
//         posts = List(Post("grenom", "title")),
//         connections = List(Connection("grenom", "zeilinda")),
//         containments = List(Containment("grenom", "telw"))
//       )

//       state.rawGraph() = graph

//       state.rawGraph.now mustEqual graph
//     }
//   }

//   "graph" - {
//     "be complete with empty view" in {
//       val state = new GlobalState
//       state.rawGraph() = Graph(
//         posts = List(Post("grenom", "title"), Post("zeilinda", "title2")),
//         connections = List(Connection("grenom", "zeilinda"))
//       )

//       state.rawGraph.now mustEqual state.displayGraphWithoutParents.now.graph
//     }

//     "be consistent with focused" in {
//       val state = new GlobalState
//       state.focusedNodeId() = Option("grenom")
//       state.focusedNodeId.now mustEqual None

//       state.rawGraph() = Graph(posts = List(Post("grenom", "title")))
//       state.focusedNodeId.now mustEqual Option(NodeId("grenom"))

//       state.rawGraph() = Graph.empty
//       state.focusedNodeId.now mustEqual None
//     }

//     "have view" in {
//       val state = new GlobalState
//       state.rawGraph() = Graph(
//         posts = List(Post("grenom", "title"), Post("zeilinda", "title2")),
//         connections = Nil,
//         containments = List(Containment("grenom", "zeilinda"))
//       )
//       state.currentView() = Perspective(collapsed = Selector.IdSet(Set("grenom")))

//       state.displayGraphWithoutParents.now.graph mustEqual Graph(posts = List(Post("grenom", "title")))
//     }

//   }

//   "view" - {
//     "be consistent with collapsed" in {
//       val state = new GlobalState
//       state.collapsedNodeIds() = Set("grenom")
//       state.currentView.now.collapsed(NodeId("grenom")) mustEqual Perspective().union(Perspective(collapsed = Selector.IdSet(Set("grenom")))).collapsed(NodeId("grenom"))

//       state.collapsedNodeIds() = Set.empty
//       state.currentView.now.collapsed.apply(NodeId("grenom")) mustEqual Perspective().union(Perspective(collapsed = Selector.IdSet(Set.empty))).collapsed(NodeId("grenom"))
//     }
//   }
// }
