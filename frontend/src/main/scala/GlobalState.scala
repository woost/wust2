package wust.frontend

import vectory._
import wust.api._
import wust.frontend.views.{ViewConfig, View}
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

  import StateHelpers._
  import ClientCache.storage

  val syncMode: Handler[SyncMode] = createHandler[SyncMode](SyncMode.default).unsafeRunSync() //TODO storage.syncMode
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
    events.flatMap(Observable.from(_)) //TODO flattening here is nice, but also pushes more updates?
  }

  val currentUser: Observable[Option[User]] = eventStream.collect {
    case LoggedIn(auth) => Some(auth.user)
    case LoggedOut => None
  }.startWith(None)

  val rawGraph: Observable[Graph] = {
    val localEvents = persistence.localChanges.map(NewGraphChanges(_))
    val events = /*eventStream merge */localEvents
    events.scan(Graph.empty)(GraphUpdate.applyEvent)
  }
  rawGraph { g => println("graph "  + g) }

  val viewConfig: Handler[ViewConfig] = UrlRouter.variable.imapMap(ViewConfig.fromHash)(x => Option(ViewConfig.toHash(x)))

  val view: Handler[View] = viewConfig.lens(ViewConfig.default)(_.view)((config, view) => config.copy(view = view))

  val rawPage: Handler[Page] = viewConfig.lens(ViewConfig.default)(_.page)((config, page) => config.copy(page = page))

  val inviteToken = viewConfig.map(_.invite)

  val page = rawPage.comap { _.combineLatestWith(rawGraph){ (page, graph) =>
    page match {
      case Page.Union(ids) =>
        Page.Union(ids.filter(graph.postsById.isDefinedAt))
      case s => s
    }
  }}

  val rawSelectedGroupId: Handler[Option[GroupId]] = viewConfig.lens(ViewConfig.default)(_.groupIdOpt)((config, groupIdOpt) => config.copy(groupIdOpt = groupIdOpt))

  val selectedGroupId: Handler[Option[GroupId]] = rawSelectedGroupId.comap( _.combineLatestWith(rawGraph){ (groupIdOpt, graph) =>
    groupIdOpt.filter(graph.groupsById.isDefinedAt)
  })

  // be aware that this is a potential memory leak.
  // it contains all ids that have ever been collapsed in this session.
  // this is a wanted feature, because manually collapsing posts is preserved with navigation
  val collapsedPostIds: Handler[Set[PostId]] = createHandler(Set.empty[PostId]).unsafeRunSync()

  val currentView: Handler[Perspective] = createHandler(Perspective()).unsafeRunSync()
    .comap(_.combineLatestWith(collapsedPostIds){ (perspective, collapsedPostIds) =>
      perspective.union(Perspective(collapsed = Selector.IdSet(collapsedPostIds)))
    })

  //TODO: when updating, both displayGraphs are recalculated
  // if possible only recalculate when needed for visualization
  val displayGraphWithoutParents: Observable[DisplayGraph] = // rawGraph.combineLatestWith(viewConfig, selectedGroupId, graphSelection, currentView){
    //(rawGraph, viewConfig, selectedGroupId, graphSelection, currentView) =>
    for {
      rawGraph <- rawGraph
      viewConfig <- viewConfig
      selectedGroupId <- selectedGroupId
      page <- page
      currentView <- currentView
    } yield {
      val graph = groupLockFilter(viewConfig, selectedGroupId, rawGraph.consistent)
      page match {
        case Page.Root =>
          currentView.applyOnGraph(graph)

        case Page.Union(parentIds) =>
          val descendants = parentIds.flatMap(graph.descendants) -- parentIds
          val selectedGraph = graph.filter(descendants)
          currentView.applyOnGraph(selectedGraph)
      }
  }


  val displayGraphWithParents: Observable[DisplayGraph] = //rawGraph.combineLatestWith(viewConfig, selectedGroupId, graphSelection, currentView){
    //(rawGraph, viewConfig, selectedGroupId, graphSelection, currentView) =>
    for {
      rawGraph <- rawGraph
      viewConfig <- viewConfig
      selectedGroupId <- selectedGroupId
      page <- page
      currentView <- currentView
    } yield {
      val graph = groupLockFilter(viewConfig, selectedGroupId, rawGraph.consistent)
      page match {
        case Page.Root =>
          currentView.applyOnGraph(graph)

        case Page.Union(parentIds) =>
          val descendants = parentIds.flatMap(graph.descendants) ++ parentIds
          val selectedGraph = graph.filter(descendants)
          currentView.applyOnGraph(selectedGraph)
      }
  }

  val focusedPostId: Handler[Option[PostId]] = {
    val handler = createHandler(Option.empty[PostId]).unsafeRunSync()
    handler.comap(_.combineLatestWith(displayGraphWithoutParents){ (focusedPostId, displayGraphWithoutParents) =>
      focusedPostId.filter(displayGraphWithoutParents.graph.postsById.isDefinedAt)
    })
  }

  val postCreatorMenus: Handler[List[PostCreatorMenu]] = createHandler(List.empty[PostCreatorMenu]).unsafeRunSync()

  val jsError: Handler[Option[String]] = createHandler(Option.empty[String]).unsafeRunSync

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

object StateHelpers {
  def groupLockFilter(viewConfig: ViewConfig, selectedGroupId: Option[GroupId], graph: Graph): Graph =
    if (viewConfig.lockToGroup) {
      val groupPosts = selectedGroupId.map(graph.postsByGroupId).getOrElse(Set.empty)
      graph.filter(groupPosts)
    } else graph
}
