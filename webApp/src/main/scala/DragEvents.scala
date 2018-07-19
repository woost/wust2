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
  case class Nodes(nodeIds: Seq[NodeId]) extends DragPayload

  import io.circe._, io.circe.generic.semiauto._
  implicit val decoder: Decoder[DragPayload] = deriveDecoder[DragPayload]
  implicit val encoder: Encoder[DragPayload] = deriveEncoder[DragPayload]

  val attrName = "data-dragpayload"
}

sealed trait DragTarget
object DragTarget extends wust.ids.serialize.Circe {
  case class Node(nodeId: NodeId) extends DragTarget
  case class Tag(nodeId: NodeId) extends DragTarget
  case object SelectedNodes extends DragTarget

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

  private def addTag(nodeId:NodeId, tagId:NodeId):Unit = addTag(nodeId :: Nil, tagId)
  private def addTag(nodeIds:Seq[NodeId], tagId:NodeId):Unit = {
    val changes:GraphChanges = nodeIds.foldLeft(GraphChanges.empty){(changes, nodeId) =>
      changes.merge (
        if(nodeId != tagId) GraphChanges.connectParent(nodeId, tagId)
        else GraphChanges.empty
      )
    }

    if(changes.isEmpty) {
      scribe.info(s"Attempted to create self-loop. Doing nothing.")
    } else {
      state.eventProcessor.enriched.changes.onNext(changes)
      scribe.info(s"Added GraphChange after drag: $changes")
    }
  }

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
          case (DragPayload.Node(draggingId), DragTarget.Node(targetId)) => addTag(draggingId, targetId)
          case (DragPayload.Node(draggingId), DragTarget.Tag(targetId)) => addTag(draggingId, targetId)
          case (DragPayload.Tag(draggingId), DragTarget.Node(targetId)) => addTag(targetId, draggingId)
          case (DragPayload.Tag(draggingId), DragTarget.Tag(targetId)) => addTag(draggingId, targetId)

          case (DragPayload.Tag(draggingId), DragTarget.SelectedNodes) => state.selectedNodeIds.update(_ + draggingId)
          case (DragPayload.Node(draggingId), DragTarget.SelectedNodes) => state.selectedNodeIds.update(_ + draggingId)
          case (DragPayload.Nodes(draggingIds), DragTarget.SelectedNodes) => state.selectedNodeIds.update(_ ++ draggingIds)

          case (DragPayload.Nodes(draggingIds), DragTarget.Node(targetId)) => addTag(draggingIds, targetId)
          case (DragPayload.Nodes(draggingIds), DragTarget.Tag(targetId)) => addTag(draggingIds, targetId)
        }

        lastDragTarget.onNext(None)

      case _ =>
    }

  draggable.on[DragOverEvent]("drag:over", e => {
    dragOverEvent.onNext(e)
  })
  draggable.on[DragOutEvent]("drag:out", dragOutEvent.onNext(_))
  draggable.on[DragEvent]("drag:stop", dragEvent.onNext(_))
}
