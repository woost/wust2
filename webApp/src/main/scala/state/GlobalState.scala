package wust.webApp.state

import draggable._
import googleAnalytics.Analytics
import monix.reactive.Observable
import monocle.macros.GenLens
import org.scalajs.dom
import org.scalajs.dom.experimental.permissions.PermissionState
import org.scalajs.dom.raw.{HTMLElement, VisibilityState}
import outwatch.dom.dsl._
import rx._
import wust.api._
import wust.graph._
import wust.ids._
import wust.sdk._
import wust.util.Selector
import wust.webApp.dragdrop.{DraggableEvents, SortableEvents}
import wust.webApp.jsdom.Notifications
import wust.webApp.outwatchHelpers._

import scala.collection.breakOut
import scala.concurrent.duration._
import scala.scalajs.js

class GlobalState (
    val appUpdateIsAvailable: Observable[Unit],
    val eventProcessor: EventProcessor,
    val sidebarOpen: Var[Boolean], //TODO: replace with ADT Open/Closed
    val viewConfig: Var[ViewConfig],
    val isOnline: Rx[Boolean]
)(implicit ctx: Ctx.Owner) {

  val auth: Rx[Authentication] = eventProcessor.currentAuth.unsafeToRx(seed = eventProcessor.initialAuth)
  val user: Rx[AuthUser] = auth.map(_.user)

  val graph: Rx[Graph] = {
    val internalGraph = eventProcessor.graph.unsafeToRx(seed = Graph.empty)

    Rx {
      val graph = internalGraph()
      val u = user()
    val newGraph =
      if (graph.nodeIds(u.channelNodeId)) graph
        else {
          graph.addNodes(
        // these nodes are obviously not in the graph for an assumed user, since the user is not persisted yet.
        // if we start with an assumed user and just create new channels we will never get a graph from the backend.
        Node.Content(u.channelNodeId, NodeData.defaultChannelsData, NodeMeta(NodeAccess.Level(AccessLevel.Restricted))) ::
            user().toNode ::
            Nil
          )
        }

    newGraph.consistent
  }
  }

  val channelTree: Rx[Tree] = Rx {
    val channelNode = graph().nodesById(user().channelNodeId)
    graph().channelTree(channelNode)
  }

  val channels: Rx[Seq[Node]] = Rx {
    channelTree().flatten.distinct
  }

  val addNodesInTransit = eventProcessor.changesInTransit
    .map(changes => changes.flatMap(_.involvedNodeIds)(breakOut): Set[NodeId])
    .unsafeToRx(Set.empty)

  val isSynced = eventProcessor.changesInTransit.map(_.isEmpty).unsafeToRx(true)

  val page: Var[Page] = viewConfig.zoom(GenLens[ViewConfig](_.page)).mapRead { rawPage =>
    rawPage() match {
      case p: Page.Selection => p.copy(
        parentIds = rawPage().parentIds //.filter(rawGraph().postsById.isDefinedAt)
      )
      case p => p
    }
  }

  val pageIsBookmarked: Rx[Boolean] = Rx {
    page().parentIds.forall(
      graph().children(user().channelNodeId).contains
    )
  }


  //TODO: wait for https://github.com/raquo/scala-dom-types/pull/36
  val documentIsVisible: Rx[Boolean] = {
    def isVisible = dom.document.visibilityState.asInstanceOf[String] == VisibilityState.visible.asInstanceOf[String]
    events.window.eventProp[dom.Event]("visibilitychange").map(_ => isVisible).unsafeToRx(isVisible)
  }
  val permissionState: Rx[PermissionState] = Notifications.createPermissionStateRx()
  permissionState.triggerLater { state =>
    if(state == PermissionState.granted || state == PermissionState.denied)
      Analytics.sendEvent("notification", state.asInstanceOf[String])
  }

  val graphContent: Rx[Graph] = Rx { graph().pageContentWithAuthors(page()) }

  val view: Var[View] = viewConfig.zoom(GenLens[ViewConfig](_.view)).mapRead { view =>
    if (!view().isContent || page().parentIds.nonEmpty || page().mode != PageMode.Default)
      view()
    else
      View.NewChannel
  }

  val pageParentNodes: Rx[Seq[Node]] = Rx {
    page().parentIds.flatMap(id => graph().nodesById.get(id))
  }

  //
  val pageAncestorsIds: Rx[Seq[NodeId]] = Rx {
    page().parentIds.flatMap(node => graph().ancestors(node))(breakOut)
  }

  val nodeAncestorsHierarchy: Rx[Map[Int, Seq[Node]]] =
    pageAncestorsIds.map(
      _.map(node => (graph().parentDepth(node), graph().nodesById(node)))
        .groupBy(_._1)
        .mapValues(_.map {
                     case (depth, node) => node
                   }.distinct)
    )

  val pageStyle = Rx {
    PageStyle(view(), page())
  }

  // specifies which nodes are collapsed
  val perspective: Var[Perspective] = Var(Perspective())

  val selectedNodeIds: Var[Set[NodeId]] = Var(Set.empty[NodeId]).mapRead{ selectedNodeIds =>
    selectedNodeIds().filter(graph().nodesById.isDefinedAt)
  }

  val jsErrors: Observable[String] = events.window.onError.map(_.message)

  val screenSize: Rx[ScreenSize] = events.window.onResize
    .debounce(0.2 second)
    .map(_ => ScreenSize.calculate())
    .unsafeToRx(ScreenSize.calculate())

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
    delay = 200.0
    mirror = new MirrorOptions {
      constrainDimensions = true
    }
  })
  val draggableEvents = new DraggableEvents(this, draggable)
  val sortableEvents = new SortableEvents(this, sortable)
}

