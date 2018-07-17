package wust.webApp

import monix.execution.Scheduler
import monix.reactive.subjects.PublishSubject
import shopify.draggable.{DragEvent, DragOutEvent, DragOverEvent, Draggable}
import wust.graph.GraphChanges
import wust.ids.{Cuid, NodeId}

class DragEvents(state: GlobalState, draggable: Draggable)(implicit scheduler: Scheduler) {
  private val dragOverEvent = PublishSubject[DragOverEvent]
  private val dragOutEvent = PublishSubject[DragOutEvent]
  private val dragEvent = PublishSubject[DragEvent]
  private val lastDragOverId = PublishSubject[Option[NodeId]]

  dragOverEvent.foreach { e =>
    // TODO: Use js properties
    //        val parentId: NodeId =  e.over.asInstanceOf[js.Dynamic].selectDynamic("woost_nodeid").asInstanceOf[NodeId]

    val hoveringNodeId = NodeId(
      Cuid.fromCuidString(e.over.attributes.getNamedItem("woost_nodeid").value)
    ) //TODO: encode as uuid

    scribe.info(s"Dragging over: ${hoveringNodeId.toCuidString}")

    lastDragOverId.onNext(Some(hoveringNodeId))
  }

  dragOutEvent.foreach { e =>
    lastDragOverId.onNext(None)
  }

  dragEvent
    .withLatestFrom(lastDragOverId)((e, lastOverId) => (e, lastOverId))
    .foreach {
      case (e, Some(hoveringNodeId)) =>
        //TODO: we need typesafety here. Especially for dragtype to get match exhaustiveness
        val draggingNodeId =
          NodeId(Cuid.fromCuidString(e.source.attributes.getNamedItem("woost_nodeid").value))
        val dragtype = e.source.attributes.getNamedItem("woost_dragtype").value

        if (hoveringNodeId != draggingNodeId) {
          val changes = dragtype match {
            case "node" => GraphChanges.connectParent(draggingNodeId, hoveringNodeId)
            case "tag"  => GraphChanges.connectParent(hoveringNodeId, draggingNodeId)
          }
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
