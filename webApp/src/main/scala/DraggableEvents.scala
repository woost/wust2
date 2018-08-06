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
import wust.webApp.outwatchHelpers._

import scala.scalajs.js


sealed trait DragStatus
object DragStatus {
  case object None extends DragStatus
  case object Dragging extends DragStatus
}


class DraggableEvents(state: GlobalState, draggable: Draggable) {
  private val dragStartEvent = PublishSubject[DragEvent] //TODO type event
  private val dragOverEvent = PublishSubject[DragOverEvent]
  private val dragOutEvent = PublishSubject[DragOutEvent]
  private val dragStopEvent = PublishSubject[DragEvent] //TODO type event
  private val lastDragTarget = PublishSubject[Option[DragTarget]] //TODO: observable derived from other subjects

  val status: Observable[DragStatus] = Observable.merge(dragStartEvent.map(_ => DragStatus.Dragging), dragStopEvent.map(_ => DragStatus.None))


  draggable.on[DragEvent]("drag:start", dragStartEvent.onNext _)
  draggable.on[DragOverEvent]("drag:over", dragOverEvent.onNext _)
  draggable.on[DragOutEvent]("drag:out", dragOutEvent.onNext _)
  draggable.on[DragEvent]("drag:stop", dragStopEvent.onNext _)

  //  draggable.on("drag:start", () => console.log("drag:start"))
  //  draggable.on("drag:over", () => console.log("drag:over"))foreach
  //  draggable.on("drag:out", () => console.log("drag:out"))
  //  draggable.on("drag:stop", () => console.log("drag:stop"))

  private def submit(changes:GraphChanges) = {
    state.eventProcessor.enriched.changes.onNext(changes)
  }
  private def addTag(nodeId:NodeId, tagId:NodeId):Unit = addTag(nodeId :: Nil, tagId)
  private def addTag(nodeIds:Seq[NodeId], tagId:NodeId):Unit = {
    submit(GraphChanges.connect(Edge.Parent)(nodeIds, tagId))
  }

  private def moveInto(nodeId:NodeId, parentId:NodeId):Unit = moveInto(nodeId :: Nil, parentId :: Nil)
  private def moveInto(nodeId:NodeId, parentIds:Iterable[NodeId]):Unit = moveInto(nodeId :: Nil, parentIds)
  private def moveInto(nodeIds:Iterable[NodeId], parentIds:Iterable[NodeId]):Unit = {
    submit(GraphChanges.moveInto(state.graph.now, nodeIds, parentIds))
  }


  val dragActions:PartialFunction[(DragPayload, DragTarget),Unit] = {
    import DragItem._
    {
      case (dragging: Chat.Message, target: Chat.Thread) => moveInto(dragging.nodeId, target.nodeId)

      case (dragging: Kanban.Card, target: SingleNode) => moveInto(dragging.nodeId, target.nodeId)
      case (dragging: Kanban.Column, target: SingleNode) => moveInto(dragging.nodeId, target.nodeId)

      case (dragging: SelectedNode, target: SingleNode) => addTag(dragging.nodeIds, target.nodeId)
      case (dragging: SelectedNodes, target: SingleNode) => addTag(dragging.nodeIds, target.nodeId)
      case (dragging: SelectedNodes, SelectedNodesBar) => // do nothing, since already selected
      case (dragging: AnyNodes, SelectedNodesBar) => state.selectedNodeIds.update(_ ++ dragging.nodeIds)

      case (dragging: AnyNodes, target: Channel) => addTag(dragging.nodeIds, target.nodeId)

      case (dragging: ChildNode, target: ParentNode) => moveInto(dragging.nodeId, target.nodeId)
      case (dragging: ChildNode, target: MultiParentNodes) => moveInto(dragging.nodeId, target.nodeIds)
      case (dragging: ChildNode, target: ChildNode) => moveInto(dragging.nodeId, target.nodeId)
      case (dragging: ParentNode, target: SingleNode) => addTag(target.nodeId, dragging.nodeId)
    }
  }

  dragStartEvent.foreachTry { e =>
    val source = decodeFromAttr[DragPayload](e.source, DragItem.payloadAttrName)
    source match {
      case Some(DragItem.DisableDrag) => e.cancel()
      case _ =>
    }
  }

  dragOverEvent.map(_.over).map { elem =>
    val target = decodeFromAttr[DragTarget](elem,DragItem.targetAttrName)
//    target.foreach{target => scribe.info(s"Dragging over: $target")}
    target
  }.subscribe(lastDragTarget)

  dragOutEvent.map(_ => None).subscribe(lastDragTarget)

  dragStopEvent.map { e =>
    decodeFromAttr[DragPayload](e.source, DragItem.payloadAttrName)
  }.withLatestFrom(lastDragTarget)((p,t) => (p,t)).foreachTry { pt =>
    pt match {
      case (Some(payload), Some(target)) =>
        println(s"Dropped: $payload -> $target")
        dragActions.applyOrElse((payload, target), (other:(DragPayload, DragTarget)) => println(s"drag combination not handled."))
      case other => println(s"incomplete drag action: $other")
    }
    lastDragTarget.onNext(None)
  }
}
