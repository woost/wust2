package wust.webApp.dragdrop

import googleAnalytics.Analytics
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom.ext.KeyCode
import draggable._
import wust.graph.{Edge, GraphChanges, Tree}
import wust.ids.NodeId
import wust.util._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._


sealed trait DragStatus
object DragStatus {
  case object None extends DragStatus
  case object Dragging extends DragStatus
}


class DraggableEvents(state: GlobalState, draggable: Draggable) {
  private val dragStartEvent = PublishSubject[DragStartEvent]
  private val dragOverEvent = PublishSubject[DragOverEvent]
  private val dragOutEvent = PublishSubject[DragOutEvent]
  private val dragStopEvent = PublishSubject[DragEvent] //TODO type event
  private val lastDragTarget = PublishSubject[Option[DragTarget]] //TODO: observable derived from other subjects


  val filteredDragStartEvent = dragStartEvent.filter { e =>
    // copy dragpayload reference from source to mirror // https://github.com/Shopify/draggable/issues/245
    val payload:Option[DragPayload] = readDragPayload(e.originalSource)
    payload.foreach ( writeDragPayload(e.mirror, _) )

    payload match {
      case Some(DragItem.DisableDrag) => e.cancel(); false
      case _ => true
    }
  }


  val status: Observable[DragStatus] = Observable.merge(filteredDragStartEvent.map(_ => DragStatus.Dragging), dragStopEvent.map(_ => DragStatus.None))


  draggable.on[DragStartEvent]("drag:start", dragStartEvent.onNext _)
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
    state.selectedNodeIds() = Set.empty[NodeId]
  }
  private def addTag(nodeIds:Seq[NodeId], tagIds:Iterable[NodeId]):Unit = {
    submit(GraphChanges.connect(Edge.Parent)(nodeIds, tagIds))
    state.selectedNodeIds() = Set.empty[NodeId]
  }

  private def moveInto(nodeId:NodeId, parentId:NodeId):Unit = moveInto(nodeId :: Nil, parentId :: Nil)
  private def moveInto(nodeId:NodeId, parentIds:Iterable[NodeId]):Unit = moveInto(nodeId :: Nil, parentIds)
  private def moveInto(nodeIds:Iterable[NodeId], parentIds:Iterable[NodeId]):Unit = {
    submit(GraphChanges.moveInto(state.graph.now, nodeIds, parentIds))
    state.selectedNodeIds() = Set.empty[NodeId]
  }
  private def moveChannel(channelId:NodeId, targetChannelId:NodeId):Unit = {

    def filterParents(nodeId:NodeId, tree:Tree):Set[NodeId] = {
      tree match {
        case _:Tree.Leaf => Set.empty
        case Tree.Parent(parentNode, children) =>
          val parent = if( children.exists(_.node.id == nodeId) ) Set(parentNode.id) else Set.empty
          parent ++ children.flatMap(child => filterParents(nodeId, child))
      }
    }

    val topologicalChannelParents = filterParents(channelId, state.channelTree.now) - state.user.now.channelNodeId
    val disconnect:GraphChanges = GraphChanges.disconnect(Edge.Parent)(channelId, topologicalChannelParents)
    val connect:GraphChanges = GraphChanges.connect(Edge.Parent)(channelId, targetChannelId)
    submit(disconnect merge connect)
    state.selectedNodeIds() = Set.empty[NodeId]
  }


  val dragActions:PartialFunction[(DragPayload, DragTarget, Boolean, Boolean),Unit] = {
    // The booleans: Ctrl is dow, Shift is down
    import DragItem._
    {
      case (dragging: Chat.Message, target: Chat.Thread, false, false) => moveInto(dragging.nodeId, target.nodeId)

      case (dragging: Kanban.Card, target: SingleNode, false, false) => moveInto(dragging.nodeId, target.nodeId)
      case (dragging: Kanban.Column, target: SingleNode, false, false) => moveInto(dragging.nodeId, target.nodeId)

      case (dragging: SelectedNode, target: SingleNode, false, false) => addTag(dragging.nodeIds, target.nodeId)
      case (dragging: SelectedNodes, target: SingleNode, false, false) => addTag(dragging.nodeIds, target.nodeId)
      case (dragging: SelectedNodes, SelectedNodesBar, false, false) => // do nothing, since already selected
      case (dragging: AnyNodes, SelectedNodesBar, false, false) => state.selectedNodeIds.update(_ ++ dragging.nodeIds)

      case (dragging: Channel, target: Channel, false, false) => moveChannel(dragging.nodeId, target.nodeId)
      case (dragging: AnyNodes, target: Channel, false, false) => moveInto(dragging.nodeIds, target.nodeId :: Nil)
      case (dragging: Channel, target: SingleNode, false, false) => addTag(dragging.nodeId, target.nodeId)

      case (dragging: ChildNode, target: ParentNode, false, false) => moveInto(dragging.nodeId, target.nodeId)
      case (dragging: ChildNode, target: MultiParentNodes, false, false) => moveInto(dragging.nodeId, target.nodeIds)
      case (dragging: ChildNode, target: ChildNode, false, false) => moveInto(dragging.nodeId, target.nodeId)
      case (dragging: ParentNode, target: SingleNode, false, false) => addTag(target.nodeId, dragging.nodeId)

      case (dragging: AnyNodes, target: AnyNodes, true, _) => addTag(dragging.nodeIds, target.nodeIds)
      case (dragging: AnyNodes, target: AnyNodes, false, _) => moveInto(dragging.nodeIds, target.nodeIds)
    }
  }

  dragOverEvent.map(_.over).map { over =>
    val target = readDragTarget(over)
    target.foreach{target => scribe.info(s"Dragging over: $target")}
    target
  }.subscribe(lastDragTarget)

  dragOutEvent.map(_ => None).subscribe(lastDragTarget)

  val ctrlDown = keyDown(KeyCode.Ctrl)
//  ctrlDown.foreach(down => println(s"ctrl down: $down"))

  //TODO: keyup-event for Shift does not work in chrome. It reports Capslock.
  val shiftDown = Observable(false)
//  val shiftDown = keyDown(KeyCode.Shift)
//  shiftDown.foreach(down => println(s"shift down: $down"))

  dragStopEvent.map { e =>
    readDragPayload(e.originalSource)
  }.withLatestFrom3(lastDragTarget, ctrlDown, shiftDown)((p,t,ctrl,shift) => (p,t, ctrl, shift)).foreachTry { pt =>
    pt match {
      case (Some(payload), Some(target), ctrl, shift) =>
        println(s"Dropped: $payload -> $target${ctrl.ifTrue(" +ctrl")}${shift.ifTrue(" +shift")}")
        dragActions.andThen{ _ =>
          Analytics.sendEvent("drag", "dropped", s"${payload.productPrefix}-${target.productPrefix} ${ctrl.ifTrue(" +ctrl")}${shift.ifTrue(" +shift")}")
        }.applyOrElse((payload, target, ctrl, shift), { case (payload:DragPayload, target:DragTarget, ctrl:Boolean, shift:Boolean) =>
          Analytics.sendEvent("drag", "nothandled", s"${payload.productPrefix}-${target.productPrefix} ${ctrl.ifTrue(" +ctrl")}${shift.ifTrue(" +shift")}")
          println(s"drag combination not handled.")
        }:PartialFunction[(DragPayload, DragTarget, Boolean, Boolean),Unit]
        )
      case other => println(s"incomplete drag action: $other")
    }
    lastDragTarget.onNext(None)
  }
}
