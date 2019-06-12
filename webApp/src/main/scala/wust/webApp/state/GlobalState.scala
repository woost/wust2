package wust.webApp.state

import wust.facades.draggable.{MirrorOptions, Options, Sortable}
import wust.facades.googleanalytics.Analytics
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom.experimental.permissions.PermissionState
import org.scalajs.dom.raw.HTMLElement
import rx._
import wust.webUtil.{ModalConfig, Ownable}
import wust.webUtil.outwatchHelpers._
import wust.api._
import wust.graph._
import wust.ids._
import wust.sdk._
import wust.webApp.dragdrop.SortableEvents
import wust.webApp.jsdom.Notifications
import wust.webApp.views._

import scala.collection.{breakOut, mutable}
import scala.scalajs.js

class GlobalState(
  val appUpdateIsAvailable: Observable[Unit],
  val eventProcessor: EventProcessor,
  val leftSidebarOpen: Var[Boolean], //TODO: replace with ADT Open/Closed
  val showTagsList: Var[Boolean], //TODO: replace with ADT Open/Closed
  val showFilterList: Var[Boolean], //TODO: replace with ADT Open/Closed
  val urlConfig: Var[UrlConfig],
  val isOnline: Rx[Boolean],
  val isLoading: Rx[Boolean],
  val hasError: Rx[Boolean],
  val fileDownloadBaseUrl: Rx[Option[String]],
  val screenSize: Rx[ScreenSize],
)(implicit ctx: Ctx.Owner) {

  val hoverNodeId: Var[Option[NodeId]] = Var(None)

  val askedForUnregisteredUserName: Var[Boolean] = Var(false)
  val askedForNotifications: Var[Boolean] = Var(false)

  val rightSidebarNode: Var[Option[FocusPreference]] = Var(None)

  val auth: Rx[Authentication] = eventProcessor.currentAuth.unsafeToRx(seed = eventProcessor.initialAuth)
  val user: Rx[AuthUser] = auth.map(_.user)
  val userId: Rx[UserId] = user.map(_.id)

  val uploadingFiles: Var[Map[NodeId, UploadingFile]] = Var(Map.empty)

  val uiSidebarConfig: PublishSubject[Ownable[GenericSidebar.SidebarConfig]] = PublishSubject()
  val uiSidebarClose: PublishSubject[Unit] = PublishSubject()
  val uiModalConfig: PublishSubject[Ownable[ModalConfig]] = PublishSubject()
  val uiModalClose: PublishSubject[Unit] = PublishSubject()

  val rawGraph: Rx[Graph] = {
    val internalGraph = eventProcessor.graph.unsafeToRx(seed = Graph.empty)

    Rx {
      val graph = internalGraph()
      val u = user()
      val newGraph =
        if (graph.contains(u.id)) graph
        else {
          graph.addNodes(
            // these nodes are obviously not in the graph for an assumed user, since the user is not persisted yet.
            // if we start with an assumed user and just create new channels we will never get a graph from the backend.
            user().toNode ::
            Nil
          )
        }

      newGraph
    }
  }

  val viewConfig: Rx[ViewConfig] = {

    var lastViewConfig: ViewConfig = ViewConfig(View.Empty, Page.empty)

    val viewAndPageAndSanitizedPage: Rx[(Option[View], Page, Page)] = Rx {
      val rawPage = urlConfig().pageChange.page
      val rawView = urlConfig().view
      (rawView, rawPage, rawPage.copy(rawPage.parentId.filter(rawGraph().contains)))
    }

    Rx {
      if (!isLoading()) {
        val tmp = viewAndPageAndSanitizedPage()
        val (rawView, rawPage, sanitizedPage) = tmp

        val visibleView: View.Visible = rawPage.parentId match {
          case None => rawView match {
            case Some(view: View.Visible) if !view.isContent => view
            case _ => View.Welcome
          }
          case Some(parentId) =>
            val bestView = ViewHeuristic(rawGraph.now, parentId, rawView).getOrElse(View.Empty) // use rawGraph.now to not trigger on every graph change
            scribe.debug(s"View heuristic chose new view (was $rawView): $bestView")
            bestView
        }

        lastViewConfig = ViewConfig(visibleView, sanitizedPage)
      }

      lastViewConfig
    }
  }



  val view: Rx[View.Visible] = viewConfig.map(_.view)

  val urlPage = urlConfig.map(_.pageChange.page)
  val page: Rx[Page] = viewConfig.map(_.page)
  val pageWithoutReload: Rx[Page] = viewConfig.map(_.page)
  val pageNotFound:Rx[Boolean] = Rx{ !urlConfig().pageChange.page.parentId.forall(rawGraph().contains) }

  val pageHasNotDeletedParents = Rx {
    page().parentId.exists(rawGraph().hasNotDeletedParents)
  }
  val selectedNodes: Var[List[NodeId]] = Var(Nil)

  val addNodesInTransit: Rx[collection.Set[NodeId]] = {
    val changesAddNodes = eventProcessor.changesInTransit
      .map(changes => changes.flatMap(_.addNodes.map(_.id))(breakOut): mutable.HashSet[NodeId])
      .unsafeToRx(mutable.HashSet.empty)

    Rx {
      changesAddNodes() ++ uploadingFiles().keySet
    }
  }

  val isSynced: Rx[Boolean] = eventProcessor.changesInTransit.map(_.isEmpty).unsafeToRx(true)

  //TODO: wait for https://github.com/raquo/scala-dom-types/pull/36
//  val documentIsVisible: Rx[Boolean] = {
//    def isVisible = dom.document.visibilityState.asInstanceOf[String] == VisibilityState.visible.asInstanceOf[String]
//
//    events.window.eventProp[dom.Event]("visibilitychange").map(_ => isVisible).unsafeToRx(isVisible)
//  }
  val permissionState: Rx[PermissionState] = Notifications.createPermissionStateRx()
  permissionState.triggerLater { state =>
    if(state == PermissionState.granted || state == PermissionState.denied)
      Analytics.sendEvent("notification", state.asInstanceOf[String])
  }

  val pageStyle = Rx {
    PageStyle(view(), page())
  }

  val topbarIsVisible:Rx[Boolean] = Rx{ screenSize() != ScreenSize.Small }
  @inline def smallScreen: Boolean = screenSize.now == ScreenSize.Small
  @inline def largeScreen: Boolean = screenSize.now == ScreenSize.Large

  val sortable = new Sortable(js.Array[HTMLElement](), new Options {
    draggable = ".draggable"
    handle = ".draghandle"
    delay = 200.0 // prevents drag when touch scrolling is intended
    mirror = new MirrorOptions {
      constrainDimensions = true
      appendTo = "#draggable-mirrors"
    }
  })
  val sortableEvents = new SortableEvents(this, sortable)


  val defaultTransformations: Seq[UserViewGraphTransformation] = Seq(GraphOperation.ExcludeDeletedChildren, GraphOperation.AutomatedHideTemplates)
  // Allow filtering / transformation of the graph on globally
  val graphTransformations: Var[Seq[UserViewGraphTransformation]] = Var(defaultTransformations)

  // transform graph with graphTransformations
  val graph: Rx[Graph] = Rx {
    val graphTrans = graphTransformations()
    val currentGraph = rawGraph()
    val currentUserId = userId()
    val currentPage = urlPage.now // use now because we do not want to trigger on page change but wait for the new raw graph, coming from each page change.

    currentPage.parentId.fold(currentGraph) { parentId =>
      if (currentGraph.contains(parentId) && graphTrans.nonEmpty) GraphOperation.filter(currentGraph, parentId, currentUserId, graphTrans)
      else currentGraph
    }
  }
  val isFilterActive: Rx[Boolean] = Rx { graphTransformations().length != 2 || defaultTransformations.exists(t => !graphTransformations().contains(t)) }

  
  def toFocusState(viewConfig: ViewConfig): Option[FocusState] = viewConfig.page.parentId.map { parentId =>
    FocusState(viewConfig.view, parentId, parentId, isNested = false, view => urlConfig.update(_.focus(view)), nodeId => urlConfig.update(_.focus(Page(nodeId))))
  }
}

