package wust.frontend

import rx._
import rxext._
import wust.api._
import wust.frontend.views.{ViewConfig, ViewPage}
import wust.ids._
import wust.graph._

sealed trait InteractionMode
case class FocusMode(postId: PostId) extends InteractionMode
case class EditMode(postId: PostId) extends InteractionMode
case object DefaultMode extends InteractionMode

class GlobalState(implicit ctx: Ctx.Owner) {
  val currentUser = RxVar[Option[User]](None)
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

  val selectedGroupId = {
    //TODO: magic number for public group!
    val rawSelectedId = RxVar[GroupId](1)
    RxVar(rawSelectedId, Rx {
      if (rawGraph().groupsById.isDefinedAt(rawSelectedId()))
        rawSelectedId()
      else
        1L: GroupId
    })
  }

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

  val onAuthEvent: AuthEvent => Unit = {
    case LoggedIn(user) => currentUser() = Option(user)
    case LoggedOut => currentUser() = None
  }

  val onApiEvent: ApiEvent => Unit = {
    case NewPost(post) =>
      println("new post")
      rawGraph.updatef(_ + post)
    case UpdatedPost(post) => rawGraph.updatef(_ + post)
    case NewConnection(connects) =>
      rawGraph.updatef(_ + connects)
      if (focusedPostId.now contains connects.targetId)
        focusedPostId() = Option(connects.sourceId)
    case NewContainment(contains) => rawGraph.updatef(_ + contains)
    case NewOwnership(ownership) => rawGraph.updatef(g => g.copy(ownerships = g.ownerships + ownership))
    case NewMembership(membership) => rawGraph.updatef(g => g.copy(memberships = g.memberships + membership))
    case NewUser(user) => rawGraph.updatef(g => g.copy(usersById = g.usersById + (user.id -> user)))
    case NewGroup(group) => rawGraph.updatef(g => g.copy(groupsById = g.groupsById + (group.id -> group)))

    case DeletePost(postId) => rawGraph.updatef(_ - postId)
    case DeleteConnection(connectsId) => rawGraph.updatef(_ - connectsId)
    case DeleteContainment(containsId) => rawGraph.updatef(_ - containsId)
    case ReplaceGraph(newGraph) =>
      rawGraph() = newGraph
      DevOnly {
        assert(newGraph.consistent == newGraph)
        assert(currentUser.now.forall(user => newGraph.usersById.isDefinedAt(user.id)), "current user is not in Graph")
        assert(currentUser.now.forall(user => newGraph.groupsByUserId(user.id).toSet == newGraph.groups.toSet), "User is not member of all groups")
      }
    case ImplicitLogin(auth) => Client.auth.acknowledgeAuth(auth)
  }
}
