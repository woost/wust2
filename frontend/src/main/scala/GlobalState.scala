package wust.frontend

import rx._
import rxext._
import vectory._
import wust.api._
import wust.frontend.views.{ViewConfig, ViewPage}
import wust.graph._
import wust.ids._
import org.scalajs.dom.{console, window}
import org.scalajs.dom.experimental.Notification
import outwatch.dom._
import rxscalajs.subjects._
import rxscalajs.facade._
import wust.util.Analytics
import vectory._
import wust.util.outwatchHelpers._
import rxscalajs.Observable

//outwatch beispiel:
  // def component(handler:Handler[ViewPage]) = {
  //   div(
  //     span(child <-- handler.map(_.toString)),
  //     button(ViewPage("hallo")) --> handler
  //   )
  // }

case class PostCreatorMenu(pos: Vec2) {
  var ySimPostOffset: Double = 50
}

class GlobalState(rawEventStream: Observable[Seq[ApiEvent]]) {

  import ClientCache.storage

  val syncMode: Observable[SyncMode] = createHandler[SyncMode](SyncMode.default).unsafeRunSync() //TODO storage.syncMode
  val syncEnabled: Observable[Boolean] = syncMode.map(_ == SyncMode.Live)

  val persistence = new GraphPersistence(syncEnabled)

  val eventStream: Observable[ApiEvent] = {
    val partitionedEvents = rawEventStream.map(_.partition {
      case NewGraphChanges(_) => true
      case _ => false
    })

    val graphEvents = partitionedEvents.map(_._1)
    val otherEvents = partitionedEvents.map(_._2)
    val bufferedGraphEvents = graphEvents.bufferUnless(syncEnabled).map(_.flatten)

    val events = bufferedGraphEvents merge otherEvents
    events.flatMap(Observable.from(_))
  }

  val currentUser: Observable[Option[User]] = eventStream.collect {
    case LoggedIn(auth) => Some(auth.user)
    case LoggedOut => None
  }.startWith(None)

  val rawGraph = {
    val localEvents = persistence.localChanges.map(NewGraphChanges(_))
    val events = eventStream merge localEvents
    events.foldLeft(Graph.empty)(GraphUpdate.applyEvent)
  }

  val viewConfig: Handler[ViewConfig] = UrlRouter.variable.imapMap(ViewConfig.fromHash)(x => Option(ViewConfig.toHash(x)))

  val viewPage: Handler[ViewPage] = viewConfig.lens(ViewConfig.default)(_.page)((config, page) => config.copy(page = page))

  val rawGraphSelection: Handler[GraphSelection] = viewConfig.lens(ViewConfig.default)(_.selection)((config, selection) => config.copy(selection = selection))

  val inviteToken = viewConfig.map(_.invite)

  val graphSelection = rawGraphSelection.comap { _.combineLatestWith(rawGraph){ (selection, graph) =>
    selection match {
      case GraphSelection.Union(ids) =>
        GraphSelection.Union(ids.filter(graph.postsById.isDefinedAt))
      case s => s
    }
  }}

  val rawSelectedGroupId = viewConfig.lens[Option[GroupId]](ViewConfig.default)(_.groupIdOpt)((config, groupIdOpt) => config.copy(groupIdOpt = groupIdOpt))

  val selectedGroupId = rawSelectedGroupId.comap( _.combineLatestWith(rawGraph){ (groupIdOpt, graph) =>
    groupIdOpt.filter(graph.groupsById.isDefinedAt)
  })

  // be aware that this is a potential memory leak.
  // it contains all ids that have ever been collapsed in this session.
  // this is a wanted feature, because manually collapsing posts is preserved with navigation
  val collapsedPostIds = createHandler[Set[PostId]](Set.empty).unsafeRunSync()

  val currentView = createHandler(Perspective()).unsafeRunSync()
    .comap(_.combineLatestWith(collapsedPostIds){ (perspective, collapsedPostIds) =>
      perspective.union(Perspective(collapsed = Selector.IdSet(collapsedPostIds)))
    })

