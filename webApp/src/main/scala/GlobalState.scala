package wust.webApp

import monix.execution.Cancelable
import monix.reactive.OverflowStrategy.Unbounded
import monocle.macros.GenLens
import org.scalajs.dom.{Event, window}
import outwatch.ObserverSink
import outwatch.dom._
import rx._
import wust.api.ApiEvent.ReplaceGraph
import wust.api._
import wust.graph._
import wust.ids._
import wust.sdk._
import wust.util.Selector
import wust.webApp.outwatchHelpers._
import wust.webApp.views.{NewGroupView, PageStyle, View, ViewConfig}

import scala.collection.breakOut

class GlobalState private (implicit ctx: Ctx.Owner) {

  val syncMode: Var[SyncMode] = Client.storage.syncMode.imap[SyncMode](_.getOrElse(SyncMode.default))(Option(_))
  val sidebarOpen: Var[Boolean] = Client.storage.sidebarOpen
  val syncDisabled = syncMode.map(_ != SyncMode.Live)

  val viewConfig: Var[ViewConfig] = UrlRouter.variable.imap(_.fold(ViewConfig.default)(ViewConfig.fromUrlHash))(x => Option(ViewConfig.toUrlHash(x)))

  val eventProcessor = EventProcessor(
    Client.observable.event,
    syncDisabled.toObservable,
    (changes, graph) => applyEnrichmentToChanges(graph, viewConfig.now)(changes),
    Client.api.changeGraph _
  )

  //TODO: rename to auth and user. globalstate implies it is the current value....
  val auth: Rx[Authentication] = eventProcessor.currentAuth.toRx(seed = Client.currentAuth)

  val user: Rx[User] = auth.map(_.user)

  val newPostSink = ObserverSink(eventProcessor.enriched.changes).redirect { o: Observable[PostContent] => o.withLatestFrom(user.toObservable)((msg, user) => GraphChanges.addPost(msg, user.id))
  }

  val rawGraph: Rx[Graph] = eventProcessor.rawGraph.toRx(seed = Graph.empty)

  val channels: Rx[List[Post]] = Rx {
    val graph = rawGraph()
    (graph.children(user().channelPostId).map(graph.postsById)(breakOut): List[Post]).sortBy(_.content.str)
  }

  val page: Var[Page] = viewConfig.zoom(GenLens[ViewConfig](_.page)).mapRead { rawPage =>
    rawPage().copy(
      parentIds = rawPage().parentIds //.filter(rawGraph().postsById.isDefinedAt)
    )
  }

  val view: Var[View] = viewConfig.zoom(GenLens[ViewConfig](_.view)).mapRead { view =>
    if( !view().isContent || page().parentIds.nonEmpty || page().mode != PageMode.Default)
      view()
    else
      NewGroupView
  }

  val pageParentPosts: Rx[Seq[Post]] = Rx {
    page().parentIds.flatMap(id => rawGraph().postsById.get(id))
  }

  val pageStyle = Rx {
    PageStyle(view(), page())
  }

  // be aware that this is a potential memory leak.
  // it contains all ids that have ever been collapsed in this session.
  // this is a wanted feature, because manually collapsing posts is preserved with navigation
  val collapsedPostIds: Var[Set[PostId]] = Var(Set.empty)

  val perspective: Var[Perspective] = Var(Perspective()).mapRead { perspective =>
    perspective().union(Perspective(collapsed = Selector.Predicate(collapsedPostIds())))
  }

  //TODO: when updating, both displayGraphs are recalculated
  // if possible only recalculate when needed for visualization
  val displayGraphWithoutParents: Rx[DisplayGraph] = Rx {
    val graph = rawGraph()
    page() match {
      case Page(parentIds, _, mode) =>
        val modeSet: Set[PostId] = mode match {
          case PageMode.Orphans => graph.postIds.filter(id => graph.parents(id).isEmpty && id != user().channelPostId).toSet
          case PageMode.Default => Set.empty
        }
        val descendants = parentIds.flatMap(graph.descendants) diff parentIds
        val selectedGraph = graph.filter(id => descendants.contains(id) || modeSet.contains(id))
        perspective().applyOnGraph(selectedGraph)
    }
  }

  val displayGraphWithParents: Rx[DisplayGraph] = Rx {
    val graph = rawGraph()
    page() match {
      case Page(parentIds, _, mode) =>
        val modeSet: Set[PostId] = mode match {
          case PageMode.Orphans => graph.postIds.filter(id => graph.parents(id).isEmpty && id != user().channelPostId).toSet
          case PageMode.Default => Set.empty
        }
        //TODO: this seems to crash when parentid does not exist
        val descendants = parentIds.flatMap(graph.descendants) ++ parentIds
        val selectedGraph = graph.filter(id => descendants.contains(id) || modeSet.contains(id))
        perspective().applyOnGraph(selectedGraph)
    }
  }

  val upButtonTargetPage: Rx[Option[Page]] = Rx {
    //TODO: handle containment cycles
    page() match {
      case Page(parentIds, _, _) if parentIds.isEmpty => None
      case Page(parentIds, _, _) =>
        val newParentIds = parentIds.flatMap(rawGraph().parents)
        Some(Page(newParentIds))
    }
  }

