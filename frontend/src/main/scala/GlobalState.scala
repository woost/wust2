package wust.frontend

import rx._
import rxext._
import wust.api._
import wust.frontend.views.{ViewConfig, ViewPage}
import wust.ids._
import wust.graph._
import org.scalajs.dom.window
import org.scalajs.dom.experimental.Notification

sealed trait InteractionMode
case class FocusMode(postId: PostId) extends InteractionMode
case class EditMode(postId: PostId) extends InteractionMode
case object DefaultMode extends InteractionMode

class GlobalState(implicit ctx: Ctx.Owner) {
  val currentUser = RxVar[Option[User]](None)

  val viewConfig = UrlRouter.variable
    .projection[ViewConfig]((ViewConfig.toHash _) andThen Option.apply, ViewConfig.fromHash)

  val viewPage = viewConfig
    .projection[ViewPage](page => viewConfig.now.copy(page = page), _.page)

  //TODO: ".now" is bad here. Maybe we need:
  // projection[B](to: (A,B) => A, from: A => B)
  val rawGraphSelection = viewConfig
    .projection[GraphSelection](selection => viewConfig.now.copy(selection = selection), _.selection)

  val inviteToken = viewConfig.map(_.invite)

  val rawGraph = RxVar(Graph.empty)
    .map(_.consistent)

  val graphSelection = RxVar(rawGraphSelection, Rx {
    val graph = rawGraph()
    rawGraphSelection() match {
      case GraphSelection.Union(ids) =>
        GraphSelection.Union(ids.filter(graph.postsById.isDefinedAt))
      case s => s
    }
  })

  val selectedGroupId = {
    val rawSelectedId = RxVar[Option[GroupId]](None)
    RxVar(rawSelectedId, Rx {
      val graph = rawGraph()
      val selected = rawSelectedId()
      selected.filter(graph.groupsById.isDefinedAt)
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

  val jsError = Var[Option[String]](None)

  def onApiEvent(event: ApiEvent) {
    rawGraph.updatef(GraphUpdate.onEvent(_, event))

    event match {
      case NewPost(post) =>
        Notifications.notify("New Post", Option(post.title),
          onclick = { (notification) =>
            notification.close()
            window.focus()
            focusedPostId() = Option(post.id)
          })
      case NewConnection(connection) =>
        if (focusedPostId.now contains connection.targetId)
          focusedPostId() = Option(connection.sourceId)
      case ReplaceGraph(newGraph) =>
        DevOnly {
          assert(newGraph.consistent == newGraph, s"got inconsistent graph from server:\n$newGraph\nshould be:\n${newGraph.consistent}")
          assert(currentUser.now.forall(user => newGraph.usersById.isDefinedAt(user.id)), s"current user is not in Graph:\n$newGraph\nuser: ${currentUser.now}")
          assert(currentUser.now.forall(user => newGraph.groupsByUserId(user.id).toSet == newGraph.groups.map(_.id).toSet), s"User is not member of all groups:\ngroups: ${newGraph.groups}\nmemberships: ${newGraph.memberships}\nuser: ${currentUser.now}\nmissing memberships for groups:${currentUser.now.map(user => newGraph.groups.map(_.id).toSet -- newGraph.groupsByUserId(user.id).toSet)}")
        }

      case LoggedIn(auth) =>
        currentUser() = Option(auth.user)
        ClientCache.currentAuth = Option(auth)
      case LoggedOut =>
        currentUser() = None
        ClientCache.currentAuth = None

      case _ =>
    }
  }
}
