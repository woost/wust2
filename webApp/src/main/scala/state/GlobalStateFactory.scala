package wust.webApp.state

import java.util.concurrent.TimeUnit

import colorado.HCL
import emojijs.EmojiConvertor
import org.scalajs.dom.console
import monix.eval.Task
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom.window
import rx._
import wust.api.ApiEvent.{NewGraphChanges, ReplaceGraph}
import wust.api._
import wust.graph._
import wust.ids._
import wust.sdk._
import wust.webApp.jsdom.{IndexedDbOps, Navigator, Notifications, ServiceWorker}
import wust.webApp.outwatchHelpers._
import wust.webApp.{BrowserDetect, Client, DevOnly}
import outwatch.dom.helpers.OutwatchTracing
import wust.util.StringOps
import wust.util.algorithm
import wust.webApp.views.UI

import scala.collection.breakOut
import scala.concurrent.duration._
import scala.scalajs.js
import scala.util.{Failure, Success}

object GlobalStateFactory {
  def create(swUpdateIsAvailable: Observable[Unit])(implicit ctx: Ctx.Owner): GlobalState = {
    val sidebarOpen = Client.storage.sidebarOpen.imap(_ getOrElse !BrowserDetect.isMobile)(Some(_)) // expanded sidebar per default for desktop
    val viewConfig = UrlRouter.variable.imap(_.fold(ViewConfig.default)(ViewConfig.fromUrlHash))(x => Option(ViewConfig.toUrlHash(x)))

    val eventProcessor = EventProcessor(
      Client.observable.event,
      (changes, graph) => applyEnrichmentToChanges(graph, viewConfig.now)(changes),
      changes => Client.api.changeGraph(changes.map(EmojiReplacer.replaceChangesToColons)),
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
          scribe.warn("Error getting file download base url, will retry in 30 seconds...", err)
          scheduleRenewal(seconds = 30)
      }
    }
    renewFileDownloadBaseUrl()

    // automatically notify visited nodes and add self as member
    def enrichVisitedGraphWithSideEffects(page: Page, graph: Graph): Graph = {
      //TODO: userdescendant
      val user = state.user.now

      page.parentId.fold(graph) { parentId =>
        val userIdx = graph.idToIdx(user.id)
        val pageIdx = graph.idToIdx(parentId)
        println("PAGE " + userIdx + pageIdx)
        if (userIdx >= 0 && pageIdx >= 0) {
          def anyPageParentIsPinned = graph.anyAncestorIsPinned(List(parentId), user.id)
          def pageIsInvited = graph.inviteNodeIdx.contains(userIdx)(pageIdx)
          def pageIsUnderUser: Boolean = algorithm.depthFirstSearchExists(start = pageIdx, graph.notDeletedParentsIdx, userIdx)
          def userIsMemberOfPage: Boolean = graph.membershipEdgeForNodeIdx.exists(pageIdx)(edgeIdx => graph.edgesIdx.a(edgeIdx) == userIdx)

          println("PAGE " + anyPageParentIsPinned + pageIsInvited + pageIsUnderUser + userIsMemberOfPage)

          if(!userIsMemberOfPage) {
            Client.api.addMember(parentId, user.id, AccessLevel.ReadWrite)
          }

          if(!anyPageParentIsPinned && !pageIsUnderUser && !pageIsInvited) {
            val changes = GraphChanges.connect(Edge.Notify)(parentId, user.id)
             .merge(GraphChanges.connect(Edge.Pinned)(user.id, parentId))
             .merge(GraphChanges.disconnect(Edge.Invite)(user.id, parentId))

            eventProcessor.changes.onNext(changes)

            graph.applyChanges(changes)
          } else graph
        } else graph
      }
    }

    def getNewGraph(page: Page) = for {
      graph <- Client.api.getGraph(page)
    } yield enrichVisitedGraphWithSideEffects(page, graph)


