package wust.webApp

import monix.execution.Scheduler
import monix.reactive.subjects.PublishSubject
import shopify.draggable.{DragEvent, DragOutEvent, DragOverEvent, Draggable}
import wust.graph.GraphChanges
import wust.ids.{Cuid, NodeId}

class DragEvents(state: GlobalState, draggable: Draggable)(implicit scheduler: Scheduler) {
  val dragOverEvent = PublishSubject[DragOverEvent]
  val dragOutEvent = PublishSubject[DragOutEvent]
  val dragEvent = PublishSubject[DragEvent]
  val lastDragOverId = PublishSubject[Option[NodeId]]
  // TODO: Use js properties
  dragOverEvent.foreach { e =>
    //        val parentId: NodeId =  e.over.asInstanceOf[js.Dynamic].selectDynamic("woost_nodeid").asInstanceOf[NodeId]
    val parentId: NodeId =
      NodeId(Cuid.fromCuidString(e.over.attributes.getNamedItem("woost_nodeid").value))

    scribe.info(s"Dragging over: ${parentId.toCuidString}")

    lastDragOverId.onNext(Some(parentId))
  }

  dragOutEvent.foreach { e =>
    lastDragOverId.onNext(None)
  }

  dragEvent
    .withLatestFrom(lastDragOverId)((e, lastOverId) => (e, lastOverId))
    .foreach {
      case (e, Some(parentId)) =>
        val childId: NodeId =
          NodeId(Cuid.fromCuidString(e.source.attributes.getNamedItem("woost_nodeid").value))
        if (parentId != childId) {
          val changes = GraphChanges.connectParent(childId, parentId)
          state.eventProcessor.enriched.changes.onNext(changes)
          lastDragOverId.onNext(None)
          scribe.info(s"Added GraphChange after drag: $changes")
        }
      case _ =>
    }

  draggable.on[DragOverEvent]("drag:over", e => {
    dragOverEvent.onNext(e)
  })
  draggable.on[DragOutEvent]("drag:out", dragOutEvent.onNext(_))
  draggable.on[DragEvent]("drag:stop", dragEvent.onNext(_))
}
