package wust.webApp.dragdrop

import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import org.scalajs.dom.console
import draggable._
import org.scalajs.dom.raw.HTMLElement
import wust.graph.{Edge, GraphChanges, GraphLookup}
import wust.util._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

import scala.scalajs.js


class SortableEvents(state: GlobalState, draggable: Draggable) {
  type PositionIndex = Int

  private val sortableStartEvent = PublishSubject[SortableStartEvent]
  private val sortableStopEvent = PublishSubject[SortableStopEvent]
  private val sortableSortEvent = PublishSubject[SortableSortEvent]

  draggable.on[SortableStartEvent]("sortable:start", sortableStartEvent.onNext _)
  draggable.on[SortableSortEvent]("sortable:sort", sortableSortEvent.onNext _)
  draggable.on[SortableStopEvent]("sortable:stop", sortableStopEvent.onNext _)

  //  draggable.on("sortable:start", (e:SortableEvent) => console.log("sortable:start", e))
  //  draggable.on("sortable:sort", () => console.log("sortable:sort"))
  //  draggable.on("sortable:sorted", () => console.log("sortable:sorted"))
  //  draggable.on("sortable:stop", () => console.log("sortable:stop"))


  // TODO: Generalize and outsource to DataOrdering
  def beforeChanges(g: GraphLookup, e: SortableStopEvent, sortNode: DragItem.Kanban.Item, from: DragContainer.Kanban.Area, into: DragContainer.Kanban.Area): GraphChanges = {
    // Hints:
    // - Most outer container contains unclassified nodes as well
    // - Only one "big" sortable => container always the same (oldContainer == newContainer)
    // - A container corresponds to a parent node
    // - The index in a container correspond to the index in the topological sorted node list of the corresponding parent node

    def getNodeStr(graph: GraphLookup, indices: Seq[Int]) = {
      indices.map(idx => graph.nodes(idx).str)
    }

    // Get index of moved node in previous container
    @inline def origElem = e.dragEvent.originalSource
    // workaround: use classList to explicitly filter elements (dragEvent.mirror does not work reliable)
    val oldContChilds = e.oldContainer.children.asInstanceOf[js.Array[HTMLElement]].filterNot(f => f == e.dragEvent.source || f == e.dragEvent.mirror || f.classList.contains("draggable-mirror") || f.classList.contains("kanbannewcolumnarea"))
    val oldSortIndex = oldContChilds.indexOf(origElem)

    // Get index of moved node in new container
    @inline def sourceElem = e.dragEvent.source
    // workaround: use classList to explicitly filter elements (dragEvent.mirror does not work reliable)
    val newContChilds = e.newContainer.children.asInstanceOf[js.Array[HTMLElement]].filterNot(f => f == e.dragEvent.originalSource || f == e.dragEvent.mirror || f.classList.contains("draggable-mirror") || f.classList.contains("kanbannewcolumnarea") )
    val newSortIndex = newContChilds.indexOf(sourceElem)

    val containerChanged = from.parentIds != into.parentIds
    val movedDownwards = oldSortIndex < newSortIndex

    // Kanban item dropped on the previous / same place
    if(!containerChanged && oldSortIndex == newSortIndex) {
      scribe.info("item dropped on same place (no movement)")
      return GraphChanges.empty
    }

   // Node id of sorted node
    val sortedNodeId = sortNode.nodeId

//    scribe.info("SORTING PREBUILDS\n" +
//      s"dragged node: ${g.nodesById(sortNode.nodeId).str}\n\t" +
//      s"from index $oldSortIndex / ${oldContChilds.length - 1}\n\t" +
//      s"to index $newSortIndex / ${newContChilds.length - 1}")

    // Reconstruct ordering
    def oldOrderingNodes = from.parentIds.flatMap(parent => g.childrenIdx(g.idToIdx(parent)).toSeq) // nodes in container
    val oldOrderedNodes: Seq[Int] = g.topologicalSortByIdx[Int](oldOrderingNodes, identity, Some(_)) // rebuild ordering in container


    // Kanban item dropped at an edge of a container
    if(oldOrderedNodes.size != oldContChilds.length) {
      // console.log("Kanban elements by sortable: ", oldContChilds)
      scribe.warn(s"Kanban elements by orderedNodes: ${getNodeStr(g, oldOrderedNodes)}")
      scribe.warn(s"oldOrderedNodes.size(${oldOrderedNodes.size}) != oldContChilds.length(${oldContChilds.length}) => Kanban item dropped at an edge of a container")
      return GraphChanges.empty
    }

    val oldIndex = oldOrderedNodes.indexOf(g.idToIdx(sortedNodeId))

    // Kanban data and node data diverge
    if(oldIndex != oldSortIndex){
      scribe.error(s"index of reconstruction and sort must match, oldIndex($oldIndex) != oldSortIndex($oldSortIndex)")
      return GraphChanges.empty
    }

    // Cleanup previous edges
    val previousBefore = if(oldIndex > 0) Some(g.nodeIds(oldOrderedNodes(oldIndex - 1))) else None // Moved from very beginning
    val previousAfter = if(oldIndex < oldOrderedNodes.size - 1) Some(g.nodeIds(oldOrderedNodes(oldIndex + 1))) else None // Moved from very end

    val previousEdges: Set[Option[Edge]] = from.parentIds.flatMap(pid => Set(
      previousBefore match {
        case Some(b) => Some(Edge.Before(b, sortedNodeId, pid))
        case _ => None
      },
      previousAfter match {
        case Some(a) => Some(Edge.Before(sortedNodeId, a, pid))
        case _ => None
      }
      )).toSet

    @inline def relinkEdges = if(previousBefore.isDefined && previousAfter.isDefined) into.parentIds.map(nid => Some(Edge.Before(previousBefore.get, previousAfter.get, nid))).toSet else Set.empty[Option[Edge]]

    def newOrderingNodes = into.parentIds.flatMap(parent => g.childrenIdx(g.idToIdx(parent)).toSeq) // nodes in container
    val newOrderedNodes: Seq[Int] = g.topologicalSortByIdx[Int](newOrderingNodes, identity, Some(_)) // rebuild ordering in container

    if(newOrderedNodes.isEmpty){
      scribe.info(s"Dropped on empty place")
      return GraphChanges.empty
    }

    // Kanban item dropped at an edge of a container
    if(newOrderedNodes.size + 1 < newContChilds.length) {
      // console.log("Kanban elements by sortable: ", newContChilds)
      scribe.warn(s"Kanban elements by orderedNodes: ${getNodeStr(g, newOrderedNodes)}")
      scribe.warn(s"newOrderedNodes.size(${newOrderedNodes.size}) != newContChilds.length(${newContChilds.length}) => Kanban item dropped at an edge of a container")
      return GraphChanges.empty
    }

    val (nowBefore, nowAfter) = if(containerChanged) {

      if(newOrderedNodes.nonEmpty) {
        val (newBefore, newAfter) = (newSortIndex-1, newSortIndex)

        val b = if(newBefore > -1) Some(g.nodeIds(newOrderedNodes(newBefore))) else None // Moved to very beginning
        val a = if(newAfter < newOrderedNodes.size) Some(g.nodeIds(newOrderedNodes(newAfter))) else None // Moved to very end

        (b, a)
      } else (None, None)
    } else {

      val downCorrection = if(movedDownwards) 1 else 0 // before correction
      val upCorrection = if(!movedDownwards) -1 else 0 // after correction
      val beforeIndexShift = if((newSortIndex - 1 + downCorrection) == oldSortIndex) -2 else -1
      val afterIndexShift = if((newSortIndex + 1 + upCorrection) == oldSortIndex) 2 else 1

      val (newBefore, newAfter) = (newSortIndex+beforeIndexShift+downCorrection, newSortIndex+afterIndexShift+upCorrection)

      // If newBefore or newAfter equals oldSortIndex in the same container (outer if condition), we would create a selfloop
      if(newBefore == oldSortIndex || newAfter == oldSortIndex) {
        scribe.error(s"Index conflict: old $oldSortIndex, before: $newBefore, after: $newAfter")
        return GraphChanges.empty
      }

      val b = if(newBefore > -1) Some(g.nodeIds(newOrderedNodes(newBefore))) else None // Moved to very beginning
      val a = if(newAfter < newOrderedNodes.size) Some(g.nodeIds(newOrderedNodes(newAfter))) else None // Moved to very end

      (b, a)
    }

    val cleanupBeforeEdges = if(nowBefore.isDefined && nowAfter.isDefined) from.parentIds.map(nid => Some(Edge.Before(nowBefore.get, nowAfter.get, nid))).toSet else Set.empty[Option[Edge]]

    val newBeforeEdges: Set[Option[Edge]] = into.parentIds.flatMap(nid => Set(
      nowBefore match {
        case Some(b) => Some(Edge.Before(b, sortedNodeId, nid))
        case _ => None
      },
      nowAfter match {
        case Some(a) => Some(Edge.Before(sortedNodeId, a, nid))
        case _ => None
      }
    )).toSet

    scribe.debug("SORTING\n" +
      s"dragged node: ${g.nodesById(sortedNodeId).str}\n\t" +
      s"from ${from.parentIds.map(pid => g.nodesById(pid).str)} containing ${getNodeStr(g, oldOrderedNodes)}\n\t" +
      s"from index $oldIndex / ${oldOrderedNodes.length - 1}\n\t" +
      s"into ${into.parentIds.map(pid => g.nodesById(pid).str)} containing ${getNodeStr(g, newOrderedNodes)}\n\t" +
      s"to index $newSortIndex / ${newOrderedNodes.length - 1}")

    val gc = GraphChanges(
      addEdges = (newBeforeEdges ++ relinkEdges).flatten,
      delEdges = (previousEdges ++ cleanupBeforeEdges).flatten
    ).consistent

    // GraphChanges.log(gc, Some(state.graph.now))

    gc
  }