    // if we have a invitation token, we merge this invited user into our account and get the graph again.
    viewConfig.foreach { viewConfig =>
      viewConfig.invitation match {
        case Some(inviteToken) => Client.auth.acceptInvitation(inviteToken).foreach { case () =>
          //clear the invitation from the viewconfig and url
          state.viewConfig.update(_.copy(invitation = None))

          // get a new graph with new right after the accepted invitation
          getNewGraph(viewConfig.pageChange.page).foreach { graph =>
            eventProcessor.localEvents.onNext(ReplaceGraph(graph))
          }
        }
        case None => ()
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
    // store auth in localstore and send to serviceworker
    val authWithPrev = auth.fold((auth.now, auth.now)) { (prev, auth) => (prev._2, auth) }
    authWithPrev.foreach { case (prev, auth) =>
      if (prev != auth) {
        Client.storage.auth() = Some(auth)
      }

      ServiceWorker.sendAuth(auth)
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
            Observable.fromFuture(getNewGraph(pageChange.page))
          } else if (prevPage == null || prevPage != pageChange) {
            if (pageChange.needsGet && (!pageChange.page.isEmpty || isFirstGraphRequest)) {
              isLoading() = true
              Observable.fromFuture(getNewGraph(pageChange.page))
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
        val channelName = page().parentId.flatMap(id => graph().nodesByIdGet(id).map(n => StringOps.trimToMaxLength(n.str, 30))).map(EmojiTitleConverter.emojiTitleConvertor.replace_colons)
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
          scribe.info("Requesting updating from SW.")
          reg.update().toFuture.onComplete { res =>
            scribe.info(s"Result of update request: ${if(res.isSuccess) "Success" else "Failure"}.")
          }
        }))
      }

    // if there is a page change and we got an sw update, we want to reload the page
    appUpdateTrigger.withLatestFrom(appUpdateIsAvailable)((_, _) => Unit).foreach { _ =>
      scribe.info("Going to reload page, due to SW update.")
      // if flag is true, page will be reloaded without cache. False means it may use the browser cache.
      window.location.reload(flag = false)
    }

    // write all initial storage changes, in case they did not get through to the server
    // Client.storage.graphChanges.take(1).flatMap(Observable.fromIterable) subscribe eventProcessor.changes
    //TODO: wait for Storage.handlerWithEventsOnly
    //Client.storage.graphChanges.drop(1) subscribe eventProcessor.nonSendingChanges
    // eventProcessor.changesInTransit subscribe Client.storage.graphChanges.unsafeOnNext _

    //Client.storage.graphChanges.redirect[GraphChanges](_.scan(List.empty[GraphChanges])((prev, curr) => prev :+ curr) <-- eventProcessor.changes

    // we send client errors from javascript to the backend
    jsErrors.foreach { msg =>
      Client.api.log(s"Javascript Error: $msg.")
    }

    DevOnly {

     graph.debugWithDetail((g: Graph) => s"graph: ${g.toString}", (g:Graph) => g.toDetailedString)

      page.debug("page")
      view.debug("view")
      user.debug("auth")
    }

    state
  }

  object EmojiTitleConverter {
    val emojiTitleConvertor = new EmojiConvertor()
    emojiTitleConvertor.replace_mode = "unified"
    emojiTitleConvertor.allow_native = true
  }

  object EmojiReplacer {
    val emojiTextConvertor = new EmojiConvertor()
    emojiTextConvertor.colons_mode = true
    emojiTextConvertor.text_mode = true
    private def replaceToColons(nodes: Iterable[Node]): Set[Node] = nodes.collect {
      case n: Node.Content =>
        scribe.debug(s"replacing node emoji: ${n.str}.")
        val emojiData = n.data.updateStr(emojiTextConvertor.replace_unified(emojiTextConvertor.replace_emoticons(n.str)))
        scribe.debug(s"New representation: ${emojiData.str}.")
        n.copy(data = emojiData)
      case n => n
    }(breakOut)
    def replaceChangesToColons(graphChanges: GraphChanges) = graphChanges.copy(addNodes = replaceToColons(graphChanges.addNodes))
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
