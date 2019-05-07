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
import wust.graph.{Edge, GraphChanges, Tree, _}
import wust.ids.{ChildId, CuidOrdering, EdgeData, EpochMilli, NodeId, NodeRole, ParentId, UserId}
import wust.webApp.{BrowserDetect, DevOnly}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._

import scala.collection.breakOut
import scala.scalajs.js
import scala.scalajs.js.|


object Sorting {

  import TaskOrdering.Position

  def parseDomPositions(e: SortableStopEvent): Option[(Position, Position)] = {

    // Get index of moved node in previous container
    val origElem = e.dragEvent.originalSource

    // Get index of moved node in new container
    val sourceElem = e.dragEvent.source

    // workaround: use classList to explicitly filter elements (dragEvent.mirror does not work reliable)
    val previousContChildren: js.Array[HTMLElement] = e.oldContainer.children.asInstanceOf[js.Array[HTMLElement]].filterNot(f => f == e.dragEvent.source || f == e.dragEvent.mirror || f.classList.contains("draggable-mirror"))
    val newContChildren: js.Array[HTMLElement] = e.newContainer.children.asInstanceOf[js.Array[HTMLElement]].filterNot(f => f == e.dragEvent.originalSource || f == e.dragEvent.mirror || f.classList.contains("draggable-mirror"))

    val prevPos = previousContChildren.indexOf(origElem)
    val nextPos = newContChildren.indexOf(sourceElem)

    if(prevPos != -1 && nextPos != -1) Some((prevPos, nextPos))
    else None
  }

  @inline def checkContainerChanged(from: DragContainer, into: DragContainer) = from != into
  @inline def checkPositionChanged(previousPosition: Position, newPosition: Position) = previousPosition != newPosition

  // Hints:
  // - Most outer container contains unclassified nodes as well
  // - Only one "big" sortable => container always the same (oldContainer == newContainer)
  // - A container corresponds to a parent node
  // - The index in a container correspond to the index in the topological sorted node list of the corresponding parent node
  def sortingChanges(graph: Graph, userId: UserId, e: SortableStopEvent, sortNode: NodeId, from: SortableContainer, into: SortableContainer): GraphChanges = {

    import DragContainer._
    scribe.debug("Computing sorting change")
    //TODO: Is a SortEvent triggered when a new card is created?
    parseDomPositions(e) match {
      case Some((previousDomPosition, newDomPosition)) =>
        val containerChanged = checkContainerChanged(from, into)

        val gc = if(!containerChanged && !checkPositionChanged(previousDomPosition, newDomPosition)) { scribe.debug("item dropped on same place (no movement)"); GraphChanges.empty }
                 else constructGraphChangesByContainer(graph, userId, sortNode, containerChanged, previousDomPosition, newDomPosition, from.parentId, into.parentId, from.items, into.items)

        scribe.debug("Calculated new sorting graph change!")
        scribe.debug(gc.toPrettyString(graph))
        gc
      case _                                           =>
        scribe.error("Could not determine position of elements")
        GraphChanges.empty
    }
  }

  @inline private def checkBounds(containerSize: Int, index: Int) = index >= 0 && index < containerSize

  private def constructGraphChangesByContainer(graph: Graph, userId: UserId, nodeId: NodeId, containerChanged: Boolean, previousDomPosition: Position, newDomPosition: Position, from: NodeId, into: NodeId, fromItems: Seq[NodeId], intoItems: Seq[NodeId]): GraphChanges = {

    scribe.debug(s"calculate for movement: from position $previousDomPosition to new position $newDomPosition into ${if(containerChanged) "different" else "same"} container")

    // Reconstruct order of nodes in the `into` container
    val newOrderedNodes: Seq[NodeId] = intoItems

    if(newOrderedNodes.isEmpty) return GraphChanges.connect(Edge.Child)(ParentId(into), ChildId(nodeId))

    // Get corresponding nodes that are before and after the dragged node
    val indexOffset = if(!containerChanged && previousDomPosition < newDomPosition) 1 else 0
    val beforeNodeIndex = newDomPosition - 1 + indexOffset
    val afterNodeIndex = newDomPosition + indexOffset

    scribe.debug(s"before index = $beforeNodeIndex, after index = $afterNodeIndex")

    val newOrderingValue = {
      val beforeNodeIndexDefined = checkBounds(newOrderedNodes.size, beforeNodeIndex)
      val afterNodeIndexDefined = checkBounds(newOrderedNodes.size, afterNodeIndex)
      if(beforeNodeIndexDefined && afterNodeIndexDefined) {
        val beforeNodeIdx = graph.idToIdxOrThrow(newOrderedNodes(beforeNodeIndex))
        val afterNodeIdx = graph.idToIdxOrThrow(newOrderedNodes(afterNodeIndex))
        val before = TaskOrdering.getChildEdgeOrThrow(graph, into, beforeNodeIdx).data.ordering
        val after = TaskOrdering.getChildEdgeOrThrow(graph, into, afterNodeIdx).data.ordering
        before + (after - before)/2
      } else if(beforeNodeIndexDefined) {
        val beforeNodeIdx = graph.idToIdxOrThrow(newOrderedNodes(beforeNodeIndex))
        val before = TaskOrdering.getChildEdgeOrThrow(graph, into, beforeNodeIdx).data.ordering
        before + 1
      } else if(afterNodeIndexDefined) {
        val afterNodeIdx = graph.idToIdxOrThrow(newOrderedNodes(afterNodeIndex))
        val after = TaskOrdering.getChildEdgeOrThrow(graph, into, afterNodeIdx).data.ordering
        after - 1
      } else { // workaround: infamous 'should never happen' case
        scribe.warn("NON-EMPTY but no before / after")
        CuidOrdering.calculate(nodeId)
      }
    }

    val newParentEdge =
      if(containerChanged) Edge.Child(ParentId(into), EdgeData.Child(newOrderingValue), ChildId(nodeId))
      else {
        val keptDeletedAt = TaskOrdering.getChildEdgeOrThrow(graph, from, graph.idToIdxOrThrow(nodeId)).data.deletedAt
        Edge.Child(ParentId(into), new EdgeData.Child(keptDeletedAt, newOrderingValue), ChildId(nodeId))
      }

    GraphChanges(addEdges = Set(newParentEdge))
  }
}
