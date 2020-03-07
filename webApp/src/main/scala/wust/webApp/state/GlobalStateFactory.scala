package wust.webApp.state

import wust.sdk.EventProcessor.AppliedChangeResult
import wust.facades.jsSha256.Sha256
import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.experimental.permissions.PermissionState
import org.scalajs.dom.window
import outwatch.helpers.OutwatchTracing
import colibri.ext.monix._
import colibri._
import rx._
import wust.api.ApiEvent.ReplaceGraph
import wust.facades.wdtEmojiBundle.wdtEmojiBundle
import wust.graph._
import wust.ids._
import wust.util.StringOps
import wust.webApp.jsdom.ServiceWorker
import wust.webApp.views.{ EditableContent, MainTutorial }
import wust.webApp.{ Client, DevOnly, DebugOnly }
import wust.webUtil.UI.ToastLevel
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ BrowserDetect, UI }
import rx.async._
import rx.async.Platform._

import scala.concurrent.duration._
import wust.facades.segment.Segment
import wust.api.AuthUser

object GlobalStateFactory {
  def init(): Unit = {

    import GlobalState._

    DevOnly {
      setupStateDebugLogging()
    }

    eventProcessor.forbiddenChanges.foreach { changesList =>
      val changes = changesList.foldLeft(GraphChanges.empty)(_ merge _)

      // TODO: this is imperfect, but currently our permission detection in the frontend is
      // bad and we do changes even when reading -- so need to not show these errors there.
      val showHeuristic =
        changes.addNodes.nonEmpty || changes.delEdges.nonEmpty || changes.addEdges.exists(_.data.tpe == EdgeData.Child.tpe)

      if (showHeuristic) {
        UI.toast("You don't have sufficient permissions to do this.", title = "Forbidden", level = ToastLevel.Error)
      }
    }

    // on load, submit pending changes from localstorage
    Client.storage.getDecodablePendingGraphChanges(GlobalState.userId.now).foreach(eventProcessor.changesWithoutEnrich.onNext)

    // from this point on, backup every change in localstorage (including applied automations)
    eventProcessor.localChanges.foreach (change => Client.storage.addPendingGraphChange(GlobalState.userId.now, change))

    // after changes are synced, clear pending changes
    eventProcessor.sendingChanges.foreach { status =>
      status.result match {
        case AppliedChangeResult.Success | AppliedChangeResult.Rejected => Client.storage.deletePendingGraphChanges(GlobalState.userId.now, status.changes)
        case AppliedChangeResult.TryLater                               => // keep in storage
      }
    }

    Observable.merge(EditableContent.currentlyEditing, UI.currentlyEditing).subscribe(eventProcessor.stopEventProcessing)

    // on mobile left and right sidebars overlay the screen.
    // close the right sidebar when the left sidebar is opened on mobile.
    // you can never open the right sidebar when the left sidebar is open,
    if (BrowserDetect.isMobile) {
      leftSidebarOpen.triggerLater { open =>
        if (open) {
          rightSidebarNode() = None
        }
      }
    }

    // on desktop, we have our custom emoji picker, which should be close when the rightsidebar is opened or closed.
    // or the modal is closed or opened
    if (!BrowserDetect.isMobile) {
      mouseClickInMainView.foreach { _ =>
        wdtEmojiBundle.close()
      }

      rightSidebarNode.foreach { focusPreferenceOpt =>
        wdtEmojiBundle.close()
        focusPreferenceOpt.foreach { focusPreference =>
          GlobalState.graph.now.nodesById(focusPreference.nodeId).foreach { node =>
            node.role match {
              case NodeRole.Project => FeatureState.use(Feature.OpenProjectInRightSidebar)
              case NodeRole.Task    => FeatureState.use(Feature.OpenTaskInRightSidebar)
              case NodeRole.Message => FeatureState.use(Feature.OpenMessageInRightSidebar)
              // case NodeRole.Note    => FeatureState.use(Feature.OpenNoteInRightSidebar)
              case _                =>
            }
          }
        }
      }

      uiModalClose.foreach { _ =>
        wdtEmojiBundle.close()
      }

      uiModalConfig.foreach { _ =>
        wdtEmojiBundle.close()
      }
    }

    def closeAllOverlays(): Unit = {
      uiSidebarClose.onNext(())
      uiModalClose.onNext(())

      if (urlConfig.now.focusId.isEmpty) rightSidebarNode() = None // keep sidebar for urlconfig with focus
    }

    urlConfig.map(_.pageChange.page).triggerLater {
      closeAllOverlays()
      GlobalState.resetGraphTransformation()
      GlobalState.clearSelectedNodes()
      GlobalState.automationIsDisabled() = false
      // The current step is createProject, because the page change happens before the viewswitcher is rendered. The rendering of the viewswitcher causes the tutoraial to advance to the next step via onDomMountContinue.
      if (!MainTutorial.currentStep.contains(MainTutorial.step.createProject)) {
        MainTutorial.endTour()
      }
    }
    viewIsContent.triggerLater { isContent =>
      if (!isContent) {
        closeAllOverlays()
      }
    }

    // automatically notify visited nodes and add self as member
    def enrichVisitedGraphWithSideEffects(page: Page, graph: Graph): Graph = {
      //TODO: userdescendant
      val user = GlobalState.user.now

      page.parentId.fold(graph) { parentId =>
        val userIdx = graph.idToIdx(user.id)
        graph.idToIdxFold(parentId)(graph) { pageIdx =>
          def anyPageParentIsPinned = graph.anyAncestorOrSelfIsPinned(Array(pageIdx), user.id)
          def pageIsInvited = userIdx.fold(false)(userIdx => graph.inviteNodeIdx.contains(userIdx)(pageIdx))

          val edgeChanges = if (!anyPageParentIsPinned && !pageIsInvited) {
            GraphChanges.connect(Edge.Notify)(parentId, user.id)
              .merge(GraphChanges.connect(Edge.Pinned)(parentId, user.id))
          } else GraphChanges.empty

          val allChanges = edgeChanges
          if (allChanges.nonEmpty) {
            eventProcessor.changes.onNext(allChanges)
            graph.applyChanges(allChanges)
          } else graph
        }
      }
    }

    def getNewGraph(page: Page) = {
      isLoading() = true
      val graph = for {
        graph <- Client.api.getGraph(page)
      } yield enrichVisitedGraphWithSideEffects(page, graph)

      graph.transform { result =>
        isLoading() = false
        result
      }
    }

    // if we have a payment success on startup, then notify user.
    urlConfig.now.info.foreach {
      case InfoContent.PaymentSucceeded => UI.toast("Successfully upgraded Payment Plan.", level = UI.ToastLevel.Success)
    }

    // if we have an invitation token, we merge this invited user into our account and get the graph again.
    urlConfig.foreach { viewConfig =>
      // handle invittation token
      viewConfig.invitation foreach { inviteToken =>
        val wasAssumed = GlobalState.auth.now.user match {
          case _: AuthUser.Assumed => true
          case _                   => false
        }
        Client.auth.acceptInvitation(inviteToken).foreach {
          case () =>
            //clear the invitation from the viewconfig and url
            urlConfig.update(_.copy(invitation = None))

            // get a new graph with new right after the accepted invitation
            getNewGraph(viewConfig.pageChange.page).foreach { graph =>
              eventProcessor.localEvents.onNext(ReplaceGraph(graph))
            }
            if (wasAssumed) {
              Segment.trackEvent("New Unregistered User", js.Dynamic.literal(`type` = "invite", via = "token"))
            }
        }
        //TODO: signal status of invitation to user in UI
      }

      // handle focus id
      viewConfig.focusId foreach { focusId =>
        //TODO better way to focus a single node in a page. for now just rightsidebar
        rightSidebarNode() = Some(FocusPreference(focusId))
      }
    }

    // clear selected nodes on view and page change
    {
      val clearTrigger = Rx {
        view()
        page()
        ()
      }
      clearTrigger.foreach { _ => clearSelectedNodes() }
    }

    //TODO: better in rx/obs operations
    // store auth in localstore and send to serviceworker
    val authWithPrev = auth.fold((auth.now, auth.now)) { (prev, auth) => (prev._2, auth) }
    authWithPrev.foreach {
      case (prev, auth) =>
        if (prev != auth) {
          Client.storage.auth() = Some(auth)
        }

        ServiceWorker.sendAuth(auth)
    }

    // whenever a new serviceworker registers, we need to resync the auth, so the worker knows about it.
    // use-case: initial page load, where a new serviceworker is installed. Our first sendauth on
    // auth-change did not get through because no serviceworker there. So need to send again,
    // when serviceworker is registered.
    serviceWorkerIsActivated.foreach { _ =>
      ServiceWorker.sendAuth(auth.now)
    }

    //TODO: better build up state from server events?
    // only adding a new channel would normally get a new graph. but we can
    // avoid this here and do nothing. Optimistic UI. We just integrate this
    // change into our local state without asking the backend. when the page
    // changes, we get a new graph. except when it is just a Page.NewChannel.
    // There we want tnuro issue the new-channel change.
    {
      val urlConfigAndUser = Rx {
        (urlConfig(), user().toNode)
      }

      var lastTransitChanges: List[GraphChanges] = Client.storage.getDecodablePendingGraphChanges(GlobalState.userId.now)
      eventProcessor.changesInTransit.foreach { lastTransitChanges = _ }

      var isFirstGraphRequest = true
      var prevPage: PageChange = null
      var prevUser: Node.User = null

      urlConfigAndUser.toObservable
        .filter {
          case (urlConfig, user) =>
            @inline def userWasChanged = prevUser == null || prevUser.id != user.id || prevUser.data.isImplicit != user.data.isImplicit
            @inline def pageWasChanged = prevPage == null || prevPage != urlConfig.pageChange
            @inline def needsGet = urlConfig.pageChange.needsGet
            @inline def pageChangeNonEmpty = urlConfig.pageChange.page.nonEmpty
            // TODO: this is a workaround for unread-edges that are not synced and we want the right status in the acitivty/notification view...
            @inline def refreshForActivity = (prevPage == urlConfig.pageChange) && (urlConfig.view == Some(View.Notifications) || urlConfig.view == Some(View.ActivityStream))

            val result = userWasChanged || refreshForActivity || (pageWasChanged && needsGet && (pageChangeNonEmpty || isFirstGraphRequest))

            prevPage = urlConfig.pageChange
            prevUser = user
            isFirstGraphRequest = false

            result
        }.switchMap {
          case (urlConfig, user) =>
            val currentTransitChanges = lastTransitChanges.fold(GraphChanges.empty)(_ merge _)
            Observable
              .fromFuture(getNewGraph(urlConfig.pageChange.page))
              .recover { case _ => Graph.empty }
              .map(g => ReplaceGraph(g.applyChanges(currentTransitChanges)))
        }
        .subscribe(eventProcessor.localEvents)
    }

    GlobalState.permissionState.triggerLater { state =>
      if (state == PermissionState.granted || state == PermissionState.denied)
        Segment.trackEvent("Changed Notification Permission", js.Dynamic.literal(state = state.asInstanceOf[String]))

      if (state == PermissionState.granted)
        FeatureState.use(Feature.EnableBrowserNotifications)
    }

    val titleSuffix = if (DevOnly.isTrue) "dev" else "Woost"
    // switch to View name in title if view switches to non-content
    Rx {
      if (viewIsContent()) {
        val channelName = page().parentId.flatMap(id => graph().nodesById(id).map(n => StringOps.trimToMaxLength(n.str, 30))).map(EmojiTitleConverter.emojiTitleConvertor.replace_colons_safe)
        window.document.title = channelName.fold(titleSuffix)(name => s"${if (name.contains("unregistered-user")) "Unregistered User" else name} - $titleSuffix")
      } else {
        window.document.title = s"${view().toString} - $titleSuffix"
      }
    }

    // we know that an update is available if the client is offline but the browser is online. This happens, because
    // every update bumps the version of the endpoint url: core-v1_2-3.app.woost.space.
    isClientOnline
      .sample(2.minutes)
      .foreach { isOnline =>
        if (!isOnline) {
          scribe.info("Client is offline, checking whether backend is online")
          // if we can access the health check of core.app.woost.space (without version in name) and not the versioned one.
          // then we know for sure, we can update:
          Client.unversionedBackendIsOnline().foreach { isOnline =>
            if (isOnline) {
              scribe.info("Unversioned Backend is online.")
              Client.versionedBackendIsOnline().foreach { isOnline =>
                if (isOnline) {
                  scribe.info("Versioned Backend is online.")
                } else {
                  scribe.info("Versioned Backend is offline, reloading.")
                  Segment.trackEvent("App updated", js.Dynamic.literal(synced = GlobalState.isSynced.now))
                  window.location.reload(flag = true)
                }
              }
            } else {
              scribe.info("Unversioned Backend is offline.")
            }
          }
        }
      }

    Client.apiErrorSubject.foreach { _ =>
      scribe.error("API request did fail, because the API is incompatible")
      hasError() = true
      Segment.trackError("API request failed", "API incompatible")
    }
    OutwatchTracing.error.foreach{ t =>
      scribe.error("Error in outwatch component", t)
      hasError() = true
      Segment.trackError("Error in Outwatch Component", t.getMessage())
    }

    // we send client errors from javascript to the backend
    outwatch.dsl.events.window.onError.foreach({ (e: dom.ErrorEvent) =>
      e.message match {
        case "Uncaught TypeError: Cannot read property 'insertBefore' of null" => // draggable
        case _ =>
          Client.api.log(s"Javascript Error: ${e.message}.")
          Segment.trackError("Javascript Error", e.message)
          DevOnly { UI.toast(e.message, level = ToastLevel.Error) }
      }
    })

    GlobalState.userId.foreach { userId =>
      GlobalState.askedForUnregisteredUserName() = false
    }

    GlobalState.userId.foreach { userId =>
      val uuid: String = userId.toUuid.toString
      val hashedUserId = Sha256.sha224(uuid)
      Segment.identify(hashedUserId)
    }
    GlobalState.auth.foreach { auth =>
      auth.user match {
        case _: AuthUser.Assumed if GlobalState.urlConfig.now.pageChange.page.isEmpty && GlobalState.urlConfig.now.invitation.isEmpty => Segment.trackEvent("New Unregistered User", js.Dynamic.literal(`type` = "organic"))
        case _: AuthUser.Assumed if GlobalState.urlConfig.now.pageChange.page.nonEmpty && GlobalState.urlConfig.now.invitation.isEmpty => Segment.trackEvent("New Unregistered User", js.Dynamic.literal(`type` = "invite", via = "link"))
        case _ =>
      }
    }
    GlobalState.presentationMode.foreach { presentationMode =>
      Segment.trackEvent("Presentation Mode", js.Dynamic.literal(mode = presentationMode.toString))
    }

    GlobalState.urlConfig.map(_.pageChange.page).foreach { rawPage =>
      rawPage.parentId.foreach {pageId =>
        Segment.trackEvent("urlPage", js.Dynamic.literal(pageIdBase58 = pageId.toBase58, pageIdUuid = pageId.toUuid.toString))
      }
    }

    GlobalState.view.toObservable.dropWhile(_ == View.Empty).filter(_ => !GlobalState.showPageNotFound.now).foreach { view =>
      // DebugOnly { UI.toast("", title = view.viewKey, level = ToastLevel.Info) }
      Segment.page(view.viewKey)
    }

    // debounce: to skip an intermediate state that satisfies pageNotFound conditions
    GlobalState.showPageNotFound.toObservable.debounce(200 millis).foreach { notFound =>
      if (notFound) {
        DebugOnly { UI.toast("", title = "Page Not Found", level = ToastLevel.Warning) }
        Segment.page("PageNotFound")
      }
    }
  }

  private var stateDebugLoggingEnabled = false
  def setupStateDebugLogging(): Unit = {
    if (!stateDebugLoggingEnabled) {
      stateDebugLoggingEnabled = true
      import GlobalState._

      rawGraph.debugWithDetail((g: Graph) => s"rawGraph: ${g.toString}", (g: Graph) => g.toDetailedString)
      graph.debugWithDetail((g: Graph) => s"graph: ${g.toString}", (g: Graph) => g.toDetailedString)

      showPageNotFound.debug("showPageNotFound")
      screenSize.debug("screenSize")
      urlConfig.debug("urlConfig")
      page.debug("page")
      view.debug("view")
      user.debug("auth")
      selectedNodes.debug("selected")
    }
  }
}
