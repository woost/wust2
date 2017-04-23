package wust.frontend

import rx._
import rxext._
import wust.api._
import wust.frontend.views.{ViewConfig, ViewPage}
import wust.graph._

sealed trait InteractionMode
case class FocusMode(postId: PostId) extends InteractionMode
case class EditMode(postId: PostId) extends InteractionMode
case object DefaultMode extends InteractionMode

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

  val viewConfig = UrlRouter.variable
    .projection[ViewConfig](ViewConfig.toHash andThen Option.apply, ViewConfig.fromHash)

  val viewPage = viewConfig
    .projection[ViewPage](page => viewConfig.now.copy(page = page), _.page)

  //TODO: ".now" is bad here. Maybe we need:
  // projection[B](to: (A,B) => A, from: A => B)
  val graphSelection = viewConfig
    .projection[GraphSelection](selection => viewConfig.now.copy(selection = selection), _.selection)

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
    RxVar(rawGraph, Rx {
      val selection = graphSelection()
      val focusedParents = selection match {
        case GraphSelection.Root => Set.empty
        case GraphSelection.Union(parentIds) => parentIds
      }
      Perspective(currentView(), rawGraph() -- focusedParents)
    })
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

  val onAuthEvent: AuthEvent => Unit = _ match {
    case LoggedIn(user) => currentUser() = Option(user)
    case LoggedOut =>
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
    case NewOwnership(ownership) => rawGraph.updatef(g => g.copy(ownerships = g.ownerships + ownership))
    case DeletePost(postId) => rawGraph.updatef(_ - postId)
    case DeleteConnection(connectsId) => rawGraph.updatef(_ - connectsId)
    case DeleteContainment(containsId) => rawGraph.updatef(_ - containsId)
    case ReplaceGraph(newGraph) => {
      rawGraph() = newGraph
      DevOnly {
        assert(newGraph.consistent == newGraph)
      }
    }
    case ReplaceUserGroups(newGroups) => currentGroups() = newGroups
    case ImplicitLogin(auth) => Client.auth.acknowledgeAuth(auth)
  }
}
