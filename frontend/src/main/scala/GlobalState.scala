package wust.frontend

import rx._
import rxext._
import wust.api._
import wust.frontend.views.{ViewConfig, ViewPage}
import wust.ids._
import wust.graph._
import org.scalajs.dom.{window, console}
import org.scalajs.dom.experimental.Notification
import wust.util.EventTracker.sendEvent
import vectory._

sealed trait SyncMode
object SyncMode {
  case object Live extends SyncMode
  case object Offline extends SyncMode

  val fromString: PartialFunction[String, SyncMode] = {
    case "Live"    => Live
    case "Offline" => Offline
  }

  val default = Live
  val all = Seq(Live, Offline)
}

case class PostCreatorMenu(pos: Vec2) {
  var ySimPostOffset: Double = 50
}

class GlobalState(implicit ctx: Ctx.Owner) {
  import Client.storage

  val persistence = new GraphPersistence(this)
  val eventCache = new EventCache(this)

  val syncMode = Var[SyncMode](storage.syncMode.getOrElse(SyncMode.default))
  //TODO: why does triggerlater not work?
  syncMode.foreach(storage.syncMode = _)

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

  val graphSelection = RxVar(rawGraphSelection, Rx {
    val graph = rawGraph()
    rawGraphSelection() match {
      case GraphSelection.Union(ids) =>
        GraphSelection.Union(ids.filter(graph.postsById.isDefinedAt))
      case s => s
    }
  })

  val rawSelectedGroupId: RxVar[Option[GroupId], Option[GroupId]] = viewConfig
    .projection[Option[GroupId]](groupIdOpt => viewConfig.now.copy(groupIdOpt = groupIdOpt), _.groupIdOpt)

  val selectedGroupId: RxVar[Option[GroupId], Option[GroupId]] = {
    RxVar(rawSelectedGroupId, Rx {
      rawSelectedGroupId().filter(rawGraph().groupsById.isDefinedAt)
    })
  }

  // be aware that this is a potential memory leak.
  // it contains all ids that have ever been collapsed in this session.
  // this is a wanted feature, because manually collapsing posts is preserved with navigation
  val collapsedPostIds = RxVar[Set[PostId]](Set.empty)

  val currentView = {
    val v = RxVar[Perspective](Perspective())
    RxVar(v, Rx {
      v().union(Perspective(collapsed = Selector.IdSet(collapsedPostIds())))
    })
  }

  val groupLockFilter: Graph => Graph = if (viewConfig.now.lockToGroup) {
    { graph =>
      val groupPosts = selectedGroupId.now.map(graph.postsByGroupId).getOrElse(Set.empty)
      graph.filter(groupPosts)
    }
  } else identity

  //TODO: when updating, both displayGraphs are recalculated
  // if possible only recalculate when needed for visualization
  val displayGraphWithoutParents = {
    RxVar(rawGraph, Rx {
      val graph = groupLockFilter(rawGraph().consistent)
      graphSelection() match {
        case GraphSelection.Root =>
          Perspective(currentView(), graph)

        case GraphSelection.Union(parentIds) =>
          val transitiveChildren = parentIds.flatMap(graph.transitiveChildren) -- parentIds
          val selectedGraph = graph.filter(transitiveChildren)
          Perspective(currentView(), selectedGraph)
      }
    })
  }

  val displayGraphWithParents = {
    RxVar(rawGraph, Rx {
      val graph = groupLockFilter(rawGraph().consistent)
      graphSelection() match {
        case GraphSelection.Root =>
          Perspective(currentView(), graph)

        case GraphSelection.Union(parentIds) =>
          val transitiveChildren = parentIds.flatMap(graph.transitiveChildren) ++ parentIds
          val selectedGraph = graph.filter(transitiveChildren)
          Perspective(currentView(), selectedGraph)
      }
    })
  }

  val focusedPostId = {
    val fp = RxVar[Option[PostId]](None)
    RxVar(fp, Rx {
      fp().filter(displayGraphWithoutParents().graph.postsById.isDefinedAt)
    })
  }

  val postCreatorMenus = Var[List[PostCreatorMenu]](Nil)

  val jsError = Var[Option[String]](None)

  //TODO: hack for having authorship and creation time of post. this needs to be in the backend
  val postTimes = new collection.mutable.HashMap[PostId, Long]
  val ownPosts = new collection.mutable.HashSet[PostId]

  def applyEvents(events: Seq[ApiEvent]) = {
    events foreach {
      case LoggedIn(auth) =>
        currentUser() = Option(auth.user)
        ClientCache.currentAuth = Option(auth)
        if (auth.user.isImplicit) {
          sendEvent("auth", "loginimplicit", "success")
        }
      case LoggedOut =>
        currentUser() = None
        ClientCache.currentAuth = None
      case _ =>
    }

    val newGraph = events.foldLeft(rawGraph.now)(GraphUpdate.onEvent(_, _))
    // take changes into account, when we get a new graph
    persistence.applyChangesToState(newGraph)
  }

  def onEvents(events: Seq[ApiEvent]) = {
    DevOnly {
      views.DevView.apiEvents.updatef(events.toList ++ _)
      events foreach {
        case ReplaceGraph(newGraph) =>
          assert(newGraph.consistent == newGraph, s"got inconsistent graph from server:\n$newGraph\nshould be:\n${newGraph.consistent}")
        //TODO needed?
        // assert(currentUser.now.forall(user => newGraph.usersById.isDefinedAt(user.id)), s"current user is not in Graph:\n$newGraph\nuser: ${currentUser.now}")
        // assert(currentUser.now.forall(user => newGraph.groupsByUserId(user.id).toSet == newGraph.groups.map(_.id).toSet), s"User is not member of all groups:\ngroups: ${newGraph.groups}\nmemberships: ${newGraph.memberships}\nuser: ${currentUser.now}\nmissing memberships for groups:${currentUser.now.map(user => newGraph.groups.map(_.id).toSet -- newGraph.groupsByUserId(user.id).toSet)}")
        case _ =>
      }
    }

    val (graphChangeEvents, otherEvents) = events partition {
      case NewGraphChanges(_) => true
      case _                  => false
    }

    //TODO fake info about post creation
    val currentTime = System.currentTimeMillis
    postTimes ++= graphChangeEvents.asInstanceOf[Seq[NewGraphChanges]].flatMap(_.changes.addPosts.map(_.id -> currentTime))

    eventCache.addEvents(graphChangeEvents)
    applyEvents(otherEvents)
  }
}