  val jsErrors: Handler[Seq[String]] = Handler.create(Seq.empty[String]).unsafeRunSync()

  private def applyEnrichmentToChanges(graph: Graph, viewConfig: ViewConfig)(changes: GraphChanges): GraphChanges = {
    import changes.consistent._

    val toDelete = delPosts.flatMap { postId =>
      Collapse.getHiddenPosts(graph removePosts viewConfig.page.parentIds, Set(postId))
    }

    def toParentConnections(page: Page, postId: PostId): Seq[Connection] = page.parentIds.map(Connection(postId, Label.parent, _))(breakOut)

    val containedPosts = addConnections.collect { case Connection(source, Label.parent, _) => source }
    val toContain = addPosts
      .filterNot(p => containedPosts(p.id))
      .flatMap(p => toParentConnections(viewConfig.page, p.id))

    changes.consistent merge GraphChanges(delPosts = toDelete, addConnections = toContain)
  }
}

object GlobalState {
  def create()(implicit ctx: Ctx.Owner): GlobalState = {
    val state = new GlobalState
    import state._

    //TODO: better in rx/obs operations
    auth.foreach(auth => Client.storage.auth() = Some(auth))
    auth.foreach(IndexedDbOps.storeAuth)

    // on initial page load we add the currently viewed page as a channel
    val changes = GraphChanges.addToParent(viewConfig.now.page.parentIds, user.now.channelPostId)
    eventProcessor.changes.onNext(changes)

    //TODO: better build up state from server events?
    viewConfig.toObservable.combineLatest(state.user.toObservable).switchMap { case (viewConfig, user) =>
      val newGraph = Client.api.getGraph(viewConfig.page).map(ReplaceGraph(_))
      Observable.fromFuture(newGraph)
    }.subscribe(eventProcessor.unsafeManualEvents)

    // clear this undo/redo history on page change. otherwise you might revert changes from another page that are not currently visible.
    page.foreach { _ => eventProcessor.history.action.onNext(ChangesHistory.Clear) }

    // write all initial storage changes, in case they did not get through to the server
    // Client.storage.graphChanges.take(1).flatMap(Observable.fromIterable) subscribe eventProcessor.changes
    //TODO: wait for Storage.handlerWithEventsOnly
    //Client.storage.graphChanges.drop(1) subscribe eventProcessor.nonSendingChanges
    // eventProcessor.changesInTransit subscribe Client.storage.graphChanges.unsafeOnNext _

    //Client.storage.graphChanges.redirect[GraphChanges](_.scan(List.empty[GraphChanges])((prev, curr) => prev :+ curr) <-- eventProcessor.changes
    // TODO: Analytics
    // if (compactChanges.addPosts.nonEmpty) Analytics.sendEvent("graphchanges", "addPosts", "success", compactChanges.addPosts.size)
    // if (compactChanges.addConnections.nonEmpty) Analytics.sendEvent("graphchanges", "addConnections", "success", compactChanges.addConnections.size)
    // if (compactChanges.updatePosts.nonEmpty) Analytics.sendEvent("graphchanges", "updatePosts", "success", compactChanges.updatePosts.size)
    // if (compactChanges.delPosts.nonEmpty) Analytics.sendEvent("graphchanges", "delPosts", "success", compactChanges.delPosts.size)
    // if (compactChanges.delConnections.nonEmpty) Analytics.sendEvent("graphchanges", "delConnections", "success", compactChanges.delConnections.size)
    // Analytics.sendEvent("graphchanges", "flush", "returned-false", changes.size)
    // Analytics.sendEvent("graphchanges", "flush", "future-failed", changes.size)

    Client.observable.event.foreach { events =>
      val changes = events.collect { case ApiEvent.NewGraphChanges(changes) => changes }.foldLeft(GraphChanges.empty)(_ merge _)
      if (changes.addPosts.nonEmpty) {
        val msg = if (changes.addPosts.size == 1) "New Post" else s"New Post (${changes.addPosts.size})"
        val body = changes.addPosts.map(_.content).mkString(", ")
        Notifications.notify(msg, body = Some(body), tag = Some("new-post"))
      }
    }

    DevOnly {
      val errorMessage = Observable.create[String](Unbounded) { observer =>
        window.onerror = { (msg: Event, source: String, line: Int, col: Int, _: Any) =>
          //TODO: send and log production js errors in backend
          observer.onNext(msg.toString)
        }
        Cancelable()
      }
      (jsErrors <-- errorMessage.scan(Vector.empty[String])((acc, msg) => acc :+ msg)).unsafeRunSync

      rawGraph.debug((g: Graph) => s"rawGraph: ${g.toSummaryString}")
      //      collapsedPostIds.debug("collapsedPostIds")
      perspective.debug("perspective")
      displayGraphWithoutParents.debug { dg => s"displayGraph: ${dg.graph.toSummaryString}" }
      //      focusedPostId.debug("focusedPostId")
      //      selectedGroupId.debug("selectedGroupId")
      // rawPage.debug("rawPage")
      page.debug("page")
      view.debug("view")
      user.debug("auth")
      // viewConfig.debug("viewConfig")
      //      currentUser.debug("\ncurrentUser")

    }

    state
  }
}