  val
  sortableActions:PartialFunction[(SortableStopEvent, DragPayload, DragContainer, DragContainer, Boolean, Boolean),Unit] = {
    import DragContainer._
    def graph = state.graph.now

    {
      case (e, dragging: DragItem.Kanban.ToplevelColumn, from: Kanban.ColumnArea, into: Kanban.ColumnArea, false, false) =>
        val beforeEdges = beforeChanges(graph.lookup, e, dragging, from, into)
        state.eventProcessor.enriched.changes.onNext(beforeEdges)

      case (e, dragging: DragItem.Kanban.SubColumn, from: Kanban.Column, into: Kanban.ColumnArea, false, false) =>

        val move = GraphChanges.changeTarget(Edge.Parent)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)

        val moveStaticInParents = if(graph.isStaticParentIn(dragging.nodeId, from.parentIds)) {
          GraphChanges.changeTarget(Edge.StaticParentIn)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)
        } else GraphChanges.empty

        val beforeEdges = beforeChanges(graph.lookup, e, dragging, from, into)
        state.eventProcessor.enriched.changes.onNext(move merge moveStaticInParents merge beforeEdges)

      case (e, dragging: DragItem.Kanban.Item, from: Kanban.Area, into: Kanban.Column, false, false) =>

        val move = GraphChanges.changeTarget(Edge.Parent)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)

        val moveStaticInParents = if(graph.isStaticParentIn(dragging.nodeId, from.parentIds)) {
          GraphChanges.changeTarget(Edge.StaticParentIn)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)
        } else GraphChanges.empty

        val beforeEdges = beforeChanges(graph.lookup, e, dragging, from, into)
        state.eventProcessor.enriched.changes.onNext(move merge moveStaticInParents merge beforeEdges)

      case (e, dragging: DragItem.Kanban.SubItem, from: Kanban.Area, into: Kanban.NewColumnArea, false, false) =>

        val move = GraphChanges.changeTarget(Edge.Parent)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)
        // always make new columns static
        val moveStaticInParents = GraphChanges.changeTarget(Edge.StaticParentIn)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)

        val beforeEdges = beforeChanges(graph.lookup, e, dragging, from, into)
        state.eventProcessor.enriched.changes.onNext(move merge moveStaticInParents merge beforeEdges)

      case (e, dragging: DragItem.Kanban.SubItem, from: Kanban.Area, into: Kanban.IsolatedNodes, false, false) =>

        val move = GraphChanges.changeTarget(Edge.Parent)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)
        val removeStatic = GraphChanges.disconnect(Edge.StaticParentIn)(dragging.nodeId, from.parentIds)

        val beforeEdges = beforeChanges(graph.lookup, e, dragging, from, into)
        state.eventProcessor.enriched.changes.onNext(move merge removeStatic merge beforeEdges)

    }
  }

