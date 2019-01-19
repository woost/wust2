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
  val screenSize: Rx[ScreenSize],
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

  val viewConfig: Var[ViewConfig] = {
      def viewHeuristic(graph:Graph, page:Page):View = {
        val bestView:View = page.parentId.flatMap(graph.idToIdxGet) match {
          case Some(pageParentIdx) => graph.nodes(pageParentIdx).role match {
            case NodeRole.Project => View.Dashboard
            case _ =>
              val stats = graph.topLevelRoleStats(page.parentId)
              stats.mostCommonRole match {
                case NodeRole.Message => View.Conversation
                case NodeRole.Task => View.Tasks
                case _ => View.Dashboard
              }
          }
          case None => View.Dashboard
        }
        scribe.info(s"viewHeuristic: '$bestView'")
        bestView
      }

      var lastPage:Page = Page.empty
      var lastGraph:Graph = rawGraph.now
      var lastBestView:View = View.Dashboard

      rawViewConfig.mapRead{ viewConfig =>
        val rawPage = viewConfig().pageChange.page
        val sanitizedPage = rawPage.copy(rawPage.parentId.filter(rawGraph().contains))
        val pageChange = viewConfig().pageChange.copy(page = sanitizedPage)

        val view = (viewConfig().view, rawPage.parentId) match {
          case (None, None) => View.Welcome
          case (Some(view), None) => if(view.isContent) View.Welcome else view
          case (None, Some(pageParentId)) =>
            // no specific view was given. Select one automatically:

            (lastGraph != rawGraph(), lastPage != rawPage) match {
              case (false, _) =>
                // The graph didn't change, keep the current view.
                lastBestView
              case (true, false) =>
                // The graph changed without page-change (life-updates, graph-changes by the user).
                // We keep the current view.
                lastGraph = rawGraph()
                lastBestView
              case (true, true) =>
                // The graph changed and we are on a different page than before.
                // The user changed the page and waited for the new graph of getGraph. Now the graph is updated.
                // Therefore, we apply the heuristic to select a new best view.
                val bestView = viewHeuristic(rawGraph(), rawPage)
                lastBestView = bestView
                lastPage = rawPage
                lastGraph = rawGraph()
                bestView
            }
              case (Some(view), Some(_)) => view
        }

        viewConfig().copy(view = Some(view), pageChange = pageChange)
    }
  }

  val view: Rx[View] = viewConfig.map(_.view.get) // TODO: improve. we can do get, because viewConfig always has a view opposed to rawViewConfig

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
    p <- rawViewConfig.map(_.pageChange.page.parentId)
  } yield {
    val transformation: Seq[GraphTransformation] = graphTrans.map(_.transformWithViewData(p, u))
    transformation.foldLeft(currentGraph)((g, gt) => gt(g))
  }
  val isFilterActive: Rx[Boolean] = Rx { graphTransformations().length != 2 || defaultTransformations.exists(t => !graphTransformations().contains(t)) }

}

