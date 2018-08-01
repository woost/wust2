package wust.webApp

import acyclic.skipped
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import org.scalajs.dom.console
import shopify.draggable._
import wust.graph.{Edge, GraphChanges}
import wust.ids.NodeId
import wust.webApp.DragItem.{payloadDecoder, targetDecoder}
import wust.webApp.views.Elements._

import scala.scalajs.js


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
  private val sortableStartEvent = PublishSubject[SortableStartEvent]
  private val sortableStopEvent = PublishSubject[SortableStopEvent]
  private val sortableSortEvent = PublishSubject[SortableSortEvent]
  private val lastDragTarget = PublishSubject[Option[DragTarget]] //TODO: observable derived from other subjects

  val status: Observable[DragStatus] = Observable.merge(dragStartEvent.map(_ => DragStatus.Dragging), dragStopEvent.map(_ => DragStatus.None))


  draggable.on[DragEvent]("drag:start", dragStartEvent.onNext(_))
  draggable.on[DragOverEvent]("drag:over", dragOverEvent.onNext(_))
  draggable.on[DragOutEvent]("drag:out", dragOutEvent.onNext(_))
  draggable.on[DragEvent]("drag:stop", dragStopEvent.onNext(_))

  draggable.on[SortableStartEvent]("sortable:start", sortableStartEvent.onNext(_))
  draggable.on[SortableSortEvent]("sortable:sort", sortableSortEvent.onNext(_))
  draggable.on[SortableStopEvent]("sortable:stop", sortableStopEvent.onNext(_))

  //  draggable.on[DroppableDroppedEvent]("droppable:dropped", droppableDroppedEvent.onNext(_))
  //  draggable.on[DroppableReturnedEvent]("droppable:returned", droppableReturnedEvent.onNext(_))

