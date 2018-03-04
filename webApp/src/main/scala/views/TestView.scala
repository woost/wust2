// package wust.webApp.views

// import wust.webApp._
// import wust.utilWeb._
// import wust.graph._

// import outwatch.dom._
// import outwatch.dom.dsl._
// import outwatch.Sink
// import wust.utilWeb.outwatchHelpers._

// object TestView extends View {
//   override val key = "test"
//   override val displayName = "Test"
//   override def apply(state: GlobalState) = {
//     import state._
//     val graph = displayGraphWithoutParents.map(_.graph)
//     graph.foreach(g => println(s"TestView: got graph update. If this is shown more than once per graph update, this is a leak."))
//     component(graph, page)
//   }

//   def component(graph:Observable[Graph], graphSelection:Sink[Page]) = {
//     div(
//       padding := "20px",
//       h1("Test View"),
//       children <-- sortedPostItems(graph, graphSelection)
//     )
//   }

//   def sortedPostItems(graph:Observable[Graph], graphSelection:Sink[Page]): Observable[Seq[VNode]] = graph.map { graph =>
//     val sortedPosts = HierarchicalTopologicalSort(graph.postIds, successors = graph.successorsWithoutParent, children = graph.children)

//     sortedPosts.map { postId =>
//       val post = graph.postsById(postId)
//       postItem(post, graphSelection)
//     }
//   }

//   def postItem(post: Post, graphSelection: Sink[Page]) = {
//     div(
//       post.content,

//       minHeight := "12px",
//       border := "solid 1px",
//       cursor.pointer,
//       onClick(Page.Union(Set(post.id))) --> graphSelection
//     )
//   }
// }
