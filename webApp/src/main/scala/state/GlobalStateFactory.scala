package wust.webApp.state

import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom.window
import rx._
import wust.api.ApiEvent.ReplaceGraph
import wust.api._
import wust.graph._
import wust.ids._
import wust.sdk._
import wust.webApp.jsdom.{IndexedDbOps, Navigator, Notifications}
import wust.webApp.outwatchHelpers._
import wust.webApp.{Client, DevOnly}

import scala.collection.breakOut
import scala.concurrent.duration._
import scala.scalajs.js.Date

object GlobalStateFactory {
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
      Client.currentAuth
    )

    val isOnline = Observable.merge(
      Client.observable.connected.map(_ => true),
      Client.observable.closed.map(_ => false)
    ).unsafeToRx(true)

    val state = new GlobalState(swUpdateIsAvailable, eventProcessor, sidebarOpen, viewConfig, isOnline)
    import state._

    //TODO: better in rx/obs operations
    // store auth in localstore and indexed db
    val authWithPrev = auth.fold((auth.now, auth.now)) { (prev, auth) => (prev._2, auth) }
    authWithPrev.foreach { case (prev, auth) =>
      Client.storage.auth() = Some(auth)
      IndexedDbOps.storeAuth(auth)

      // first subscription is send by the serviceworker. we do the one when the user changes
      // TODO:
      // - send message to service worker on user change.
      // - drop whole indexeddb storage to sync with serviceworker.
      // - move this logic into serviceworker
      if (prev.user.id != auth.user.id) {
        auth.user match {
          case u: AuthUser.Assumed => Notifications.cancelSubscription()
          case u: AuthUser.Persisted => Notifications.subscribe()
        }
      }
    }

  def newChannelTitle(state: GlobalState) = {
//    var today = new Date()
//    // January is 0!
//    val title =
//      s"Channel ${today.getMonth + 1}-${today.getDate} ${today.getHours()}:${today.getMinutes()}"
//    val sameNamePosts = state.channels.now.filter(_.data.str.startsWith(title))
//    if (sameNamePosts.isEmpty) title
//    else s"$title ${('A' - 1 + sameNamePosts.size).toChar}"
    "New Channel"
  }

    val pageObservable = page.toObservable
    val userObservable = user.toObservable

    //TODO: better build up state from server events?
    // only adding a new channel would normally get a new graph. but we can
    // avoid this here and do nothing. Optimistic UI. We just integrate this
    // change into our local state without asking the backend. when the page
    // changes, we get a new graph. except when it is just a Page.NewChannel.
    // There we want to issue the new-channel change.
    pageObservable
      .withLatestFrom(userObservable)((_,_))
      .switchMap { case (page, user) =>
        page match {
          case Page.Selection(parentIds, childrenIds, mode) =>
            val newGraph = Client.api.getGraph(page)
            Observable.fromFuture(newGraph).map(ReplaceGraph.apply)
          case Page.NewChannel(nodeId) =>
            val changes = GraphChanges.newChannel(nodeId, newChannelTitle(state), user.channelNodeId)
            eventProcessor.enriched.changes.onNext(changes)
            Observable.empty
        }
      }.subscribe(additionalManualEvents)

      // when the user change, we definitly need a new graph, regardless of the page
      userObservable.withLatestFrom(pageObservable)((_,_)).switchMap { case (user, page) =>
        val newGraph = Client.api.getGraph(page)
        Observable.fromFuture(newGraph).map(ReplaceGraph.apply)
      }.subscribe(additionalManualEvents)

    // clear this undo/redo history on page change. otherwise you might revert changes from another page that are not currently visible.
    // update of page was changed manually AFTER initial page
    // pageObservable.drop(1).map(_ => ChangesHistory.Clear).subscribe(eventProcessor.history.action)

    // try to update serviceworker. We do this automatically every 60 minutes. If we do a navigation change like changing the page,
    // we will check for an update immediately, but at max every 30 minutes.
    val autoCheckUpdateInterval = 60.minutes
    val maxCheckUpdateInterval = 30.minutes
    pageObservable
      .drop(1)
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
    pageObservable.drop(1).withLatestFrom(appUpdateIsAvailable)((_, _) => Unit).foreach { _ =>
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
      val nodes = changes.addNodes.collect { case n: Node.Content => n } // only notify for content changes
      if (!state.documentIsVisible.now && nodes.nonEmpty) {
        val msg =
          if (nodes.size == 1) "New Node" else s"New Node (${nodes.size})"
        val body = nodes.map(_.data).mkString(", ")
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

    def toParentConnections(page: Page, nodeId: NodeId): Seq[Edge] =
      page.parentIds.map(Edge.Parent(nodeId, _))(breakOut)

    val containedNodes = addEdges.collect { case Edge.Parent(source, _, _) => source }
    val toContain = addNodes
      .filterNot(p => containedNodes(p.id))
      .flatMap(p => toParentConnections(viewConfig.page, p.id))

    changes.consistent merge GraphChanges(addEdges = toContain)
  }
}
