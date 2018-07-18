package wust.webApp

import monix.execution.Scheduler
import monix.reactive.subjects.PublishSubject
import shopify.draggable.{DragEvent, DragOutEvent, DragOverEvent, Draggable}
import wust.graph.GraphChanges
import wust.ids.{Cuid, NodeId}
import io.circe.parser.decode

sealed trait DragPayload
object DragPayload extends wust.ids.serialize.Circe {
  case class Node(nodeId: NodeId) extends DragPayload
  case class Tag(nodeId: NodeId) extends DragPayload

  import io.circe._, io.circe.generic.semiauto._
  implicit val decoder: Decoder[DragPayload] = deriveDecoder[DragPayload]
  implicit val encoder: Encoder[DragPayload] = deriveEncoder[DragPayload]

  val attrName = "data-dragpayload"
}

sealed trait DragTarget
object DragTarget extends wust.ids.serialize.Circe {
  case class Node(nodeId: NodeId) extends DragTarget
  case class Tag(nodeId: NodeId) extends DragTarget

  import io.circe._, io.circe.generic.semiauto._
  implicit val decoder: Decoder[DragTarget] = deriveDecoder[DragTarget]
  implicit val encoder: Encoder[DragTarget] = deriveEncoder[DragTarget]

  val attrName = "data-dragtarget"
}

class DragEvents(state: GlobalState, draggable: Draggable)(implicit scheduler: Scheduler) {
  private val dragOverEvent = PublishSubject[DragOverEvent]
  private val dragOutEvent = PublishSubject[DragOutEvent]
  private val dragEvent = PublishSubject[DragEvent]
  private val lastDragTarget = PublishSubject[Option[DragTarget]]

  dragOverEvent.foreach { e =>
    val target = decode[DragTarget](e.over.attributes.getNamedItem(DragTarget.attrName).value).toOption
    scribe.info(s"Dragging over: $target")
    lastDragTarget.onNext(target)
  }

  dragOutEvent.foreach { e =>
    lastDragTarget.onNext(None)
  }

  dragEvent
    .withLatestFrom(lastDragTarget){(e, targetOpt) =>
      val payloadOpt = decode[DragPayload](e.source.attributes.getNamedItem(DragPayload.attrName).value).toOption
      (payloadOpt, targetOpt)
    }
    .foreach {
      case (Some(payload), Some(target)) =>
        val changes = (payload,target) match {
          case (DragPayload.Node(draggingNodeId), DragTarget.Node(targetNodeId)) if draggingNodeId != targetNodeId =>
            GraphChanges.connectParent(draggingNodeId, targetNodeId)
          case (DragPayload.Node(draggingNodeId), DragTarget.Tag(targetNodeId)) if draggingNodeId != targetNodeId =>
            GraphChanges.connectParent(draggingNodeId, targetNodeId)
          case (DragPayload.Tag(draggingNodeId), DragTarget.Node(targetNodeId)) if draggingNodeId != targetNodeId =>
            GraphChanges.connectParent(targetNodeId, draggingNodeId)
          case (DragPayload.Tag(draggingNodeId), DragTarget.Tag(targetNodeId)) if draggingNodeId != targetNodeId =>
            GraphChanges.connectParent(draggingNodeId, targetNodeId)
        }

        state.eventProcessor.enriched.changes.onNext(changes)
        lastDragTarget.onNext(None)
        scribe.info(s"Added GraphChange after drag: $changes")

      case _ =>
    }

  draggable.on[DragOverEvent]("drag:over", e => {
    dragOverEvent.onNext(e)
  })
  draggable.on[DragOutEvent]("drag:out", dragOutEvent.onNext(_))
  draggable.on[DragEvent]("drag:stop", dragEvent.onNext(_))
}