  private def groupLockFilter(viewConfig: ViewConfig, selectedGroupId: Option[GroupId], graph: Graph):Graph = if (viewConfig.lockToGroup) {
    val groupPosts = selectedGroupId.map(graph.postsByGroupId).getOrElse(Set.empty)
    graph.filter(groupPosts)
  } else graph

  //TODO: when updating, both displayGraphs are recalculated
  // if possible only recalculate when needed for visualization
  val displayGraphWithoutParents = rawGraph.combineLatestWith(viewConfig, selectedGroupId, graphSelection, currentView){
    (rawGraph, viewConfig, selectedGroupId, graphSelection, currentView) =>
      val graph = groupLockFilter(viewConfig, selectedGroupId, rawGraph.consistent)
      graphSelection match {
        case GraphSelection.Root =>
          currentView.applyOnGraph(graph)

        case GraphSelection.Union(parentIds) =>
          val descendants = parentIds.flatMap(graph.descendants) -- parentIds
          val selectedGraph = graph.filter(descendants)
          currentView.applyOnGraph(selectedGraph)
      }
  }


  val displayGraphWithParents = rawGraph.combineLatestWith(viewConfig, selectedGroupId, graphSelection, currentView){
    (rawGraph, viewConfig, selectedGroupId, graphSelection, currentView) =>
      val graph = groupLockFilter(viewConfig, selectedGroupId, rawGraph.consistent)
      graphSelection match {
        case GraphSelection.Root =>
          currentView.applyOnGraph(graph)

        case GraphSelection.Union(parentIds) =>
          val descendants = parentIds.flatMap(graph.descendants) ++ parentIds
          val selectedGraph = graph.filter(descendants)
          currentView.applyOnGraph(selectedGraph)
      }
  }

  val focusedPostId = {
    val handler = createHandler[Option[PostId]](None).unsafeRunSync()
    handler.comap(_.combineLatestWith(displayGraphWithoutParents){ (focusedPostId, displayGraphWithoutParents) =>
      focusedPostId.filter(displayGraphWithoutParents.graph.postsById.isDefinedAt)
    })
  }

  val postCreatorMenus = createHandler[List[PostCreatorMenu]](Nil).unsafeRunSync()

  val jsError = createHandler[Option[String]](None).unsafeRunSync

  //TODO: hack for having authorship of post. this needs to be in the backend
  val ownPosts = new collection.mutable.HashSet[PostId]



  //events!!
  //TODO persistence?
      // rawGraph() = newGraph applyChanges persistence.currentChanges
  //TODO: on user login:
      //     ClientCache.currentAuth = Option(auth)
      //     if (auth.user.isImplicit) {
      //       Analytics.sendEvent("auth", "loginimplicit", "success")
      //     }
      //     ClientCache.currentAuth = None

  // rawEventStream { events =>
    // DevOnly {
    //   views.DevView.apiEvents.updatef(events.toList ++ _)
    //   events foreach {
    //     case ReplaceGraph(newGraph) =>
    //       assert(newGraph.consistent == newGraph, s"got inconsistent graph from server:\n$newGraph\nshould be:\n${newGraph.consistent}")
    //     //TODO needed?
    //     // assert(currentUser.now.forall(user => newGraph.usersById.isDefinedAt(user.id)), s"current user is not in Graph:\n$newGraph\nuser: ${currentUser.now}")
    //     // assert(currentUser.now.forall(user => newGraph.groupsByUserId(user.id).toSet == newGraph.groups.map(_.id).toSet), s"User is not member of all groups:\ngroups: ${newGraph.groups}\nmemberships: ${newGraph.memberships}\nuser: ${currentUser.now}\nmissing memberships for groups:${currentUser.now.map(user => newGraph.groups.map(_.id).toSet -- newGraph.groupsByUserId(user.id).toSet)}")
    //     case _ =>
    //   }
    // }
  // }
}
