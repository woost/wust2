package wust.frontend

import rx._, rxext._

import wust.api._
import wust.graph._
import wust.util.Pipe

sealed trait InteractionMode
case class FocusMode(postId: PostId) extends InteractionMode
case class EditMode(postId: PostId) extends InteractionMode
case object DefaultMode extends InteractionMode

sealed trait ViewPage
object ViewPage {
  case object Graph extends ViewPage
  case object Tree extends ViewPage
  case object User extends ViewPage

  val default = ViewPage.Graph

  val fromHash: PartialFunction[String, ViewPage] = {
    case "graph" => ViewPage.Graph
    case "tree" => ViewPage.Tree
    case "user" => ViewPage.User
  }

  def toHash(page: ViewPage) = page.toString.toLowerCase
}

class GlobalState(implicit ctx: Ctx.Owner) {
  val currentUser = RxVar[Option[User]](None)

  val viewPage = UrlRouter.variable
    .projection[ViewPage](_ |> ViewPage.toHash |> (Option(_)), _.flatMap(ViewPage.fromHash.lift).getOrElse(ViewPage.default))

  val rawGraph = RxVar(Graph.empty)
    .map(_.consistent)

  val collapsedPostIds = RxVar[Set[PostId]](Set.empty)

  val currentView = {
    val v = RxVar[Perspective](Perspective())
    RxVar(v.write, Rx {
      v().union(Perspective(collapsed = Selector.IdSet(collapsedPostIds())))
    })
  }

  val displayGraph = {
    RxVar(rawGraph.write, Rx { Perspective(currentView(), rawGraph()) })
  }

  val focusedPostId = {
    val fp = RxVar[Option[PostId]](None)
    RxVar(fp.write, Rx {
      fp().filter(displayGraph().graph.postsById.isDefinedAt)
    })
  }

  val editedPostId = {
    val ep = RxVar[Option[PostId]](None)
    RxVar(ep.write, Rx {
      ep().filter(displayGraph().graph.postsById.isDefinedAt)
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
    displayGraph.rx.debug { dg => import dg.graph; s"graph: ${graph.posts.size} posts, ${graph.connections.size} connections, ${graph.containments.size} containments" }
    focusedPostId.rx.debug("focusedPostId")
    editedPostId.rx.debug("editedPostId")
    mode.rx.debug("mode")
  }

  val onAuthEvent: AuthEvent => Unit = _ match {
    case LoggedIn(user) => currentUser() = Option(user)
    case LoggedOut => currentUser() = None
  }

  val onApiEvent: ApiEvent => Unit = _ match {
    case NewPost(post) => rawGraph.updatef(_ + post)
    case UpdatedPost(post) => rawGraph.updatef(_ + post)
    case NewConnection(connects) =>
      rawGraph.updatef(_ + connects)
      if (focusedPostId.now contains connects.targetId)
        focusedPostId() = Option(connects.sourceId)
    case NewContainment(contains) => rawGraph.updatef(_ + contains)
    case DeletePost(postId) => rawGraph.updatef(_ - postId)
    case DeleteConnection(connectsId) => rawGraph.updatef(_ - connectsId)
    case DeleteContainment(containsId) => rawGraph.updatef(_ - containsId)
  }
}
