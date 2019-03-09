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

  val sortAction: PartialFunction[(DragPayload, DragContainer, DragContainer, Boolean, Boolean), (SortableStopEvent, Graph, UserId) => GraphChanges] = {
    // First, Sort actions:
    import DragContainer._
    import Sorting._
    {
      //// Kanban View ////
      // Reorder or nest Stages
      case (payload: DragItem.Stage, from: Kanban.AreaForColumns, into: Kanban.AreaForColumns, ctrl, false) =>
        (sortableStopEvent, graph, userId) =>
          //        val move = GraphChanges.changeTarget[NodeId, NodeId, Edge.Parent](Edge.Parent)(Some(dragging.nodeId), Some(from.parentId), Some(into.parentId))
          def addColumn = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          def disconnetColumn: GraphChanges = if (from.parentId != into.parentId)
            GraphChanges.disconnect(Edge.Child)(ParentId(from.parentId), ChildId(payload.nodeId))
            else GraphChanges.empty
          if(ctrl)
            addColumn
          else
            addColumn merge disconnetColumn

      // Task between Columns
      case (payload: DragItem.Task, from: Kanban.Column, into: Kanban.Column, ctrl, false) =>
        (sortableStopEvent, graph, userId) =>
          def addTargetColumn = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          def addTargetWorkspace = GraphChanges.connect(Edge.Child)(ParentId(into.workspace), ChildId(payload.nodeId))
          def disconnectColumn = GraphChanges.disconnect(Edge.Child)(ParentId(from.nodeId), ChildId(payload.nodeId))
          def disconnectWorkspace: GraphChanges = if (from.workspace != into.workspace)
              GraphChanges.disconnect(Edge.Child)(ParentId(from.workspace), ChildId(payload.nodeId))
            else GraphChanges.empty

          if(ctrl)
            addTargetColumn merge addTargetWorkspace
          else
            addTargetColumn merge addTargetWorkspace merge disconnectColumn merge disconnectWorkspace

      // e.g. Subtask into Column
      case (payload: DragItem.Task, from: Kanban.Inbox, intoColumn: Kanban.Column, ctrl, false) =>
        (sortableStopEvent, graph, userId) =>
          //        val move = GraphChanges.changeTarget(Edge.Parent)(Some(payload.nodeId), stageParents, Some(intoColumn.parentId))
          def addTargetColumn = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, intoColumn)
          def addTargetWorkspace = GraphChanges.connect(Edge.Child)(ParentId(intoColumn.workspace), ChildId(payload.nodeId))
          def disconnect: GraphChanges = if (from.parentId != intoColumn.workspace)
              GraphChanges.disconnect(Edge.Child)(ParentId(from.parentId), ChildId(payload.nodeId))
            else GraphChanges.empty
          if(ctrl)
            addTargetColumn merge addTargetWorkspace
          else
            addTargetColumn merge addTargetWorkspace merge disconnect

      // e.g. Card from Column into other Card/Inbox
      case (payload: DragItem.Task, fromColumn: Kanban.Column, into: Kanban.Workspace, ctrl, false) =>
        (sortableStopEvent, graph, userId) =>
          // disconnect fromColumn all stage parents
          val addTargetWorkspace = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, fromColumn, into)
          def disconnectFromWorkspace: GraphChanges = if (fromColumn.workspace != into.parentId)
              GraphChanges.disconnect(Edge.Child)(ParentId(fromColumn.workspace), ChildId(payload.nodeId))
            else GraphChanges.empty
          def disconnectFromColumn = GraphChanges.disconnect(Edge.Child)(ParentId(fromColumn.nodeId), ChildId(payload.nodeId))
          if(ctrl)
            addTargetWorkspace
          else
            addTargetWorkspace merge disconnectFromColumn merge disconnectFromWorkspace

      // e.g. Card from Card/Inbox into Card/Inbox
      case (payload: DragItem.Task, from: Kanban.Workspace, into: Kanban.Workspace, ctrl, false) =>
        (sortableStopEvent, graph, userId) =>
          // disconnect from all stage parents
          val addTargetWorkspace = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          def disconnectFromWorkspace: GraphChanges = if (from.parentId != into.parentId)
              GraphChanges.disconnect(Edge.Child)(ParentId(from.parentId), ChildId(payload.nodeId))
            else GraphChanges.empty
          if(ctrl)
            addTargetWorkspace
          else
            addTargetWorkspace merge disconnectFromWorkspace

      //// List View ////
      case (payload: DragItem.Task, from: List, into: List, false, false) =>
        (sortableStopEvent, graph, userId) =>
          sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into, revert = true)

    }
  }

  val dragAction: PartialFunction[(DragPayload, DragTarget, Boolean, Boolean), (Graph, UserId) => GraphChanges] = {
    import DragItem._
    import wust.graph.GraphChanges.{ linkOrCopyInto, linkOrMoveInto, linkInto, movePinnedChannel, assign }
    {
      case (payload: ContentNode, target: ContentNode, ctrl, false) => (graph, userId) => linkOrMoveInto(ChildId(payload.nodeId), ParentId(target.nodeId), graph, ctrl)
      case (payload: ContentNode, target: Thread, ctrl, false) => (graph, userId) => linkOrMoveInto(ChildId(payload.nodeId), target.nodeIds.map(ParentId(_)), graph, ctrl)
      case (payload: ContentNode, target: Workspace, ctrl, false) => (graph, userId) => linkOrMoveInto(ChildId(payload.nodeId), ParentId(target.nodeId), graph, ctrl)
      case (payload: ContentNode, target: Channel, ctrl, false) => (graph, userId) => linkOrMoveInto(ChildId(payload.nodeId), ParentId(target.nodeId), graph, ctrl)

      case (payload: ContentNode, target: Tag, false, false) => (graph, userId) => linkInto(ChildId(payload.nodeId), ParentId(target.nodeId), graph)
      case (payload: ContentNode, target: BreadCrumb, ctrl, false) => (graph, userId) => linkOrMoveInto(ChildId(payload.nodeId), ParentId(target.nodeId), graph, ctrl)

      case (payload: SelectedNode, target: ContentNode, ctrl, false) => (graph, userId) => linkOrMoveInto(ChildId(payload.nodeId), ParentId(target.nodeId), graph, ctrl)
      case (payload: SelectedNodes, target: ContentNode, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeIds.map(ChildId(_)), ParentId(target.nodeId), graph, ctrl)
      case (payload: SelectedNodes, target: Workspace, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeIds.map(ChildId(_)), ParentId(target.nodeId), graph, ctrl)
      case (payload: SelectedNodes, target: Channel, ctrl, false) => (graph, userId) => linkOrMoveInto(payload.nodeIds.map(ChildId(_)), ParentId(target.nodeId), graph, ctrl)

      case (payload: Channel, target: Channel, false, false) => (graph, userId) => movePinnedChannel(ChildId(payload.nodeId), Some(ParentId(target.nodeId)), graph, userId)
      case (payload: Channel, target: Sidebar.type, false, false) => (graph, userId) => movePinnedChannel(ChildId(payload.nodeId), None, graph, userId)
      case (payload: Channel, target: ContentNode, ctrl, false) => (graph, userId) => movePinnedChannel(ChildId(payload.nodeId), Some(ParentId(target.nodeId)), graph, userId)

      case (payload: Property, target: ContentNode, false, false) => (graph, userId) => linkOrCopyInto(payload.edge, target.nodeId, graph)

      case (payload: Tag, target: ContentNode, false, false) => (graph, userId) => linkInto(ChildId(target.nodeId), ParentId(payload.nodeId), graph)
      case (payload: Tag, target: Tag, ctrl, false) => (graph, userId) => linkOrMoveInto(ChildId(payload.nodeId), ParentId(target.nodeId), graph, ctrl)
      case (payload: Tag, target: TagBar, ctrl, false) => (graph, userId) => linkOrMoveInto(ChildId(payload.nodeId), ParentId(target.nodeId), graph, ctrl)
      case (payload: Tag, target: Channel, ctrl, false) => (graph, userId) => linkOrMoveInto(ChildId(payload.nodeId), ParentId(target.nodeId), graph, ctrl)

      case (payload: User, target: Task, false, false) => (graph, userId) => assign(target.nodeId, payload.userId)
    }
  }

}
