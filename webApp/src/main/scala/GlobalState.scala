package wust.webApp

import monix.execution.Cancelable
import monix.reactive.OverflowStrategy.Unbounded
import monix.reactive.subjects.PublishSubject
import monocle.macros.GenLens
import org.scalajs.dom.{Event, window}
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
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
import scala.concurrent.duration._

class GlobalState private (
    val appUpdateIsAvailable: Observable[Unit],
    val eventProcessor: EventProcessor,
    val sidebarOpen: Var[Boolean],
    val viewConfig: Var[ViewConfig]
)(implicit ctx: Ctx.Owner) {

  val auth: Rx[Authentication] = eventProcessor.currentAuth.toRx(seed = Client.currentAuth)
  val user: Rx[AuthUser] = auth.map(_.user)

  val newNodeSink = ObserverSink(eventProcessor.enriched.changes).redirect {
    o: Observable[NodeData.Content] =>
      o.withLatestFrom(user.toObservable)((msg, user) => GraphChanges.addNode(msg))
  }

  val graph: Rx[Graph] = eventProcessor.graph.toRx(seed = Graph.empty)
  val graphContent: Rx[Graph] = graph.map(_.content.consistent)

  val channels: Rx[Seq[Node]] = Rx {
    graph().channels.toSeq.sortBy(_.data.str)
  }

  val page: Var[Page] = viewConfig.zoom(GenLens[ViewConfig](_.page)).mapRead { rawPage =>
    rawPage().copy(
      parentIds = rawPage().parentIds //.filter(rawGraph().postsById.isDefinedAt)
    )
  }

  val view: Var[View] = viewConfig.zoom(GenLens[ViewConfig](_.view)).mapRead { view =>
    if (!view().isContent || page().parentIds.nonEmpty || page().mode != PageMode.Default)
      view()
    else
      NewGroupView
  }

  val pageParentNodes: Rx[Seq[Node]] = Rx {
    page().parentIds.flatMap(id => graph().nodesById.get(id))
  }

  //
  val pageAncestorsIds: Rx[Seq[NodeId]] = Rx {
    page().parentIds.flatMap(node => graph().ancestors(node).toSeq)
  }

  val nodeAncestorsHierarchy: Rx[Map[Int, Seq[Node]]] =
    pageAncestorsIds.map(
      _.map(node => (graph().parentDepth(node), graph().nodesById(node)))
        .groupBy(_._1)
        .mapValues(_.map(_._2).distinct)
    )

  val pageStyle = PageStyle(view, page)

  // be aware that this is a potential memory leak.
  // it contains all ids that have ever been collapsed in this session.
  // this is a wanted feature, because manually collapsing nodes is preserved with navigation
  val collapsedNodeIds: Var[Set[NodeId]] = Var(Set.empty)

  // specifies which nodes are collapsed
  val perspective: Var[Perspective] = Var(Perspective()).mapRead { perspective =>
    perspective().union(Perspective(collapsed = Selector.Predicate(collapsedNodeIds())))
  }

  val jsErrors: Observable[String] = events.window.onError.map(_.message)

  val screenSize: Observable[ScreenSize] = events.window.onResize
    .map(_ => ScreenSize.calculate())
    .startWith(Seq(ScreenSize.calculate()))

}

