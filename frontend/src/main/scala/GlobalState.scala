package wust.frontend

import rx._, RxVar._

import wust.api._
import wust.graph._

sealed trait InteractionMode
case class FocusMode(postId: PostId) extends InteractionMode
case class EditMode(postId: PostId) extends InteractionMode
case object DefaultMode extends InteractionMode

sealed trait ViewPage
object ViewPage {
  case object Graph extends ViewPage
  case object Tree extends ViewPage
  case object User extends ViewPage
}

object RouteablePage extends Routeable[ViewPage] {
  override val default = ViewPage.Graph

  override val fromRoute: PartialFunction[String, ViewPage] = {
    case "graph" => ViewPage.Graph
    case "tree" => ViewPage.Tree
    case "user" => ViewPage.User
  }

  override def toRoute(page: ViewPage) = page.toString.toLowerCase
}

class GlobalState(implicit ctx: Ctx.Owner) {
  val currentUser = RxVar(None: Option[User])

  val viewPage: Var[ViewPage] = Var(ViewPage.Graph)

  val rawGraph = RxVar(Graph.empty)
    .map(_.consistent)

  val collapsedPostIds = RxVar[Set[PostId]](Set.empty)

  val currentView = {
    val v = RxVar[View](View())
    RxVar(v.write, Rx {
      v().union(View(collapsed = Selector.IdSet(collapsedPostIds())))
    })
  }

  val graph = {
    RxVar(rawGraph.write, Rx {
      View(currentView(), rawGraph())
    })
  }

  val focusedPostId = {
    val fp = RxVar[Option[PostId]](None)
    RxVar(fp.write, Rx {
      fp().filter(graph().posts.isDefinedAt)
    })
  }

  val editedPostId = {
    val ep = RxVar[Option[PostId]](None)
    RxVar(ep.write, Rx {
      ep().filter(graph().posts.isDefinedAt)
    })
  }

  val mode: Rx[InteractionMode] = Rx {
    (focusedPostId(), editedPostId()) match {
      case (_, Some(id)) => EditMode(id)
      case (Some(id), None) => FocusMode(id)
      case _ => DefaultMode
    }
  }

  DevOnly {
    rawGraph.rx.debug(v => s"rawGraph: ${v.posts.size} posts, ${v.connections.size} connections, ${v.containments.size} containments")
    collapsedPostIds.rx.debug("collapsedPostIds")
    currentView.rx.debug("currentView")
    graph.rx.debug(v => s"graph: ${v.posts.size} posts, ${v.connections.size} connections, ${v.containments.size} containments")
    focusedPostId.rx.debug("focusedPostId")
    editedPostId.rx.debug("editedPostId")
    mode.rx.debug("mode")
  }

  val onAuthEvent: AuthEvent => Unit = _ match {
    case LoggedIn(user) => currentUser := Some(user)
    case LoggedOut => currentUser := None
  }

  val onApiEvent: ApiEvent => Unit = _ match {
    case NewPost(post) => graph.update(_ + post)
    case UpdatedPost(post) => graph.update(_ + post)
    case NewConnection(connects) => graph.update(_ + connects)
    case NewContainment(contains) => graph.update(_ + contains)

    case DeletePost(postId) => graph.update(_ - postId)
    case DeleteConnection(connectsId) => graph.update(_ - connectsId)
    case DeleteContainment(containsId) => graph.update(_ - containsId)
  }
}
