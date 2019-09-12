package wust.webApp.state

import java.util.concurrent.TimeUnit

import wust.webApp.views.MainTutorial

import wust.facades.googleanalytics.Analytics
import wust.facades.hotjar
import monix.eval.Task
import monix.reactive.Observable
import org.scalajs.dom
import org.scalajs.dom.window
import outwatch.ext.monix._
import outwatch.dom.helpers.OutwatchTracing
import rx._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ BrowserDetect, UI }
import UI.ToastLevel
import wust.api.ApiEvent.ReplaceGraph
import wust.graph._
import wust.ids._
import wust.sdk._
import wust.util.StringOps
import wust.webApp.jsdom.{ Navigator, ServiceWorker }
import wust.webApp.parsers.{ UrlConfigParser, UrlConfigWriter }
import wust.webApp.views.EditableContent
import wust.webApp.{ Client, DevOnly }
import wust.facades.wdtEmojiBundle.wdtEmojiBundle
import UI.ToastLevel
import org.scalajs.dom.experimental.permissions.PermissionState

import scala.concurrent.duration._
import scala.util.{ Failure, Success }

object GlobalStateFactory {
  def init(): Unit = {

    import GlobalState._

    Observable(EditableContent.currentlyEditing, UI.currentlyEditing).merge.subscribe(eventProcessor.stopEventProcessing)

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
      graphTransformations() = defaultTransformations
      GlobalState.clearSelectedNodes()
      // The current step is createProject, because the page change happens before the viewswitcher is rendered. The rendering of the viewswitcher causes the tutoraial to advance to the next step via onDomMountContinue.
      if(!MainTutorial.currentStep.contains(MainTutorial.step.createProject)) {
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

    // if we have a invitation token, we merge this invited user into our account and get the graph again.
    urlConfig.foreach { viewConfig =>
      // handle invittation token
      viewConfig.invitation foreach { inviteToken =>
        Client.auth.acceptInvitation(inviteToken).foreach {
          case () =>
            //clear the invitation from the viewconfig and url
            urlConfig.update(_.copy(invitation = None))

            // get a new graph with new right after the accepted invitation
            getNewGraph(viewConfig.pageChange.page).foreach { graph =>
              eventProcessor.localEvents.onNext(ReplaceGraph(graph))
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

    GlobalState.auth.foreach { auth =>
      Analytics.setUserId(auth.user.id.toUuid.toString)
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
      val userAndPage = Rx {
        (urlConfig(), user().toNode)
      }

      var lastTransitChanges: List[GraphChanges] = Nil
      eventProcessor.changesInTransit.foreach { lastTransitChanges = _ }

      var isFirstGraphRequest = true
      var prevPage: PageChange = null
      var prevUser: Node.User = null

      userAndPage.toObservable
        .filter {
          case (viewConfig, user) =>
            @inline def userWasChanged = prevUser == null || prevUser.id != user.id || prevUser.data.isImplicit != user.data.isImplicit
            @inline def pageWasChanged = prevPage == null || prevPage != viewConfig.pageChange
            @inline def needsGet = viewConfig.pageChange.needsGet
            @inline def pageChangeNonEmpty = viewConfig.pageChange.page.nonEmpty

            val result = userWasChanged || (pageWasChanged && needsGet && (pageChangeNonEmpty || isFirstGraphRequest))

            prevPage = viewConfig.pageChange
            prevUser = user
            isFirstGraphRequest = false

            result
        }.switchMap {
          case (viewConfig, user) =>
            val currentTransitChanges = lastTransitChanges.fold(GraphChanges.empty)(_ merge _)
            Observable
              .fromFuture(getNewGraph(viewConfig.pageChange.page))
              .onErrorHandle(_ => Graph.empty)
              .map(g => ReplaceGraph(g.applyChanges(currentTransitChanges)))
        }
        .subscribe(eventProcessor.localEvents)
    }

    GlobalState.permissionState.triggerLater { state =>
      if (state == PermissionState.granted || state == PermissionState.denied)
        Analytics.sendEvent("browser-notification", state.asInstanceOf[String])

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
    //TODO with is browseronline: isBrowserOnlineObservable
    isClientOnlineObservable
      .lift[Observable]
      .throttleLast(2.minutes)
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
    }
    OutwatchTracing.error.foreach{ t =>
      scribe.error("Error in outwatch component", t)
      hasError() = true
    }
    hasError.foreach { error =>
      if (error) hotjar.pageView("/js-error")
    }

    // we send client errors from javascript to the backend
    dom.window.addEventListener("onerror", { (e: dom.ErrorEvent) =>
      Client.api.log(s"Javascript Error: ${e.message}.")
      DevOnly { UI.toast(e.message, level = ToastLevel.Error) }
    })

    DevOnly {
      setupStateDebugLogging()
    }

  }

  private var stateDebugLoggingEnabled = false
  def setupStateDebugLogging(): Unit = {
    if (!stateDebugLoggingEnabled) {
      stateDebugLoggingEnabled = true
      import GlobalState._

      rawGraph.debugWithDetail((g: Graph) => s"rawGraph: ${g.toString}", (g: Graph) => g.toDetailedString)
      graph.debugWithDetail((g: Graph) => s"graph: ${g.toString}", (g: Graph) => g.toDetailedString)

      screenSize.debug("screenSize")
      page.debug("page")
      view.debug("view")
      user.debug("auth")
      selectedNodes.debug("selected")
    }
  }
}