object GlobalState {
  def create(swUpdateIsAvailable: Observable[Unit])(implicit ctx: Ctx.Owner): GlobalState = {
    val sidebarOpen = Client.storage.sidebarOpen
    val viewConfig = UrlRouter.variable.imap(_.fold(ViewConfig.default)(ViewConfig.fromUrlHash))(
      x => Option(ViewConfig.toUrlHash(x))
    )

    val additionalManualEvents = PublishSubject[ApiEvent]()
    val eventProcessor = EventProcessor(
      Observable.merge(additionalManualEvents.map(Seq(_)), Client.observable.event),
      (changes, graph) => applyEnrichmentToChanges(graph, viewConfig.now)(changes),
      Client.api.changeGraph _,
      Client.currentAuth.user
    )

    val state =
      new GlobalState(swUpdateIsAvailable, eventProcessor, sidebarOpen, viewConfig)

    import state._

    //TODO: better in rx/obs operations
    // store auth in localstore and indexed db
    auth.foreach { auth =>
      Client.storage.auth() = Some(auth)
      IndexedDbOps.storeAuth(auth)
    }

    val pageObservable = page.toObservable.drop(1) // update of page was changed manually AFTER initial page

    //TODO: better build up state from server events?
    // when the viewconfig or user changes, we get a new graph for the current page
    pageObservable
      .combineLatest(user.toObservable)
      .switchMap {
        case (page, user) =>
          val newGraph = Client.api.getGraph(page)
          Observable.fromFuture(newGraph).startWith(Graph.empty :: Nil).map(ReplaceGraph.apply)
      }
      .subscribe(additionalManualEvents)

    // clear this undo/redo history on page change. otherwise you might revert changes from another page that are not currently visible.
    pageObservable.map(_ => ChangesHistory.Clear).subscribe(eventProcessor.history.action)

    // try to update serviceworker. We do this automatically every 60 minutes. If we do a navigation change like changing the page,
    // we will check for an update immediately, but at max every 30 minutes.
    val autoCheckUpdateInterval = 60.minutes
    val maxCheckUpdateInterval = 30.minutes
    pageObservable
      .echoRepeated(autoCheckUpdateInterval)
      .throttleFirst(maxCheckUpdateInterval)
      .foreach { _ =>
        Navigator.serviceWorker.foreach(_.getRegistration().toFuture.foreach(_.foreach { reg =>
          scribe.info("Requesting updating from SW")
          reg.update().toFuture.onComplete { res =>
            scribe.info(s"Result of update request: $res")
          }
        }))
      }

    // if there is a page change and we got an sw update, we want to reload the page
    pageObservable.withLatestFrom(appUpdateIsAvailable)((_, _) => Unit).foreach { _ =>
      scribe.info("Going to reload page, due to SW update")
      // if flag is true, page will be reloaded without cache. False means it may use the browser cache.
      window.location.reload(flag = false)
    }

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

    // notify user about new graph change events (this is the in-app
    // notification, opposed to push notifications coming from the
    // servieworker. the serviceworker will not show push notifications if a
    // client is currently running.
    Client.observable.event.foreach { events =>
      val changes = events
        .collect { case ApiEvent.NewGraphChanges(changes) => changes }
        .foldLeft(GraphChanges.empty)(_ merge _)
      if (changes.addNodes.nonEmpty) {
        val msg =
          if (changes.addNodes.size == 1) "New Node" else s"New Node (${changes.addNodes.size})"
        val body = changes.addNodes.map(_.data).mkString(", ")
        Notifications.notify(msg, body = Some(body), tag = Some("new-node"))
      }
    }

    // we send client errors from javascript to the backend
    jsErrors.foreach { msg =>
      Client.api.log(s"Javascript Error: $msg")
    }

    DevOnly {

//      rawGraph.debug((g: Graph) => s"rawGraph: ${g.toString}")
      //      collapsedNodeIds.debug("collapsedNodeIds")
//      perspective.debug("perspective")
//      displayGraphWithoutParents.debug { dg => s"displayGraph: ${dg.graph.toString}" }
      //      focusedNodeId.debug("focusedNodeId")
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

  private def applyEnrichmentToChanges(graph: Graph, viewConfig: ViewConfig)(
      changes: GraphChanges
  ): GraphChanges = {
    import changes.consistent._

    val toDelete = delNodes.flatMap { nodeId =>
      Collapse.getHiddenNodes(graph removeNodes viewConfig.page.parentIds, Set(nodeId))
    }

    def toParentConnections(page: Page, nodeId: NodeId): Seq[Edge] =
      page.parentIds.map(Edge.Parent(nodeId, _))(breakOut)

    val containedNodes = addEdges.collect { case Edge.Parent(source, _) => source }
    val toContain = addNodes
      .filterNot(p => containedNodes(p.id))
      .flatMap(p => toParentConnections(viewConfig.page, p.id))

    changes.consistent merge GraphChanges(delNodes = toDelete, addEdges = toContain)
  }
}
