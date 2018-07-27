package wust.webApp

import io.circe.parser.decode
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import shopify.draggable._
import wust.graph.GraphChanges
import wust.ids.NodeId
import wust.webApp.DragItem.{payloadDecoder, targetDecoder}


sealed trait DragStatus
object DragStatus {
  case object None extends DragStatus
  case object Dragging extends DragStatus
}


class DragEvents(state: GlobalState, draggable: Draggable)(implicit scheduler: Scheduler) {
  private val dragStartEvent = PublishSubject[DragEvent] //TODO type event
  private val dragOverEvent = PublishSubject[DragOverEvent]
  private val dragOutEvent = PublishSubject[DragOutEvent]
  private val droppableDroppedEvent = PublishSubject[DroppableDroppedEvent] //TODO type event
  private val droppableReturnedEvent = PublishSubject[DroppableReturnedEvent] //TODO type event
  private val dragStopEvent = PublishSubject[DragEvent] //TODO type event
  private val lastDragTarget = PublishSubject[Option[DragTarget]] //TODO: observable derived from other subjects

  val status: Observable[DragStatus] = Observable.merge(dragStartEvent.map(_ => DragStatus.Dragging), dragStopEvent.map(_ => DragStatus.None))

  private val currentDragPayload: Observable[Option[DragPayload]] = dragStopEvent.map { e =>
    decode[DragPayload](e.source.attributes.getNamedItem(DragItem.payloadAttrName).value).toOption
  }

  private def addTag(nodeId:NodeId, tagId:NodeId):Unit = addTag(nodeId :: Nil, tagId)
  private def addTag(nodeIds:Seq[NodeId], tagId:NodeId):Unit = {
    //TODO: create GraphChanges factory for this
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

  private def moveInto(nodeId:NodeId, parentId:NodeId):Unit = moveInto(nodeId :: Nil, parentId)
  private def moveInto(nodeIds:Seq[NodeId], parentId:NodeId):Unit = {
    val changes:GraphChanges = nodeIds.foldLeft(GraphChanges.empty){(changes, nodeId) =>
      changes.merge (
        if(nodeId != parentId) GraphChanges.moveInto(state.graph.now, nodeId, parentId)
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

  Observable.merge(dragOverEvent.map(_.over), droppableDroppedEvent.map(_.dropzone)).map { e =>
    println("over...")
    val target = for {
      attr <- Option(e.attributes.getNamedItem(DragItem.targetAttrName))
      target <- decode[DragTarget](attr.value).toOption
    } yield target
    println(target)
    scribe.info(s"Dragging over: $target")
    target
  }.subscribe(lastDragTarget)

  dragOutEvent.map(_ => None).subscribe(lastDragTarget)

  currentDragPayload
    .withLatestFrom(lastDragTarget){
      case (Some(payload), Some(target)) =>
        import DragItem._
        (payload,target) match {
          case (dragging:KanbanCard, target:SingleNode) => moveInto(dragging.nodeId, target.nodeId)
          case (dragging:KanbanColumn, target:SingleNode) => moveInto(dragging.nodeId, target.nodeId)

          case (dragging:AnyNodes, target:Channel) => addTag(dragging.nodeIds, target.nodeId)

          case (dragging:ChildNode, target:ParentNode) => addTag(dragging.nodeId, target.nodeId)
          case (dragging:ChildNode, target:ChildNode) => addTag(dragging.nodeId, target.nodeId)
          case (dragging:ParentNode, target:SingleNode) => addTag(target.nodeId, dragging.nodeId)

          case (dragging:SelectedNodes, target:SingleNode) => addTag(dragging.nodeIds, target.nodeId)
          case (dragging:SelectedNodes, SelectedNodesBar) => // do nothing, since already selected
          case (dragging:AnyNodes, SelectedNodesBar) => state.selectedNodeIds.update(_ ++ dragging.nodeIds)
        }

        lastDragTarget.onNext(None)

      case _ =>
    }
    .subscribe(
      _ => Ack.Continue,
      e => scribe.error("Error in drag events", e)
    )

  draggable.on[DragEvent]("drag:start", dragStartEvent.onNext(_))
  draggable.on[DragOverEvent]("drag:over", dragOverEvent.onNext(_))
  draggable.on[DragOutEvent]("drag:out", dragOutEvent.onNext(_))
  draggable.on[DragEvent]("drag:stop", dragStopEvent.onNext(_))
  draggable.on[DroppableDroppedEvent]("droppable:dropped", droppableDroppedEvent.onNext(_))
  draggable.on[DroppableReturnedEvent]("droppable:returned", droppableReturnedEvent.onNext(_))

  draggable.on("droppable:dropped", () => dom.console.log("droppable:dropped"))
  draggable.on("droppable:returned", () => dom.console.log("droppable:returned"))

  draggable.on("drag:start", () => dom.console.log("drag:start"))
  draggable.on("drag:over", () => dom.console.log("drag:over"))
  draggable.on("drag:out", () => dom.console.log("drag:out"))
  draggable.on("drag:stop", () => dom.console.log("drag:stop"))


}
