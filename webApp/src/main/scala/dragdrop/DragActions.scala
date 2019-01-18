package wust.webApp.dragdrop

import draggable._
import monix.reactive.Observable
import googleAnalytics.Analytics
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import org.scalajs.dom.console
import org.scalajs.dom.ext.KeyCode
import wust.util._
import org.scalajs.dom.raw.HTMLElement
import wust.api.AuthUser
import wust.graph.{ Edge, GraphChanges, Tree, _ }
import wust.ids.{ EdgeData, NodeId, NodeRole, UserId }
import wust.webApp.{ BrowserDetect, DevOnly }
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.ids._

import scala.collection.breakOut
import scala.scalajs.js
import scala.scalajs.js.|

object DragActions {

  // These partial functions describe what happens, but also what is allowed to drag from where to where
  // Be aware: Other functions rely on its partiality (isDefinedAt), therefore do not make them a full function
  // The booleans: Ctrl-pressed, Shift-pressed

  val sortAction: PartialFunction[(DragContainer, DragPayload, DragContainer, Boolean, Boolean), (SortableStopEvent, Graph, UserId) => GraphChanges] = {
    // First, Sort actions:
    import DragContainer._
    import Sorting._
    {
      //// Kanban View ////
      case (from: Kanban.AreaForColumns, payload: DragItem.Stage, into: Kanban.AreaForColumns, false, false) =>
        (sortableStopEvent, graph, userId) =>
          //        val move = GraphChanges.changeTarget[NodeId, NodeId, Edge.Parent](Edge.Parent)(Some(dragging.nodeId), Some(from.parentId), Some(into.parentId))
          val sortChanges = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          val unstageChanges: GraphChanges = if (from.parentId != into.parentId) GraphChanges.disconnect(Edge.Child)(ParentId(from.parentId), ChildId(payload.nodeId)) else GraphChanges.empty
          unstageChanges merge sortChanges

      case (from: Kanban.Column, payload: DragItem.Task, into: Kanban.Column, false, false) =>
        (sortableStopEvent, graph, userId) =>
          val sortChanges = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          val unstageChanges: GraphChanges = if (from.parentId != into.parentId) GraphChanges.disconnect(Edge.Child)(ParentId(from.parentId), ChildId(payload.nodeId)) else GraphChanges.empty
          unstageChanges merge sortChanges

      case (from: Kanban.Card, payload: DragItem.Task, into: Kanban.Column, false, false) =>
        (sortableStopEvent, graph, userId) =>
          // the card changes its workspace from from:Card to into:Kanban.Column.workspace
          //        val move = GraphChanges.changeTarget(Edge.Parent)(Some(dragging.nodeId), stageParents, Some(into.parentId))
          val sortChanges = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          val changeWorkspace: GraphChanges = GraphChanges.changeSource(Edge.Child)(Some(ChildId(payload.nodeId)), ParentId(from.parentId) :: Nil, Some(ParentId(into.workspace)))
          // TODO: adding stageParents to fullChange results in a graphchange where the same parentedge
          // is introduced by sortChanges, but with an ordering. Graphchanges does NOT squash the edges. This is a bug in GraphChanges.
          // val stageParents: GraphChanges = GraphChanges.connect(Edge.Parent)(dragging.nodeId, into.parentId)
          sortChanges merge changeWorkspace //merge stageParents

      case (from: Kanban.Inbox, payload: DragItem.Task, into: Kanban.Column, false, false) =>
        (sortableStopEvent, graph, userId) =>
          //        val move = GraphChanges.changeTarget(Edge.Parent)(Some(payload.nodeId), stageParents, Some(into.parentId))
          val sortChanges = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          val stageParents = graph.getRoleParents(payload.nodeId, NodeRole.Stage).filterNot(_ == into.parentId)
          sortChanges

      case (from: Kanban.Column, payload: DragItem.Task, into: Kanban.Workspace, false, false) =>
        (sortableStopEvent, graph, userId) =>
          // disconnect from all stage parents
          val sortChanges = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          val stageParents = graph.getRoleParents(payload.nodeId, NodeRole.Stage)
          val unstageChanges: GraphChanges = GraphChanges.disconnect(Edge.Child)(ParentId(stageParents), ChildId(payload.nodeId))
          val changeWorkspace: GraphChanges = if (from.workspace != into.parentId) GraphChanges.disconnect(Edge.Child)(ParentId(from.workspace) :: Nil, ChildId(payload.nodeId)) else GraphChanges.empty
          unstageChanges merge sortChanges merge changeWorkspace

      case (from: Kanban.Workspace, payload: DragItem.Task, into: Kanban.Workspace, false, false) =>
        (sortableStopEvent, graph, userId) =>
          // disconnect from all stage parents
          val sortChanges = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          val oldParents = graph.parents(payload.nodeId).filterNot(parentId => parentId == into.parentId || graph.nodesById(parentId).role == NodeRole.Tag)
          val unstageChanges: GraphChanges = GraphChanges.disconnect(Edge.Child)(ParentId(oldParents), ChildId(payload.nodeId))
          unstageChanges merge sortChanges

      //// List View ////
      case (from: List, payload: DragItem.Task, into: List, false, false) =>
        (sortableStopEvent, graph, userId) =>
          sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into, revert = true)

    }
  }

  val dragAction: PartialFunction[(DragPayload, DragTarget, Boolean, Boolean), (Graph, UserId) => GraphChanges] = {
    import DragItem._
    import wust.graph.GraphChanges.{ linkOrMoveInto, linkInto, movePinnedChannel, assign }
    {
      case (payload: ContentNode, target: ContentNode, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeId, target.nodeId, graph, ctrl)
      case (payload: ContentNode, target: Thread, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeId, target.nodeIds, graph, ctrl)
      case (payload: ContentNode, target: Workspace, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeId, target.nodeId, graph, ctrl)
      case (payload: ContentNode, target: Channel, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeId, target.nodeId, graph, ctrl)

      case (payload: ContentNode, target: Tag, false, false) => (graph, userId) => linkInto(payload.nodeId, target.nodeId, graph)
      case (payload: ContentNode, target: BreadCrumb, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeId, target.nodeId, graph, ctrl)

      case (payload: SelectedNode, target: ContentNode, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeId, target.nodeId, graph, ctrl)
      case (payload: SelectedNodes, target: ContentNode, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeIds, target.nodeId, graph, ctrl)
      case (payload: SelectedNodes, target: Workspace, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeIds, target.nodeId, graph, ctrl)
      case (payload: SelectedNodes, target: Channel, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeIds, target.nodeId, graph, ctrl)

      case (payload: Channel, target: Channel, false, false) => (graph, userId) => movePinnedChannel(payload.nodeId, Some(target.nodeId), graph, userId)
      case (payload: Channel, target: Sidebar.type, false, false) => (graph, userId) => movePinnedChannel(payload.nodeId, None, graph, userId)
      case (payload: Channel, target: ContentNode, ctrl, false) => (graph, userId) => movePinnedChannel(payload.nodeId, Some(target.nodeId), graph, userId)

      case (payload: Tag, target: ContentNode, false, false) => (graph, userId) => linkInto(target.nodeId, payload.nodeId, graph)
      case (payload: Tag, target: Tag, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeId, target.nodeId, graph, ctrl)
      case (payload: Tag, target: TagBar, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeId, target.nodeId, graph, ctrl)
      case (payload: Tag, target: Channel, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeId, target.nodeId, graph, ctrl)

      case (payload: User, target: Task, false, false) => (graph, userId) => assign(target.nodeId, payload.userId)
    }
  }

}
