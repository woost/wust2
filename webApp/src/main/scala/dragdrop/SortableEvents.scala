package wust.webApp.dragdrop

import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import org.scalajs.dom.console
import draggable._
import wust.graph.{Edge, GraphChanges}
import wust.util._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

import scala.scalajs.js


class SortableEvents(state: GlobalState, draggable: Draggable) {
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

  val sortableActions:PartialFunction[(SortableEvent, DragPayload, DragContainer, DragContainer, Boolean, Boolean),Unit] = {
    import DragContainer._
    def graph = state.graph.now

    {
      case (e, dragging: DragItem.Kanban.ToplevelColumn, from: Kanban.ColumnArea, into: Kanban.ColumnArea, false, false) =>
        // console.log("reordering columns")
        // TODO: persist ordering

      case (e, dragging: DragItem.Kanban.SubColumn, from: Kanban.Column, into: Kanban.ColumnArea, false, false) =>
        val move = GraphChanges.changeTarget(Edge.Parent)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)

        val moveStaticInParents = if(graph.isStaticParentIn(dragging.nodeId, from.parentIds)) {
          GraphChanges.changeTarget(Edge.StaticParentIn)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)
        } else GraphChanges.empty

        state.eventProcessor.enriched.changes.onNext(move merge moveStaticInParents)

      case (e, dragging: DragItem.Kanban.Item, from: Kanban.Area, into: Kanban.Column, false, false) =>
        val move = GraphChanges.changeTarget(Edge.Parent)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)

        val moveStaticInParents = if(graph.isStaticParentIn(dragging.nodeId, from.parentIds)) {
          GraphChanges.changeTarget(Edge.StaticParentIn)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)
        } else GraphChanges.empty

        state.eventProcessor.enriched.changes.onNext(move merge moveStaticInParents)

      case (e, dragging: DragItem.Kanban.SubItem, from: Kanban.Area, into: Kanban.NewColumnArea, false, false) =>
        val move = GraphChanges.changeTarget(Edge.Parent)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)
        // always make new columns static
        val moveStaticInParents = GraphChanges.changeTarget(Edge.StaticParentIn)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)

        state.eventProcessor.enriched.changes.onNext(move merge moveStaticInParents)

      case (e, dragging: DragItem.Kanban.SubItem, from: Kanban.Area, into: Kanban.IsolatedNodes, false, false) =>
        val move = GraphChanges.changeTarget(Edge.Parent)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)
        val removeStatic = GraphChanges.disconnect(Edge.StaticParentIn)(dragging.nodeId, from.parentIds)
        state.eventProcessor.enriched.changes.onNext(move merge removeStatic)
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
      case (Some(dragging), Some(sourceContainer), Some(overContainer)) if sortableActions.isDefinedAt((e, dragging, sourceContainer, overContainer, ctrl, shift)) => // allowed
//      case (Some(dragging), Some(sourceContainer), Some(overContainer)) => println(s"not allowed: $sourceContainer -> $dragging -> $overContainer"); e.cancel()
      case a => e.cancel() // not allowed
    }
  }


  sortableStopEvent.withLatestFrom2(ctrlDown,shiftDown)((e,ctrl,shift) => (e,ctrl,shift)).foreachTry { case (e,ctrl,shift) =>
    if(e.newContainer != e.oldContainer) {
      val dragging = readDragPayload(e.dragEvent.source)
      val oldContainer = readDragContainer(e.oldContainer)
      val newContainer = readDragContainer(e.newContainer)

      (dragging, oldContainer, newContainer) match {
        case (Some(dragging), Some(oldContainer), Some(newContainer)) =>
          scribe.debug(s"Sorting: $oldContainer -> $dragging -> $newContainer${ctrl.ifTrue(" +ctrl")}${shift.ifTrue(" +shift")}")
          sortableActions.applyOrElse((e, dragging, oldContainer, newContainer, ctrl, shift), (other:(SortableEvent, DragPayload, DragContainer, DragContainer, Boolean, Boolean)) => scribe.warn(s"sort combination not handled."))
        case other => scribe.warn(s"incomplete drag action: $other")
      }
    }
  }
}