//  draggable.on("drag:start", () => console.log("drag:start"))
//  draggable.on("drag:over", () => console.log("drag:over"))
//  draggable.on("drag:out", () => console.log("drag:out"))
//  draggable.on("drag:stop", () => console.log("drag:stop"))
//
//  draggable.on("sortable:start", (e:SortableEvent) => console.log("sortable:start", e))
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
          case (dragging:Kanban.Card, target:SingleNode) => moveInto(dragging.nodeId, target.nodeId)
          case (dragging:Kanban.Column, target:SingleNode) => moveInto(dragging.nodeId, target.nodeId)

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
    { e =>
      scribe.error("Error in dragStopEvent")
      throw e
    }
  )

  val sortableActions:PartialFunction[(SortableEvent, DragPayload, DragContainer, DragContainer),Unit] = {
    import DragContainer._
    def graph = state.graph.now

    {
      case (e, dragging: DragItem.Kanban.ToplevelColumn, from: Kanban.ColumnArea, into: Kanban.ColumnArea) =>
        console.log("reordering columns")
        // TODO: persist ordering

      case (e, dragging: DragItem.Kanban.Item, from: Kanban.Area, into: Kanban.Column) =>
        val move = GraphChanges.changeTarget(Edge.Parent)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)

        val moveStaticInParents = if(graph.isStaticParentIn(dragging.nodeId, from.parentIds)) {
          GraphChanges.changeTarget(Edge.StaticParentIn)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)
        } else GraphChanges.empty

        val alreadyInParent = graph.children(into.nodeId).contains(dragging.nodeId)
        if (alreadyInParent) {
//          console.log("already in parent! removing dom element:", e.dragEvent.originalSource)
          defer(removeDomElement(e.dragEvent.originalSource))
        }
        state.eventProcessor.enriched.changes.onNext(move merge moveStaticInParents)

      case (e, dragging: DragItem.Kanban.SubItem, from: Kanban.Area, into: Kanban.NewColumnArea) =>
        val move = GraphChanges.changeTarget(Edge.Parent)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)
        // always make new columns static
        val moveStaticInParents = GraphChanges.changeTarget(Edge.StaticParentIn)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)

        val alreadyInParent = into.parentIds.exists(parentId => graph.children(parentId).contains(dragging.nodeId))
        if (alreadyInParent || dragging.isInstanceOf[DragItem.Kanban.Card]) { // remove card, as it will be replaced by a column via graph events
//          console.log("already in parent! removing dom element:", e.dragEvent.originalSource)
          defer(removeDomElement(e.dragEvent.originalSource))
        }
        state.eventProcessor.enriched.changes.onNext(move merge moveStaticInParents)

      case (e, dragging: DragItem.Kanban.SubItem, from: Kanban.Area, into: Kanban.IsolatedNodes) =>
        val move = GraphChanges.changeTarget(Edge.Parent)(dragging.nodeId :: Nil, from.parentIds, into.parentIds)
        val removeStatic = GraphChanges.disconnect(Edge.StaticParentIn)(dragging.nodeId, from.parentIds)
        // will be reintroduced by change event, so we delete the original node:
        state.eventProcessor.enriched.changes.onNext(move merge removeStatic)
        defer(removeDomElement(e.dragEvent.originalSource))
    }
  }

  sortableStartEvent.map { e =>
    val source = decodeFromAttr[DragPayload](e.dragEvent.source, DragItem.payloadAttrName)
    source match {
      case Some(DragItem.DisableDrag) => e.cancel()
      case _ =>
    }
  }.subscribe(
    _ => Ack.Continue,
    { e =>
      scribe.error("Error in sortableStartEvent")
      throw e
    }
  )

  sortableSortEvent.map { e =>
    val overContainerWorkaround = e.dragEvent.asInstanceOf[js.Dynamic].overContainer.asInstanceOf[dom.html.Element] // https://github.com/Shopify/draggable/issues/256
    val sourceContainerWorkaround = e.dragEvent.asInstanceOf[js.Dynamic].sourceContainer.asInstanceOf[dom.html.Element] // TODO: report as feature request
    val dragging = decodeFromAttr[DragPayload](e.dragEvent.source, DragItem.payloadAttrName)
    val overContainer = decodeFromAttr[DragContainer](overContainerWorkaround, DragContainer.attrName)
    val sourceContainer = decodeFromAttr[DragContainer](sourceContainerWorkaround, DragContainer.attrName).orElse(overContainer)

    // white listing allowed sortable actions
    (dragging, sourceContainer, overContainer) match {
      case (Some(dragging), Some(sourceContainer), Some(overContainer)) if sortableActions.isDefinedAt((e, dragging, sourceContainer, overContainer)) => // allowed
      case (Some(dragging), Some(sourceContainer), Some(overContainer)) => println(s"not handled ${(dragging, sourceContainer, overContainer)}"); e.cancel()
      case _ => e.cancel() // not allowed
    }
  }.subscribe(
    _ => Ack.Continue,
    { e =>
      scribe.error("Error in sortableSortEvent")
      throw e
    }
  )

  sortableStopEvent.map { e =>
    console.log(e)
    if(e.newContainer != e.oldContainer) {
      val dragging = decodeFromAttr[DragPayload](e.dragEvent.source, DragItem.payloadAttrName)
      val oldContainer = decodeFromAttr[DragContainer](e.oldContainer, DragContainer.attrName)
      val newContainer = decodeFromAttr[DragContainer](e.newContainer, DragContainer.attrName)
      (dragging, oldContainer, newContainer) match {
        case (Some(dragging), Some(oldContainer), Some(newContainer)) =>
          sortableActions.applyOrElse((e, dragging, oldContainer, newContainer), (other:(SortableEvent, DragPayload, DragContainer, DragContainer)) => println(s"not handled: $other"))
        case other => println(s"incomplete drag action: $other")
      }
    }
  }.subscribe(
    _ => Ack.Continue,
    { e =>
      scribe.error("Error in sortableStopEvent")
      throw e
    }
  )
}
