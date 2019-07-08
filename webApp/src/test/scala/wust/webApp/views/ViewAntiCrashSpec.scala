// package wust.webApp.views

// import monix.reactive.Observable
// import monix.reactive.subjects.PublishSubject
// import org.scalatest._
// import org.scalatest.prop._
// import rx.Ctx.Owner.Unsafe._
// import rx.Var
// import wust.api.{ApiEvent, Authentication, AuthUser}
// import wust.ids._
// import wust.graph._
// import wust.sdk.EventProcessor
// import wust.webApp.{DevOnly, LocalStorageMock, RequestAnimationFrameMock}
// import wust.webApp.state.{GlobalState, View, ViewConfig}
// import webUtil.outwatchHelpers._

// import scala.collection.immutable._
// import scala.concurrent.Future

// class ViewsExamples extends Tables {
//   DevOnly.enabled = false
//   def views = Table(
//     "views",
//     ("Kanban", View.Kanban),
//     ("Chat", View.Chat),
//     ("Graph", View.Graph)
//   )
// }

// class ViewAntiCrashSpec extends FreeSpec with TableDrivenPropertyChecks with MustMatchers with LocalStorageMock with RequestAnimationFrameMock {

//   def freshNodeId(i:Int) = NodeId(Cuid(i, i))

//   def freshState: (PublishSubject[Seq[ApiEvent]], GlobalState) = {
//     val eventStream = PublishSubject[Seq[ApiEvent]]

//     val eventProcessor = EventProcessor(
//       eventStream = eventStream,
//       enrichChanges = (c, _) => c,
//       sendChange = _ => Future.successful(true),
//       initialAuth = Authentication.Assumed(
//         AuthUser.Assumed(UserId(freshNodeId(1)), freshNodeId(2))))

//     val state = new GlobalState(
//       appUpdateIsAvailable = Observable.empty,
//       eventProcessor = eventProcessor,
//       sidebarOpen = Var(true),
//       viewConfig = Var(ViewConfig.default),
//       isOnline = Var(true))

//     (eventStream)
//   }

//   "focusing post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val (eventStream) = freshState
//         val node: Node = Node.Content(NodeData.PlainText("Moin"))
//         eventStream.onNext(ApiEvent.NewGraphChanges(GraphChanges.addNode(node)) :: Nil)

//         val vnode = ViewRender(view)
//         vnode.render
//         GlobalState.page() = Page.apply(node.id)
//     }
//   }

//   "unfocusing post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val (eventStream) = freshState
//         val node: Node = Node.Content(NodeData.PlainText("Moin"))
//         eventStream.onNext(ApiEvent.NewGraphChanges(GraphChanges.addNode(node)) :: Nil)
//         GlobalState.page() = Page(node.id)

//         val vnode = ViewRender(view)
//         vnode.render
//         GlobalState.page() = Page.empty
//     }
//   }

//   "adding post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val (eventStream) = freshState
//         val node: Node = Node.Content(NodeData.PlainText("Moin"))
//         GlobalState.page() = Page.apply(node.id)

//         val vnode = ViewRender(view)
//         vnode.render
//         eventStream.onNext(ApiEvent.NewGraphChanges(GraphChanges.addNode(node)) :: Nil)
//     }
//   }

//   "updating post" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val (eventStream) = freshState
//         val node: Node = Node.Content(NodeData.PlainText("Moin"))
//         eventStream.onNext(ApiEvent.NewGraphChanges(GraphChanges.addNode(node)) :: Nil)
//         GlobalState.page() = Page.apply(node.id)

//         val vnode = ViewRender(view)
//         vnode.render
//         val newNode = Node.Content(node.id, NodeData.PlainText("Bye"))
//         eventStream.onNext(ApiEvent.NewGraphChanges(GraphChanges.addNode(newNode)) :: Nil)
//     }
//   }

//   "adding connection" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val (eventStream) = freshState
//         val node1: Node = Node.Content(NodeData.PlainText("Moin"))
//         val node2: Node = Node.Content(NodeData.PlainText("Byte"))
//         eventStream.onNext(ApiEvent.NewGraphChanges(GraphChanges(addNodes = Set(node1, node2))) :: Nil)
//         GlobalState.page() = Page.apply(Seq(node1.id,node2.id))

//         val vnode = ViewRender(view)
//         vnode.render
//         val edge = Edge.Parent(node1.id, node2.id)
//         eventStream.onNext(ApiEvent.NewGraphChanges(GraphChanges(addEdges = Set(edge))) :: Nil)
//     }
//   }

//   "deleting connection" in new ViewsExamples {
//     forAll(views) {
//       case (_, view) =>
//         val (eventStream) = freshState
//         val node1: Node = Node.Content(NodeData.PlainText("Moin"))
//         val node2: Node = Node.Content(NodeData.PlainText("Byte"))
//         val edge: Edge = Edge.Parent(node1.id, node2.id)
//         eventStream.onNext(ApiEvent.NewGraphChanges(GraphChanges(addNodes = Set(node1, node2), addEdges = Set(edge))) :: Nil)
//         GlobalState.page() = Page.apply(Seq(node1.id,node2.id))

//         val vnode = ViewRender(view)
//         vnode.render
//         eventStream.onNext(ApiEvent.NewGraphChanges(GraphChanges(delEdges = Set(edge))) :: Nil)
//     }
//   }
// }
