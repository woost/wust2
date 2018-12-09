package wust.webApp.state

import java.util.concurrent.TimeUnit

import monix.eval.Task
import monix.reactive.Observable
import org.scalajs.dom.window
import rx._
import wust.api.ApiEvent.ReplaceGraph
import wust.api._
import wust.graph._
import wust.ids._
import wust.sdk._
import wust.webApp.jsdom.{IndexedDbOps, Navigator, Notifications}
import wust.webApp.outwatchHelpers._
import wust.webApp.{BrowserDetect, Client, DevOnly}
import outwatch.dom.helpers.OutwatchTracing
import wust.util.StringOps
import wust.util.algorithm
import wust.webApp.views.UI

import scala.collection.breakOut
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object GlobalStateFactory {
  def create(swUpdateIsAvailable: Observable[Unit])(implicit ctx: Ctx.Owner): GlobalState = {
    val sidebarOpen = Client.storage.sidebarOpen.imap(_ getOrElse !BrowserDetect.isMobile)(Some(_)) // expanded sidebar per default for desktop
    val viewConfig = UrlRouter.variable.imap(_.fold(ViewConfig.default)(ViewConfig.fromUrlHash))(x => Option(ViewConfig.toUrlHash(x)))

    val eventProcessor = EventProcessor(
      Client.observable.event,
      (changes, graph) => applyEnrichmentToChanges(graph, viewConfig.now)(changes),
      Client.api.changeGraph _,
      Client.currentAuth
    )

    val isOnline = Observable(
      Client.observable.connected.map(_ => true),
      Client.observable.closed.map(_ => false)
    ).merge.unsafeToRx(true)

    val isLoading = Var(false)

    val hasError = OutwatchTracing.error.map(_ => true).unsafeToRx(false)

    val fileDownloadBaseUrl = Var[Option[String]](None)

    val state = new GlobalState(swUpdateIsAvailable, eventProcessor, sidebarOpen, viewConfig, isOnline, isLoading, hasError, fileDownloadBaseUrl)
    import state._

    // would be better to statically have this base url from the index.html or something.
    def renewFileDownloadBaseUrl(): Unit = {
      def scheduleRenewal(seconds: Int): Unit = {
        Task(renewFileDownloadBaseUrl()).delayExecution(FiniteDuration(seconds, TimeUnit.SECONDS)).runToFuture
      }

      Client.api.fileDownloadBaseUrl.onComplete {
        case Success(Some(fileUrl)) =>
          fileDownloadBaseUrl() = Some(fileUrl.url)
        case Success(None) =>
          () // nothing to do, not file url available at backend
        case Failure(err) =>
          scribe.warn("Error getting file download base url, will retry in 30 seconds", err)
          scheduleRenewal(seconds = 30)
      }
    }
    renewFileDownloadBaseUrl()

    // TODO: we need this for the migration from username to email can go away afterwards
    user.foreach {
      case user: AuthUser.Real =>
        Client.auth.getUserDetail(user.id).onComplete {
          case Success(Some(detail)) =>
            if(detail.email.isEmpty)
              UI.toast("You do not have an Email setup, please update your Profile.", click = () => state.view() = View.UserSettings, autoclose = false, level = UI.ToastLevel.Warning)
          case err                   =>
            scribe.info(s"Cannot get User Details: ${ err }")
        }
      case _                   => ()
    }

    // automatically pin and notify newly focused nodes
    {
      var prevPage: Page = null
      Rx {
        //TODO: userdescendant
        val pageChangeCmd = pageChange()

        def anyPageParentIsPinned = graph().anyAncestorIsPinned(pageChangeCmd.page.parentId, user().id)
        def pageIsUnderUser:Boolean = (for {
          pageParentId <- pageChangeCmd.page.parentId
          pageIdx = graph().idToIdx(pageParentId)
          if pageIdx != -1
          userIdx = graph().idToIdx(user().id)
          if userIdx != -1
        } yield algorithm.depthFirstSearchExists(start = pageIdx, graph().notDeletedParentsIdx, userIdx)).getOrElse(true)


        def userIsMemberOfPage:Option[Boolean] = for {
          pageParentId <- pageChangeCmd.page.parentId
          pageIdx = graph().idToIdx(pageParentId)
          if pageIdx != -1
          userIdx = graph().idToIdx(user().id)
          if userIdx != -1
        } yield graph().membershipEdgeForNodeIdx.exists(pageIdx)(edgeIdx => graph().edgesIdx.a(edgeIdx) == userIdx)

        userIsMemberOfPage.foreach { userIsMemberOfPage =>
          if(!userIsMemberOfPage) {
            val pageId = pageChangeCmd.page.parentId.get
            Client.api.addMember(pageId, user().id, AccessLevel.ReadWrite)
            // locally add the edge, since a user does not receive its own events
            val event = ApiEvent.NewGraphChanges(user().toNode, GraphChanges(addEdges = Set(Edge.Member(user().id, EdgeData.Member(AccessLevel.ReadWrite), pageId))))
            eventProcessor.localEvents.onNext(event)
          }
        }

        pageChangeCmd.page.parentId.foreach { parentId =>
          if(!isLoading() && pageChangeCmd.needsGet && prevPage != pageChangeCmd.page && !anyPageParentIsPinned && !pageIsUnderUser) {
            prevPage = pageChangeCmd.page //we do this ONCE per page

            // user probably clicked on a woost-link.
            // So we pin the page and enable notifications, and add the user as member
            val changes =
              GraphChanges.connect(Edge.Notify)(parentId, user().id)
              .merge(GraphChanges.connect(Edge.Pinned)(user().id, parentId))
            eventProcessor.changes.onNext(changes)
          }
        }
      }
    }

    // clear selected nodes on view and page change
    {
      val clearTrigger = Rx {
        view()
        page()
        ()
      }
      clearTrigger.foreach { _ => selectedNodes() = Nil }
    }

    //TODO: better in rx/obs operations
    // store auth in localstore and indexed db
    val authWithPrev = auth.fold((auth.now, auth.now)) { (prev, auth) => (prev._2, auth) }
    authWithPrev.foreach { case (prev, auth) =>
      if (prev != auth) {
        Client.storage.auth() = Some(auth)
        IndexedDbOps.storeAuth(auth)
      }

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

    //TODO: better build up state from server events?
    // only adding a new channel would normally get a new graph. but we can
    // avoid this here and do nothing. Optimistic UI. We just integrate this
    // change into our local state without asking the backend. when the page
    // changes, we get a new graph. except when it is just a Page.NewChannel.
    // There we want tnuro issue the new-channel change.
    {
    val userAndPage = Rx {
      (pageChange(), user().toNode)
    }

    var lastTransitChanges: List[GraphChanges] = Nil
    eventProcessor.changesInTransit.foreach { lastTransitChanges = _ }

    var isFirstGraphRequest = true
    var prevPage: PageChange = null
    var prevUser: Node.User = null
    userAndPage.toRawObservable
      .switchMap { case (pageChange, user) =>
        val currentTransitChanges = lastTransitChanges.fold(GraphChanges.empty)(_ merge _)
        val observable: Observable[Graph] =
          if (prevUser == null || prevUser.id != user.id || prevUser.data.isImplicit != user.data.isImplicit) {
            isLoading() = true
            Observable.fromFuture(Client.api.getGraph(pageChange.page))
          } else if (prevPage == null || prevPage != pageChange) {
            if (pageChange.needsGet && (!pageChange.page.isEmpty || isFirstGraphRequest)) {
              isLoading() = true
              Observable.fromFuture(Client.api.getGraph(pageChange.page))
            } else Observable.empty
          } else {
            Observable.empty
          }

        prevPage = pageChange
        prevUser = user
        isFirstGraphRequest = false

        observable.map(g => ReplaceGraph(g.applyChanges(currentTransitChanges)))
      }
        .doOnNext(_ => Task { isLoading() = false })
        .subscribe(eventProcessor.localEvents)
    }

    val titleSuffix = if(DevOnly.isTrue) "dev" else "Woost"
    // switch to View name in title if view switches to non-content
    Rx {
      if (view().isContent) {
        val channelName = page().parentId.flatMap(id => graph().nodesByIdGet(id).map(n => StringOps.trimToMaxLength(n.str, 30)))
        window.document.title = channelName.fold(titleSuffix)(name => s"${if(name.contains("unregistered-user")) "Unregistered User" else name} - $titleSuffix")
      } else {
        window.document.title = s"${view().toString} - $titleSuffix"
      }
    }

    // trigger for updating the app and reloading. we drop 1 because we do not want to trigger for the initial state
    val appUpdateTrigger = Observable(page.toTailObservable, view.toTailObservable).merge

    // try to update serviceworker. We do this automatically every 60 minutes. If we do a navigation change like changing the page,
    // we will check for an update immediately, but at max every 30 minutes.
    val autoCheckUpdateInterval = 60.minutes
    val maxCheckUpdateInterval = 30.minutes
    appUpdateTrigger
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
    appUpdateTrigger.withLatestFrom(appUpdateIsAvailable)((_, _) => Unit).foreach { _ =>
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

    // we send client errors from javascript to the backend
    jsErrors.foreach { msg =>
      Client.api.log(s"Javascript Error: $msg")
    }

    DevOnly {

     graph.debug((g: Graph) => s"graph: ${g.toString}")
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

    def toParentConnections(page: Page, nodeId: NodeId): Option[Edge] =
      page.parentId.map(Edge.Parent(nodeId, _))

    val containedNodes = addEdges.collect { case Edge.Parent(source, _, _) => source }
    val toContain = addNodes
      .filterNot(p => containedNodes(p.id))
      .flatMap(p => toParentConnections(viewConfig.pageChange.page, p.id))

    changes.consistent merge GraphChanges(addEdges = toContain)
  }
}
