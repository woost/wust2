package wust.webApp.state

import draggable._
import googleAnalytics.Analytics
import monix.eval.Task
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import monocle.macros.GenLens
import org.scalajs.dom.experimental.permissions.PermissionState
import org.scalajs.dom.raw.HTMLElement
import outwatch.dom.dsl._
import rx._
import wust.api._
import wust.graph._
import wust.ids._
import wust.sdk._
import wust.webApp.dragdrop.SortableEvents
import wust.webApp.jsdom.Notifications
import wust.webApp.outwatchHelpers._
import wust.webApp.views._
import wust.css.Styles
import wust.util.algorithm
import wust.webApp.Ownable
import wust.webApp.views.GraphOperation.GraphTransformation

import scala.collection.breakOut
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.scalajs.js

sealed trait UploadingFile
object UploadingFile {
  case class Waiting(dataUrl: String) extends UploadingFile
  case class Error(dataUrl: String, retry: Task[Unit]) extends UploadingFile
}

class GlobalState(
  val appUpdateIsAvailable: Observable[Unit],
  val eventProcessor: EventProcessor,
  val sidebarOpen: Var[Boolean], //TODO: replace with ADT Open/Closed
  val rawViewConfig: Var[ViewConfig],
  val isOnline: Rx[Boolean],
  val isLoading: Rx[Boolean],
  val hasError: Rx[Boolean],
  val fileDownloadBaseUrl: Rx[Option[String]],
)(implicit ctx: Ctx.Owner) {

  val auth: Rx[Authentication] = eventProcessor.currentAuth.unsafeToRx(seed = eventProcessor.initialAuth)
  val user: Rx[AuthUser] = auth.map(_.user)

  val uploadingFiles: Var[Map[NodeId, UploadingFile]] = Var(Map.empty)

  val modalConfig: PublishSubject[Ownable[UI.ModalConfig]] = PublishSubject()

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

  // Allow filtering / transformation of the graph on globally
  val graphTransformations: Var[Seq[UserViewGraphTransformation]] = Var(Seq.empty[UserViewGraphTransformation])

  // Always transform graph - using identity on default
  case class ViewGraphData(page: Page, userId: UserId, graph: Graph)
  val graph: Rx[Graph] = Rx {
    val currGraph: Graph = rawGraph()
    val transformation: Seq[GraphTransformation] = graphTransformations().map(_.transformWithViewData(page().parentId, user().id))
    transformation.foldLeft(currGraph)((g, gt) => gt(g))
  }

  val viewConfig: Var[ViewConfig] = rawViewConfig.mapRead{ viewConfig =>
    val page = viewConfig().pageChange.page
    val sanitizedPage = page.copy(page.parentId.filter(rawGraph().contains))
    viewConfig().copy(pageChange = viewConfig().pageChange.copy(page = sanitizedPage))
  }

  val page: Rx[Page] = viewConfig.map(_.pageChange.page)
  val pageWithoutReload: Rx[Page] = viewConfig.map(_.pageChange.page)
  val pageNotFound:Rx[Boolean] = Rx{ !rawViewConfig().pageChange.page.parentId.forall(rawGraph().contains) }

  val pageHasParents = Rx {
    page().parentId.exists(rawGraph().hasNotDeletedParents)
  }
  val selectedNodes: Var[List[NodeId]] = Var(Nil)

  val channelForest: Rx[Seq[Tree]] = Rx { rawGraph().channelTree(user().id) }
  val channels: Rx[Seq[(Node,Int)]] = Rx { channelForest().flatMap(_.flattenWithDepth()).distinct }

  val addNodesInTransit: Rx[Set[NodeId]] = {
    val changesAddNodes = eventProcessor.changesInTransit
      .map(changes => changes.flatMap(_.addNodes.map(_.id))(breakOut): Set[NodeId])
      .unsafeToRx(Set.empty)

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

  val view: Var[View] = rawViewConfig.zoom { viewConfig =>
    if(!viewConfig.view.isContent || viewConfig.pageChange.page.parentId.nonEmpty)
      viewConfig.view
    else
      View.Welcome
  }((viewConfig, view) => viewConfig.copy(view = view))

  val pageStyle = Rx {
    PageStyle(view(), page())
  }

  val jsErrors: Observable[String] = events.window.onError.map(_.message)

  val screenSize: Rx[ScreenSize] = events.window.onResize
    .debounce(0.2 second)
    .map(_ => ScreenSize.calculate())
    .unsafeToRx(ScreenSize.calculate())

  @inline def smallScreen: Boolean = screenSize.now == ScreenSize.Small
  @inline def largeScreen: Boolean = screenSize.now == ScreenSize.Large

  val sortable = new Sortable(js.Array[HTMLElement](), new Options {
    draggable = ".draggable"
    handle = ".draghandle"
    delay = 200.0 // prevents drag when touch scrolling is intended
    mirror = new MirrorOptions {
      constrainDimensions = true
    }
  })
  val sortableEvents = new SortableEvents(this, sortable)
}

