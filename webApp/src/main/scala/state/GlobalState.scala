package wust.webApp.state

import wust.webApp.BrowserDetect
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
import wust.webApp.views.GraphOperation.{GraphFilter, GraphTransformation}

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
  val leftSidebarOpen: Var[Boolean], //TODO: replace with ADT Open/Closed
  val showTagsList: Var[Boolean], //TODO: replace with ADT Open/Closed
  val urlConfig: Var[UrlConfig],
  val isOnline: Rx[Boolean],
  val isLoading: Rx[Boolean],
  val hasError: Rx[Boolean],
  val fileDownloadBaseUrl: Rx[Option[String]],
  val screenSize: Rx[ScreenSize],
)(implicit ctx: Ctx.Owner) {

  val rightSidebarNode: Var[Option[NodeId]] = Var(None)

  val auth: Rx[Authentication] = eventProcessor.currentAuth.unsafeToRx(seed = eventProcessor.initialAuth)
  val user: Rx[AuthUser] = auth.map(_.user)

  val uploadingFiles: Var[Map[NodeId, UploadingFile]] = Var(Map.empty)

  val uiSidebarConfig: PublishSubject[Ownable[UI.SidebarConfig]] = PublishSubject()
  val uiSidebarClose: PublishSubject[Unit] = PublishSubject()
  val uiModalConfig: PublishSubject[Ownable[UI.ModalConfig]] = PublishSubject()
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
            val bestView = ViewHeuristic(rawGraph.now, parentId, rawView) // use rawGraph.now to not trigger on every graph change
            scribe.debug(s"View heuristic chose new view (was $rawView): $bestView")
            bestView
        }

        lastViewConfig = ViewConfig(visibleView, sanitizedPage)
      }

      lastViewConfig
    }
  }



  val view: Rx[View.Visible] = viewConfig.map(_.view)

  val page: Rx[Page] = viewConfig.map(_.page)
  val pageWithoutReload: Rx[Page] = viewConfig.map(_.page)
  val pageNotFound:Rx[Boolean] = Rx{ !urlConfig().pageChange.page.parentId.forall(rawGraph().contains) }

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

  val pageStyle = Rx {
    PageStyle(view(), page())
  }

  val jsErrors: Observable[String] = events.window.onError.map(_.message)

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


  val defaultTransformations: Seq[UserViewGraphTransformation] = Seq(GraphOperation.NoDeletedParents, GraphOperation.AutomatedHideTemplates)
  // Allow filtering / transformation of the graph on globally
  val graphTransformations: Var[Seq[UserViewGraphTransformation]] = Var(defaultTransformations)

  // transform graph with graphTransformations
  val graph: Rx[Graph] = for {
    graphTrans <- graphTransformations
    currentGraph <- rawGraph
    u <- user.map(_.id)
    p <- urlConfig.map(_.pageChange.page.parentId)
  } yield {
    val filterSeq: Seq[GraphFilter] = graphTrans.map(_.filterWithViewData(p, u))
    val filter = filterSeq.reduce(GraphOperation.stackFilter(_,_))
    val transformation = GraphOperation.filterToTransformation(filter)
    transformation(currentGraph)
  }
  val isFilterActive: Rx[Boolean] = Rx { graphTransformations().length != 2 || defaultTransformations.exists(t => !graphTransformations().contains(t)) }

}

