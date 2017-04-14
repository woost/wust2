package wust.frontend

import rx._, rxext._

import wust.api._
import wust.graph._
import wust.util.Pipe

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import autowire._
import boopickle.Default._

sealed trait InteractionMode
case class FocusMode(postId: PostId) extends InteractionMode
case class EditMode(postId: PostId) extends InteractionMode
case object DefaultMode extends InteractionMode

sealed abstract class ViewPage(val isEditable: Boolean = false)
object ViewPage {
  case object Graph extends ViewPage(isEditable = true)
  case object Tree extends ViewPage(isEditable = true)
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
  val currentGroups = RxVar[Seq[UserGroup]](Seq.empty)
  val selectedGroup = {
    //TODO: magic number for public group!
    val s = RxVar[Long](1)
    RxVar(s, Rx {
      if (currentGroups().exists(_.id == s())) s() else 1
    })
  }

  val viewPage = UrlRouter.variable
    .projection[ViewPage](_ |> ViewPage.toHash |> (Option(_)), _.flatMap(ViewPage.fromHash.lift).getOrElse(ViewPage.default))

  val rawGraph = RxVar(Graph.empty)
    .map(_.consistent)

  val collapsedPostIds = RxVar[Set[PostId]](Set.empty)

  val currentView = {
    val v = RxVar[Perspective](Perspective())
    RxVar(v, Rx {
      v().union(Perspective(collapsed = Selector.IdSet(collapsedPostIds())))
    })
  }

  val displayGraph = {
    RxVar(rawGraph, Rx { Perspective(currentView(), rawGraph()) })
  }

  val focusedPostId = {
    val fp = RxVar[Option[PostId]](None)
    RxVar(fp, Rx {
      fp().filter(displayGraph().graph.postsById.isDefinedAt)
    })
  }

  val editedPostId = {
    val ep = RxVar[Option[PostId]](None)
    RxVar(ep, Rx {
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
    rawGraph.debug(v => s"rawGraph: ${v.posts.size} posts, ${v.connections.size} connections, ${v.containments.size} containments")
    collapsedPostIds.debug("collapsedPostIds")
    currentView.debug("currentView")
    displayGraph.debug { dg => import dg.graph; s"graph: ${graph.posts.size} posts, ${graph.connections.size} connections, ${graph.containments.size} containments" }
    focusedPostId.debug("focusedPostId")
    editedPostId.debug("editedPostId")
    mode.debug("mode")
  }

  val onAuthEvent: AuthEvent => Unit = _ match {
    case LoggedIn(user) => currentUser() = Option(user)
    case LoggedOut =>
      //TODO: on logout, get new graph from server directly per event instead of requesting here
      //TODO: public group id from config
      Client.api.getGraph(1).call().foreach { newGraph =>
        rawGraph() = newGraph
      }
      currentUser() = None
      currentGroups() = Nil
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
    case ReplaceGraph(newGraph) => rawGraph() = newGraph
    case ReplaceUserGroups(newGroups) => currentGroups() = newGroups
  }
}
