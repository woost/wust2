package wust.webApp

import monix.execution.Cancelable
import monix.reactive.OverflowStrategy.Unbounded
import monocle.macros.GenLens
import org.scalajs.dom.{Event, window}
import outwatch.dom._
import rx._
import wust.api.ApiEvent.ReplaceGraph
import wust.api._
import wust.graph._
import wust.ids._
import wust.sdk._
import wust.util.Selector
import wust.webApp.outwatchHelpers._
import wust.webApp.views.{PageStyle, View, ViewConfig}
import cats._
import cats.data.NonEmptyList
import outwatch.ObserverSink

import scala.collection.breakOut

class GlobalState private (implicit ctx: Ctx.Owner) {

  val syncMode: Var[SyncMode] = Client.storage.syncMode.imap[SyncMode](_.getOrElse(SyncMode.default))(Option(_))
  val sidebarOpen: Var[Boolean] = Client.storage.sidebarOpen
  val syncDisabled = syncMode.map(_ != SyncMode.Live)
  private val viewConfig: Var[ViewConfig] = UrlRouter.variable.imap(_.fold(ViewConfig.default)(ViewConfig.fromUrlHash))(x => Option(ViewConfig.toUrlHash(x)))

  val eventProcessor = EventProcessor(
    Client.observable.event,
    syncDisabled.toObservable,
    (changes, graph) => applyEnrichmentToChanges(graph, viewConfig.now)(changes),
    Client.api.changeGraph _
  )

  val currentAuth: Rx[Authentication] = eventProcessor.currentAuth.toRx(seed = Client.currentAuth)

  val currentUser: Rx[User] = currentAuth.map(_.user)
  val highLevelPosts = Var[List[Post]](Nil)

  val newPostSink = ObserverSink(eventProcessor.enriched.changes).redirect { (o: Observable[PostContent]) =>
    o.withLatestFrom(currentUser.toObservable)((msg, user) => GraphChanges.addPost(msg, user.id))
  }

  val rawGraph: Rx[Graph] = {
    val graph = eventProcessor.rawGraph.toRx(seed = Graph.empty)
    Rx {
      graph().addPosts(highLevelPosts()) //TODO: this is a hack, highlevel posts should already be in the graph
    }
  }

  val view: Var[View] = viewConfig.zoom(GenLens[ViewConfig](_.view))

  val page: Var[Page] = viewConfig.zoom(GenLens[ViewConfig](_.page)).mapRead { rawPage =>
    rawPage().copy(
      parentIds = rawPage().parentIds //.filter(rawGraph().postsById.isDefinedAt)
    )
  }

  val pageParentPosts: Rx[Seq[Post]] = Rx {
    //TODO highLevelPosts should be part of graph
    page().parentIds.flatMap(id => rawGraph().postsById.get(id) orElse highLevelPosts().find(_.id == id))
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
      case Page(parentIds, _) if parentIds.isEmpty =>
        perspective().applyOnGraph(graph)

      case Page(parentIds, _) =>
        val descendants = parentIds.flatMap(graph.descendants) diff parentIds
        val selectedGraph = graph.filter(descendants.contains)
        perspective().applyOnGraph(selectedGraph)
    }
  }

  val displayGraphWithParents: Rx[DisplayGraph] = Rx {
    val graph = rawGraph()
    page() match {
      case Page(parentIds, _) if parentIds.isEmpty =>
        perspective().applyOnGraph(graph)

      case Page(parentIds, _) =>
        //TODO: this seems to crash when parentid does not exist
        val descendants = parentIds.flatMap(graph.descendants) ++ parentIds
        val selectedGraph = graph.filter(descendants.contains)
        perspective().applyOnGraph(selectedGraph)
    }
  }

  val upButtonTargetPage: Rx[Option[Page]] = Rx {
    //TODO: handle containment cycles
    page() match {
      case Page(parentIds, _) if parentIds.isEmpty => None
      case Page(parentIds, _) =>
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
    currentAuth.foreach(auth => Client.storage.auth() = Some(auth))
    currentAuth.foreach(IndexedDbOps.storeAuth)

    //TODO: better build up state from server events?
    viewConfig.toObservable.switchMap { vc =>
      Observable.fromFuture(Client.api.getGraph(vc.page))
    }.foreach { graph =>
      eventProcessor.unsafeManualEvents.onNext(ReplaceGraph(graph))
    }

    currentUser.foreach { _ =>
      //TODO: HighLevelPosts should be part of the graph in the first place
      Client.api.getHighLevelPosts().foreach {
        highLevelPosts() = _
      }
    }

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
      // viewConfig.debug("viewConfig")
      //      currentUser.debug("\ncurrentUser")

    }

    state
  }
}
