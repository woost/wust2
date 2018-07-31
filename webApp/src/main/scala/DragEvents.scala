package wust.webApp

import acyclic.skipped // file is allowed in dependency cycle
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom.console
import shopify.draggable._
import wust.graph.{Edge, GraphChanges}
import wust.ids.NodeId
import wust.webApp.DragItem.{payloadDecoder, targetDecoder}
import wust.webApp.views.Elements._


sealed trait DragStatus
object DragStatus {
  case object None extends DragStatus
  case object Dragging extends DragStatus
}


class DragEvents(state: GlobalState, draggable: Draggable)(implicit scheduler: Scheduler) {
  private val dragStartEvent = PublishSubject[DragEvent] //TODO type event
  private val dragOverEvent = PublishSubject[DragOverEvent]
  private val dragOutEvent = PublishSubject[DragOutEvent]
  private val dragStopEvent = PublishSubject[DragEvent] //TODO type event
  private val sortableStopEvent = PublishSubject[SortableStopEvent]
  private val lastDragTarget = PublishSubject[Option[DragTarget]] //TODO: observable derived from other subjects

  val status: Observable[DragStatus] = Observable.merge(dragStartEvent.map(_ => DragStatus.Dragging), dragStopEvent.map(_ => DragStatus.None))


  draggable.on[DragEvent]("drag:start", dragStartEvent.onNext(_))
  draggable.on[DragOverEvent]("drag:over", dragOverEvent.onNext(_))
  draggable.on[DragOutEvent]("drag:out", dragOutEvent.onNext(_))
  draggable.on[DragEvent]("drag:stop", dragStopEvent.onNext(_))

  draggable.on[SortableStopEvent]("sortable:stop", sortableStopEvent.onNext(_))

  //  draggable.on[DroppableDroppedEvent]("droppable:dropped", droppableDroppedEvent.onNext(_))
  //  draggable.on[DroppableReturnedEvent]("droppable:returned", droppableReturnedEvent.onNext(_))

//  draggable.on("drag:start", () => console.log("drag:start"))
//  draggable.on("drag:over", () => console.log("drag:over"))
//  draggable.on("drag:out", () => console.log("drag:out"))
//  draggable.on("drag:stop", () => console.log("drag:stop"))
//
//  draggable.on("sortable:start", () => console.log("sortable:start"))
//  draggable.on("sortable:sort", () => console.log("sortable:sort"))
//  draggable.on("sortable:sorted", () => console.log("sortable:sorted"))
//  draggable.on("sortable:stop", () => console.log("sortable:stop"))
//
//  draggable.on("droppable:dropped", () => console.log("droppable:dropped"))
//  draggable.on("droppable:returned", () => console.log("droppable:returned"))

  private def addTag(nodeId:NodeId, tagId:NodeId):Unit = addTag(nodeId :: Nil, tagId)
  private def addTag(nodeIds:Seq[NodeId], tagId:NodeId):Unit = {
    //TODO: create GraphChanges factory for this
    val changes:GraphChanges = GraphChanges.connect(Edge.Parent)(nodeIds, tagId)

    if(changes.isEmpty) {
      scribe.info(s"Attempted to create self-loop. Doing nothing.")
    } else {
      state.eventProcessor.enriched.changes.onNext(changes)
      scribe.info(s"Added GraphChange after drag: $changes")
    }
  }

  private def moveInto(nodeId:NodeId, parentId:NodeId):Unit = moveInto(nodeId :: Nil, parentId)
  private def moveInto(nodeIds:Seq[NodeId], parentId:NodeId):Unit = {
    val changes:GraphChanges = GraphChanges.moveInto(state.graph.now, nodeIds, parentId)

    if(changes.isEmpty) {
      scribe.info(s"Attempted to create self-loop. Doing nothing.")
    } else {
      state.eventProcessor.enriched.changes.onNext(changes)
      scribe.info(s"Added GraphChange after drag: $changes")
    }
  }

  dragOverEvent.map(_.over).map { elem =>
    val target = decodeFromAttr[DragTarget](elem,DragItem.targetAttrName)
    target.foreach{target => scribe.info(s"Dragging over: $target")}
    target
  }.subscribe(lastDragTarget)

  dragOutEvent.map(_ => None).subscribe(lastDragTarget)

  dragStopEvent.map { e =>
    console.log(e)
    decodeFromAttr[DragPayload](e.source, DragItem.payloadAttrName)
  }.withLatestFrom(lastDragTarget){
      case (Some(payload), Some(target)) if payload != target => // for sortable payload == target, so no normal drag is triggered
        import DragItem._
        println(s"Draggable stop: $payload -> $target")
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

          case other => println(s"not handled: $other")
        }

        lastDragTarget.onNext(None)

      case _ =>
    }.subscribe(
      _ => Ack.Continue,
      e => scribe.error("Error in drag events", e)
    )

  sortableStopEvent.foreach { e =>
    import DragContainer._
    if(e.newContainer != e.oldContainer) {
      val dragging = decodeFromAttr[DragPayload](e.dragEvent.source, DragItem.payloadAttrName)
      val oldContainer = decodeFromAttr[DragContainer](e.oldContainer, DragContainer.attrName)
      val newContainer = decodeFromAttr[DragContainer](e.newContainer, DragContainer.attrName)
      (dragging, oldContainer, newContainer) match {
        case (Some(dragging), Some(oldContainer), Some(newContainer)) =>
          (dragging, oldContainer, newContainer) match {
            case (dragging:DragItem.KanbanItem, oldContainer:KanbanColumn, newContainer:KanbanColumn) =>
              val alreadyInParent = state.graph.now.children(newContainer.nodeId).contains(dragging.nodeId)
              val disconnect = GraphChanges.disconnect(Edge.Parent)(dragging.nodeId, oldContainer.nodeId)
              val connect = if(alreadyInParent) GraphChanges.empty else GraphChanges.connect(Edge.Parent)(dragging.nodeId, newContainer.nodeId)
              if(alreadyInParent) {
//                console.log("already in parent! removing dom element:", e.dragEvent.originalSource)
                defer(removeDomElement(e.dragEvent.originalSource))
              }
              state.eventProcessor.enriched.changes.onNext(disconnect.merge(connect))

            case (dragging:DragItem.KanbanItem, page:Page, newContainer:KanbanColumn) =>
              val connect = GraphChanges.connect(Edge.Parent)(dragging.nodeId, newContainer.nodeId)
              val disconnect = GraphChanges.disconnect(Edge.Parent)(dragging.nodeId, page.parentIds)
              state.eventProcessor.enriched.changes.onNext(connect merge disconnect)

            case (dragging:DragItem.KanbanItem, oldContainer:KanbanColumn, page:Page) =>
              val disconnect = GraphChanges.disconnect(Edge.Parent)(dragging.nodeId, oldContainer.nodeId)
              val connect = GraphChanges.connect(Edge.Parent)(dragging.nodeId, page.parentIds)
              // will be reintroduced by change event, so we delete the original node:
              defer(removeDomElement(e.dragEvent.originalSource))
              state.eventProcessor.enriched.changes.onNext(disconnect merge connect)

            case other => println(s"not handled: $other")
          }

        case other => println(s"incomplete drag action: $other")
      }
    }
  }
}
