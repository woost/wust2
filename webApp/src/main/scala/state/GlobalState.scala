package wust.webApp.state

import draggable._
import googleAnalytics.Analytics
import monix.reactive.Observable
import monocle.macros.GenLens
import org.scalajs.dom
import org.scalajs.dom.experimental.permissions.PermissionState
import org.scalajs.dom.raw.{HTMLElement, VisibilityState}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api._
import wust.graph._
import wust.ids._
import wust.sdk._
import wust.util.time.time
import wust.webApp.dragdrop.{DraggableEvents, SortableEvents}
import wust.webApp.jsdom.Notifications
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Components
import wust.css.Styles
import wust.util.algorithm

import scala.collection.mutable
import scala.collection.breakOut
import scala.concurrent.duration._
import scala.scalajs.js

class GlobalState(
  val appUpdateIsAvailable: Observable[Unit],
  val eventProcessor: EventProcessor,
  val sidebarOpen: Var[Boolean], //TODO: replace with ADT Open/Closed
  val viewConfig: Var[ViewConfig],
  val isOnline: Rx[Boolean],
  val isLoading: Rx[Boolean],
  val hasError: Rx[Boolean]
)(implicit ctx: Ctx.Owner) {

  val auth: Rx[Authentication] = eventProcessor.currentAuth.unsafeToRx(seed = eventProcessor.initialAuth)
  val user: Rx[AuthUser] = auth.map(_.user)

  val graph: Rx[Graph] = {
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

  val selectedNodes: Var[List[NodeId]] = Var(Nil)

  val channelForest: Rx[Seq[Tree]] = Rx { graph().channelTree(user().id) }
  val channels: Rx[Seq[(Node,Int)]] = Rx { channelForest().flatMap(_.flattenWithDepth()).distinct }

  val addNodesInTransit: Rx[Set[NodeId]] = eventProcessor.changesInTransit
    .map(changes => changes.flatMap(_.addNodes.map(_.id))(breakOut): Set[NodeId])
    .unsafeToRx(Set.empty)

  val isSynced: Rx[Boolean] = eventProcessor.changesInTransit.map(_.isEmpty).unsafeToRx(true)

  val pageChange: Var[PageChange] = viewConfig.zoom(GenLens[ViewConfig](_.pageChange))
  val page: Var[Page] = pageChange.zoom(GenLens[PageChange](_.page))

  val pageNotFound:Rx[Boolean] = Rx{ !page().parentId.forall(graph().contains) }

  val pageHasParents = Rx {
    page().parentId.exists(graph().hasParents)
  }

  Rx {
    //TODO: userdescendant

    def anyPageParentIsPinned = graph().anyAncestorIsPinned(page().parentId, user().id)
    def pageIsUnderUser:Boolean = (for {
      pageParentId <- page().parentId
      pageIdx = graph().idToIdx(pageParentId)
      if pageIdx != -1
      userIdx = graph().idToIdx(user().id)
      if userIdx != -1
    } yield algorithm.depthFirstSearchExists(start = pageIdx, graph().notDeletedParentsIdx, userIdx)).getOrElse(true)

    if(page().parentId.nonEmpty && !isLoading() && !anyPageParentIsPinned && !pageIsUnderUser) {
      // user probably clicked on a woost-link.
      // So we pin the page as channels and enable notifications
      val changes = page().parentId.foldLeft(GraphChanges.empty) {(changes,parentId) =>
        changes
        .merge(GraphChanges.connect(Edge.Notify)(parentId, user.now.id))
        .merge(GraphChanges.connect(Edge.Pinned)(user.now.id, parentId))
      }
      eventProcessor.changes.onNext(changes)
    }
  }

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

  val view: Var[View] = viewConfig.zoom(GenLens[ViewConfig](_.view)).mapRead { view =>
    if(!view().isContent || page().parentId.nonEmpty)
      view()
    else
      View.Welcome
  }

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

  val draggable = new Draggable(js.Array[HTMLElement](), new Options {
    draggable = ".draggable"
    handle = ".draghandle"
    delay = 200.0
    mirror = new MirrorOptions {
      constrainDimensions = true
    }
  })
  val sortable = new Sortable(js.Array[HTMLElement](), new Options {
    draggable = ".draggable"
    handle = ".draghandle"
    delay = 200.0 // prevents drag when touch scrolling is intended
    mirror = new MirrorOptions {
      constrainDimensions = true
    }
  })
  val draggableEvents = new DraggableEvents(this, draggable)
  val sortableEvents = new SortableEvents(this, sortable)
}