//  val ctrlDown = keyDown(KeyCode.Ctrl)
  val ctrlDown = Observable(false) // TODO: somehow revert sortableSortEvent when pressing ctrl
  //  ctrlDown.foreach(down => println(s"ctrl down: $down"))

  //TODO: keyup-event for Shift does not work in chrome. It reports Capslock.
  val shiftDown = Observable(false)
  //  val shiftDown = keyDown(KeyCode.Shift)
  //  shiftDown.foreach(down => println(s"shift down: $down"))


  sortableStartEvent.foreachTry { e =>
    // copy dragpayload reference from source to mirror // https://github.com/Shopify/draggable/issues/245
    val payload:Option[DragPayload] = readDragPayload(e.dragEvent.originalSource)
    payload.foreach ( writeDragPayload(e.dragEvent.source, _) )

    payload match {
      case Some(DragItem.DisableDrag) => e.cancel()
      case _ =>
    }
  }

  sortableSortEvent.withLatestFrom2(ctrlDown,shiftDown)((e,ctrl,shift) => (e,ctrl,shift)).foreachTry { case (e,ctrl,shift) =>
    val overContainerWorkaround = e.dragEvent.asInstanceOf[js.Dynamic].overContainer.asInstanceOf[dom.html.Element] // https://github.com/Shopify/draggable/issues/256
    val sourceContainerWorkaround = e.dragEvent.asInstanceOf[js.Dynamic].sourceContainer.asInstanceOf[dom.html.Element] // TODO: report as feature request
    val dragging = readDragPayload(e.dragEvent.source)
    val overContainer = readDragContainer(overContainerWorkaround)
    val sourceContainer = readDragContainer(sourceContainerWorkaround)

    // white listing allowed sortable actions
    (dragging, sourceContainer, overContainer) match {
      case (Some(dragging), Some(sourceContainer), Some(overContainer)) if sortableActions.isDefinedAt((null, dragging, sourceContainer, overContainer, ctrl, shift)) => // allowed
//      case (Some(dragging), Some(sourceContainer), Some(overContainer)) => println(s"not allowed: $sourceContainer -> $dragging -> $overContainer"); e.cancel()
      case a => e.cancel() // not allowed
    }
  }


  sortableStopEvent.withLatestFrom2(ctrlDown,shiftDown)((e,ctrl,shift) => (e,ctrl,shift)).foreachTry { case (e,ctrl,shift) =>
      val dragging = readDragPayload(e.dragEvent.source)
      val oldContainer = readDragContainer(e.oldContainer)
      val newContainer = if(e.newContainer != e.oldContainer) readDragContainer(e.newContainer) else oldContainer

      (dragging, oldContainer, newContainer) match {
        case (Some(dragging), Some(oldContainer), Some(newContainer)) =>
          sortableActions.applyOrElse((e, dragging, oldContainer, newContainer, ctrl, shift), (other:(SortableEvent, DragPayload, DragContainer, DragContainer, Boolean, Boolean)) => scribe.warn(s"sort combination not handled."))
        case other => scribe.warn(s"incomplete drag action: $other")
      }
//    }
  }
}
