package wust.webApp

import acyclic.skipped
import googleAnalytics.Analytics
import monix.reactive.Observable
import monocle.macros.GenLens
import org.scalajs.dom
import org.scalajs.dom.experimental.permissions.PermissionState
import org.scalajs.dom.raw.{HTMLElement, VisibilityState}
import outwatch.dom.dsl._
import rx._
import shopify.draggable._
import wust.api._
import wust.graph._
import wust.ids._
import wust.sdk._
import wust.util.Selector
import wust.webApp.outwatchHelpers._
import wust.webApp.SafeDom.Navigator
import wust.webApp.views.{NewChannelView, PageStyle, View, ViewConfig}

import scala.collection.breakOut
import scala.concurrent.duration._
import scala.scalajs.js

class GlobalState (
    val appUpdateIsAvailable: Observable[Unit],
    val eventProcessor: EventProcessor,
    val sidebarOpen: Var[Boolean], //TODO: replace with ADT Open/Closed
    val viewConfig: Var[ViewConfig]
)(implicit ctx: Ctx.Owner) {

  val auth: Rx[Authentication] = eventProcessor.currentAuth.unsafeToRx(seed = Client.currentAuth)
  val user: Rx[AuthUser] = auth.map(_.user)

  val graph: Rx[Graph] = eventProcessor.graph.unsafeToRx(seed = Graph.empty).map { graph =>
    val u = user.now
    val newGraph =
      if (graph.nodeIds(u.channelNodeId)) graph
      else graph.addNodes(
        // these nodes are obviously not in the graph for an assumed user, since the user is not persisted yet.
        // if we start with an assumed user and just create new channels we will never get a graph from the backend.
        Node.Content(u.channelNodeId, NodeData.defaultChannelsData, NodeMeta(NodeAccess.Level(AccessLevel.Restricted))) ::
          Node.User(u.id, NodeData.User(u.name, isImplicit = true, revision = 0, channelNodeId = u.channelNodeId), NodeMeta.User)  ::
          Nil)

    newGraph.consistent
  }

  val channels: Rx[Seq[Node]] = Rx {
    graph().channels.toSeq.sortBy(_.data.str)
  }

  val isOnline = Observable.merge(
    Client.observable.connected.map(_ => true),
    Client.observable.closed.map(_ => false)
  ).unsafeToRx(true)

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
    def isVisible = dom.document.visibilityState == VisibilityState.visible
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
      NewChannelView
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

  // be aware that this is a potential memory leak.
  // it contains all ids that have ever been collapsed in this session.
  // this is a wanted feature, because manually collapsing nodes is preserved with navigation
  val collapsedNodeIds: Var[Set[NodeId]] = Var(Set.empty)

  // specifies which nodes are collapsed
  val perspective: Var[Perspective] = Var(Perspective()).mapRead { perspective =>
    perspective().union(Perspective(collapsed = Selector.Predicate(collapsedNodeIds())))
  }

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

