// package wust.webApp.views
//
// import monix.reactive.Observable
// import org.scalatest._
// import org.scalatest.prop._
// import rx.Ctx.Owner.Unsafe._
// import rx.Var
// import wust.api.Authentication
// import wust.ids._
// import wust.graph._
// import wust.sdk.EventProcessor
// import wust.webApp.{DevOnly, LocalStorageMock}
// import wust.webApp.state.{GlobalState, View, ViewConfig}
// import wust.webApp.outwatchHelpers._
//
// import scala.collection.immutable._
// import scala.concurrent.Future
//
// class ViewsExamples extends Tables {
//   DevOnly.enabled = false
//   def views = Table(
//     "views",
//     ("Kanban", View.Kanban),
//     ("Chat", View.Chat),
//     ("Graph", View.Graph)
//   )
// }
//
// class ViewAntiCrashSpec extends FreeSpec with TableDrivenPropertyChecks with MustMatchers with LocalStorageMock {
//   def freshState = new GlobalState(
//     appUpdateIsAvailable = Observable.empty,
//     eventProcessor = EventProcessor.apply(
//       eventStream = Observable.empty,
//       enrichChanges = (c, _) => c,
//       sendChange = _ => Future.successful(true),
//       initialAuth = Authentication.Assumed.fresh),
//     sidebarOpen = Var(true),
//     viewConfig = Var(ViewConfig.default),
//     isOnline = Var(true))
//
//   "focusing post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = freshState
//         val node: Node = Node.Content(NodeData.PlainText("Moin"))
//         state.graph() = Graph(List(node))
//
//         val vnode = ViewRender(view, state)
//         vnode.render
//         state.page() = Page.apply(node.id)
//     }
//   }
//
//   "unfocusing post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = freshState
//         val node: Node = Node.Content(NodeData.PlainText("Moin"))
//         state.graph() = Graph(List(node))
//         state.page() = Page(node.id)
//
//         val vnode = ViewRender(view, state)
//         vnode.render
//         state.page() = Page.empty
//     }
//   }
//
//   "adding post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = freshState
//         val node: Node = Node.Content(NodeData.PlainText("Moin"))
//         state.graph() = Graph.empty
//         state.page() = Page.apply(node.id)
//
//         val vnode = ViewRender(view, state)
//         vnode.render
//         state.graph() = state.graph.now + node
//     }
//   }
//
//   "updating post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = freshState
//         val node: Node = Node.Content(NodeData.PlainText("Moin"))
//         state.graph() = Graph(List(node))
//         state.page() = Page.apply(node.id)
//
//         val vnode = ViewRender(view, state)
//         vnode.render
//         val newNode = Node.Content(node.id, NodeData.PlainText("Bye"))
//         state.graph() = state.graph.now + newNode
//     }
//   }
//
//   "deleting post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = freshState
//         val node: Node = Node.Content(NodeData.PlainText("Moin"))
//         state.graph() = Graph(List(node))
//         state.page() = Page.apply(node.id)
//
//         val vnode = ViewRender(view, state)
//         vnode.render
//         state.graph() = state.graph.now - node.id
//     }
//   }
//
//   "deleting connected source post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = freshState
//         val node1: Node = Node.Content(NodeData.PlainText("Moin"))
//         val node2: Node = Node.Content(NodeData.PlainText("Byte"))
//         state.graph() = Graph(List(node1, node2), List(Edge.Parent(node1.id, node2.id)))
//         state.page() = Page.apply(Seq(node1.id,node2.id))
//
//         val vnode = ViewRender(view, state)
//         vnode.render
//         state.graph() = state.graph.now - node1.id
//     }
//   }
//
//   "deleting connected target post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = freshState
//         val node1: Node = Node.Content(NodeData.PlainText("Moin"))
//         val node2: Node = Node.Content(NodeData.PlainText("Byte"))
//         state.graph() = Graph(List(node1, node2), List(Edge.Parent(node1.id, node2.id)))
//         state.page() = Page.apply(Seq(node1.id,node2.id))
//
//         val vnode = ViewRender(view, state)
//         vnode.render
//         state.graph() = state.graph.now - node2.id
//     }
//   }
//
//   "adding connection" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = freshState
//         val node1: Node = Node.Content(NodeData.PlainText("Moin"))
//         val node2: Node = Node.Content(NodeData.PlainText("Byte"))
//         state.graph() = Graph(List(node1, node2))
//         state.page() = Page.apply(Seq(node1.id,node2.id))
//
//         val vnode = ViewRender(view, state)
//         vnode.render
//         state.graph() = state.graph.now + Edge.Parent(node1.id, node2.id)
//     }
//   }
//
//   "deleting connection" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val state = freshState
//         val node1: Node = Node.Content(NodeData.PlainText("Moin"))
//         val node2: Node = Node.Content(NodeData.PlainText("Byte"))
//         val edge: Edge = Edge.Parent(node1.id, node2.id)
//         state.graph() = Graph(List(node1, node2), List(edge))
//         state.page() = Page.apply(Seq(node1.id,node2.id))
//
//         val vnode = ViewRender(view, state)
//         vnode.render
//         state.graph() = state.graph.now - edge
//     }
//   }
// }
