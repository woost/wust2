package wust.webApp.dragdrop

import draggable._
import monix.reactive.Observable
import googleAnalytics.Analytics
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import wust.util._
import org.scalajs.dom.raw.HTMLElement
import wust.graph.{Edge, GraphChanges, Tree, _}
import wust.ids.{EdgeData, NodeId, NodeRole, UserId}
import wust.webApp.{BrowserDetect, DevOnly}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._

import scala.collection.breakOut
import scala.scalajs.js


class SortableEvents(state: GlobalState, draggable: Draggable) {

  import TaskOrdering.Position

  private val dragStartEvent = PublishSubject[DragStartEvent]
  private val dragOverEvent = PublishSubject[DragOverEvent]
  private val dragOutEvent = PublishSubject[DragOutEvent]
  private val lastDragTarget = PublishSubject[Option[DragTarget]] //TODO: observable derived from other subjects
  private val sortableStartEvent = PublishSubject[SortableStartEvent]
  private val sortableStopEvent = PublishSubject[SortableStopEvent]
  private val sortableSortEvent = PublishSubject[SortableSortEvent]

  val filteredDragStartEvent = dragStartEvent.filter { e =>
    // copy dragpayload reference from source to mirror // https://github.com/Shopify/draggable/issues/245
    val payload:Option[DragPayload] = readDragPayload(e.originalSource)
    payload.foreach ( writeDragPayload(e.mirror, _) )

    payload match {
      case Some(DragItem.DisableDrag) => e.cancel(); false
      case _ => true
    }
  }

  draggable.on[SortableStartEvent]("sortable:start", sortableStartEvent.onNext _)
  draggable.on[SortableSortEvent]("sortable:sort", sortableSortEvent.onNext _)
  draggable.on[SortableStopEvent]("sortable:stop", (e: SortableStopEvent) => {
    sortableStopEvent.onNext(e)
    scribe.debug(s"moved from position ${e.oldIndex} to new position ${e.newIndex}")
  })

  draggable.on[DragStartEvent]("drag:start", dragStartEvent.onNext _)
  draggable.on[DragOverEvent]("drag:over", dragOverEvent.onNext _)
  draggable.on[DragOutEvent]("drag:out", dragOutEvent.onNext _)

  def parseDomPositions(e: SortableStopEvent): Option[(Position, Position)] = {

    // Get index of moved node in previous container
    val origElem = e.dragEvent.originalSource

    // Get index of moved node in new container
    val sourceElem = e.dragEvent.source

    // workaround: use classList to explicitly filter elements (dragEvent.mirror does not work reliable)
    val previousContChildren: js.Array[HTMLElement] = e.oldContainer.children.asInstanceOf[js.Array[HTMLElement]].filterNot(f => f == e.dragEvent.source || f == e.dragEvent.mirror || f.classList.contains("draggable-mirror") )
    val newContChildren: js.Array[HTMLElement] = e.newContainer.children.asInstanceOf[js.Array[HTMLElement]].filterNot(f => f == e.dragEvent.originalSource || f == e.dragEvent.mirror || f.classList.contains("draggable-mirror") )

    val prevPos = previousContChildren.indexOf(origElem)
    val nextPos = newContChildren.indexOf(sourceElem)

    if(prevPos != -1 && nextPos != -1) Some((prevPos, nextPos))
    else None
  }

  @inline def checkContainerChanged(from: DragContainer, into: DragContainer) = from != into
  @inline def checkPositionChanged(previousPosition: Position, newPosition: Position) = previousPosition != newPosition

  // Hints:
  // - Most outer container contains unclassified nodes as well
  // - Only one "big" sortable => container always the same (oldContainer == newContainer)
  // - A container corresponds to a parent node
  // - The index in a container correspond to the index in the topological sorted node list of the corresponding parent node
  def sortingChanges(graph: Graph, userId: UserId, e: SortableStopEvent, sortNode: DragItem.Kanban.Item, from: DragContainer, into: DragContainer): GraphChanges = {

    import DragContainer._
    scribe.info("Computing sorting change")
    //TODO: Is a SortEvent triggered when a new card is created?
    parseDomPositions(e) match {
      case Some((previousDomPosition, newDomPosition)) =>
        val containerChanged = checkContainerChanged(from, into)

        val gc = if(!containerChanged && !checkPositionChanged(previousDomPosition, newDomPosition))
                   TaskOrdering.abortSorting("item dropped on same place (no movement)")
                 else
                   TaskOrdering.constructGraphChangesByContainer(graph, userId, sortNode.nodeId, containerChanged, previousDomPosition, newDomPosition, from.parentId, into.parentId, from.items, into.items)


//                 else if(from.isInstanceOf[Kanban.Inbox] && into.isInstanceOf[Kanban.Inbox])
//                   TaskOrdering.constructGraphChangesByContainer(graph, userId, sortNode.nodeId, containerChanged, previousDomPosition, newDomPosition, from.parentId, into.parentId, from.asInstanceOf[Kanban.Inbox].items, into.asInstanceOf[Kanban.Inbox].items)
//                 else
//                   TaskOrdering.constructGraphChangesByOrdering(graph, userId, sortNode.nodeId, containerChanged, previousDomPosition, newDomPosition, from.parentId, into.parentId)

        scribe.info("Calculated new sorting graph change!")
        GraphChanges.log(gc)
        gc
      case _ =>
        TaskOrdering.abortSorting("Could not determine position of elements")
    }
  }

  private def submit(changes:GraphChanges) = {
    state.eventProcessor.changes.onNext(changes)
  }
  private def addTag(nodeId:NodeId, tagId:NodeId):Unit = addTag(nodeId :: Nil, tagId)
  private def addTag(nodeIds:Iterable[NodeId], tagId:NodeId):Unit = addTag(nodeIds, tagId :: Nil)
  private def addTag(nodeIds:Iterable[NodeId], tagIds:Iterable[NodeId]):Unit = {
    val changes = nodeIds.foldLeft(GraphChanges.empty) {(currentChange, nodeId) =>
      val graph = state.graph.now
      val subjectIdx = graph.idToIdx(nodeId)
      val deletedAt = if(subjectIdx == -1) None else graph.combinedDeletedAt(subjectIdx)
      currentChange merge GraphChanges.connect((s,d,t) => new Edge.Parent(s,d,t))(nodeIds, EdgeData.Parent(deletedAt, None), tagIds)
    }
    submit(changes)
  }

  private def moveInto(nodeId:NodeId, newParentId:NodeId):Unit = moveInto(nodeId :: Nil, newParentId :: Nil)
  private def moveInto(nodeId:NodeId, newParentIds:Iterable[NodeId]):Unit = moveInto(nodeId :: Nil, newParentIds)
  private def moveInto(nodeIds:Iterable[NodeId], newParentIds:Iterable[NodeId]):Unit = {
    submit(GraphChanges.moveInto(state.graph.now, nodeIds, newParentIds))
  }
  private def moveChannel(channelId:NodeId, targetChannelId:Option[NodeId]):Unit = {

    def filterParents(nodeId:NodeId, tree:Tree):Set[NodeId] = {
      tree match {
        case _:Tree.Leaf => Set.empty
        case Tree.Parent(parentNode, children) =>
          val parent = if( children.exists(_.node.id == nodeId) ) Set(parentNode.id) else Set.empty
          parent ++ children.flatMap(child => filterParents(nodeId, child))
      }
    }

    val topologicalChannelParents = state.channelForest.now.flatMap(filterParents(channelId, _))
    val disconnect:GraphChanges = GraphChanges.disconnect(Edge.Parent)(channelId, topologicalChannelParents)
    val connect:GraphChanges = targetChannelId.fold(GraphChanges.empty){ (targetChannelId) => GraphChanges.connect(Edge.Parent)(channelId, targetChannelId)}
    submit(disconnect merge connect)

  }


  private def assign(userId: UserId, nodeId: NodeId) = {
    submit(
      GraphChanges.connect(Edge.Assigned)(userId, nodeId)
    )
  }

  val dragActions:PartialFunction[(DragPayload, DragTarget, Boolean, Boolean),Unit] = {
    // The booleans: Ctrl is dow, Shift is down
    import DragItem._
    {
      case (dragging: Chat.Message, target: Chat.Thread, false, false) => moveInto(dragging.nodeId, target.nodeIds)

      case (dragging: Kanban.Card, target: SingleNode, false, false) => moveInto(dragging.nodeId, target.nodeId)
      case (dragging: Kanban.Column, target: SingleNode, false, false) => moveInto(dragging.nodeId, target.nodeId)

      case (dragging: SelectedNode, target: SingleNode, false, false) => addTag(dragging.nodeIds, target.nodeId)
      case (dragging: SelectedNodes, target: SingleNode, false, false) => addTag(dragging.nodeIds, target.nodeId)

      case (dragging: Channel, target: Channel, false, false) => moveChannel(dragging.nodeId, Some(target.nodeId))
      case (dragging: AnyNodes, target: Channel, false, false) => moveInto(dragging.nodeIds, target.nodeId :: Nil)
      case (dragging: Channel, target: SingleNode, false, false) => addTag(dragging.nodeId, target.nodeId)
      case (dragging: Channel, target: Sidebar.type, false, false) => moveChannel(dragging.nodeId, None)

      case (dragging: ChildNode, target: ParentNode, false, false) => moveInto(dragging.nodeId, target.nodeId)
      case (dragging: ChildNode, target: MultiParentNodes, false, false) => moveInto(dragging.nodeId, target.nodeIds)
      case (dragging: ChildNode, target: ChildNode, false, false) => moveInto(dragging.nodeId, target.nodeId)
      case (dragging: ParentNode, target: SingleNode, false, false) => addTag(target.nodeId, dragging.nodeId)

      case (dragging: AvatarNode, target: DragContainer.AvatarHolder, _, _) => assign(dragging.userId, target.nodeId)
      case (dragging: AvatarNode, target: Kanban.Card, _, _) => assign(dragging.userId, target.nodeId)

      case (dragging: AnyNodes, target: AnyNodes, true, _) => addTag(dragging.nodeIds, target.nodeIds)
      case (dragging: AnyNodes, target: AnyNodes, false, _) => moveInto(dragging.nodeIds, target.nodeIds)

    }
  }

  dragOverEvent.map(_.over).map { over =>
    val target = readDragTarget(over)
    DevOnly {
      target.foreach{target => scribe.info(s"Dragging over: $target")}
    }
    target
  }.subscribe(lastDragTarget)

  dragOutEvent.map(_ => None).subscribe(lastDragTarget)

  // This partial function describes what happens, but also what is allowed to drag from where to where
  val
  sortableActions: PartialFunction[(SortableStopEvent, DragPayload, DragContainer, DragContainer, Boolean, Boolean),Unit] = {
    import DragContainer._
    def graph = state.graph.now
    def userId = state.user.now.id

    {
      case (e, dragging: DragItem.Kanban.Column, from: Kanban.AreaForColumns, into: Kanban.AreaForColumns, false, false) =>
        //        val move = GraphChanges.changeTarget[NodeId, NodeId, Edge.Parent](Edge.Parent)(Some(dragging.nodeId), Some(from.parentId), Some(into.parentId))
        val sortChanges = sortingChanges(graph, userId, e, dragging, from, into)
        val unstageChanges: GraphChanges = if(from.parentId != into.parentId) GraphChanges.disconnect(Edge.Parent)(dragging.nodeId, from.parentId) else GraphChanges.empty
        val fullChange = unstageChanges merge sortChanges

        state.eventProcessor.changes.onNext(fullChange)

      case (e, dragging: DragItem.Kanban.Card, from: Kanban.AreaForCards, into: Kanban.Column, false, false) =>
        //        val move = GraphChanges.changeTarget(Edge.Parent)(Some(dragging.nodeId), stageParents, Some(into.parentId))
        val sortChanges = sortingChanges(graph, userId, e, dragging, from, into)
        val stageParents = graph.getRoleParents(dragging.nodeId, NodeRole.Stage).filterNot(_ == into.parentId)
        val unstageChanges: GraphChanges = GraphChanges.disconnect(Edge.Parent)(dragging.nodeId, stageParents)
        val fullChange = unstageChanges merge sortChanges

        state.eventProcessor.changes.onNext(fullChange)

      case (e, dragging: DragItem.Kanban.Card, from: Kanban.Column, into: Kanban.Inbox, false, false) =>
        // disconnect from all stage parents
        val sortChanges = sortingChanges(graph, userId, e, dragging, from, into)
        val stageParents = graph.getRoleParents(dragging.nodeId, NodeRole.Stage).filterNot(_ == into.parentId)
        val unstageChanges: GraphChanges = GraphChanges.disconnect(Edge.Parent)(dragging.nodeId, stageParents)
        val fullChange = unstageChanges merge sortChanges

        state.eventProcessor.changes.onNext(fullChange)

      case (e, dragging: DragItem.Kanban.Card, from: Kanban.Inbox, into: Kanban.Inbox, false, false) => // needed to allow dragging from inbox into column, not dropping and then dropping it back in inbox or sorting within Inbox
        val sortChanges = sortingChanges(graph, userId, e, dragging, from, into)
        state.eventProcessor.changes.onNext(sortChanges)
    }
  }

//  val ctrlDown = keyDown(KeyCode.Ctrl)
  val ctrlDown = if(BrowserDetect.isMobile) Observable.now(false) else keyDown(KeyCode.Ctrl)
  //  ctrlDown.foreach(down => println(s"ctrl down: $down"))

  //TODO: keyup-event for Shift does not work in chrome. It reports Capslock.
  val shiftDown = Observable.now(false)
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

    val disableSort = readDragDisableSort(e.dragEvent.originalSource).getOrElse(false)
    // dom.console.log(e.dragEvent)

    if(disableSort) {
      // println("sort is disabled")
      e.cancel()
    } else {
      // println("sorting....")
      val overContainerWorkaround = e.dragEvent.asInstanceOf[js.Dynamic].overContainer.asInstanceOf[dom.html.Element] // https://github.com/Shopify/draggable/issues/256
      val sourceContainerWorkaround = e.dragEvent.asInstanceOf[js.Dynamic].sourceContainer.asInstanceOf[dom.html.Element] // TODO: report as feature request
      val draggingOpt = readDragPayload(e.dragEvent.source)
      val overContainerOpt = readDragContainer(overContainerWorkaround)
      val sourceContainerOpt = readDragContainer(sourceContainerWorkaround) // will be written by registerSortableContainer

      // println(s"$draggingOpt, $overContainerOpt, $sourceContainerOpt")
      // white listing allowed sortable actions
      (draggingOpt, sourceContainerOpt, overContainerOpt) match {
        case (Some(dragging), Some(sourceContainer), Some(overContainer)) if sortableActions.isDefinedAt((null, dragging, sourceContainer, overContainer, ctrl, shift)) => // allowed
        case (Some(dragging), Some(sourceContainer), Some(overContainer)) => println(s"not allowed: $sourceContainer -> $dragging -> $overContainer"); e.cancel()
        case _ =>
          println(s"not allowed: $sourceContainerOpt -> $draggingOpt -> $overContainerOpt")
          e.cancel()
      }
    }
  }


  sortableStopEvent.withLatestFrom3(lastDragTarget, ctrlDown, shiftDown)((e, target, ctrl, shift) => (e, target, ctrl, shift)).foreachTry { case (e, target, ctrl, shift) =>

    val disableSort = readDragDisableSort(e.dragEvent.originalSource).getOrElse(false)

    if(disableSort) {
      (readDraggableDraggedAction(e.dragEvent.originalSource), readDragPayload(e.dragEvent.originalSource), target, ctrl, shift) match {
        case (afterDraggedAction, Some(payload), Some(target), ctrl, shift) =>
          scribe.debug(s"Dropped: $payload -> $target${ctrl.ifTrue(" +ctrl")}${shift.ifTrue(" +shift")}")
          dragActions.andThen{ _ =>
            Analytics.sendEvent("drag", "dropped", s"${payload.productPrefix}-${target.productPrefix} ${ctrl.ifTrue(" +ctrl")}${shift.ifTrue(" +shift")}")
          }.applyOrElse((payload, target, ctrl, shift), { case (payload:DragPayload, target:DragTarget, ctrl:Boolean, shift:Boolean) =>
            Analytics.sendEvent("drag", "nothandled", s"${payload.productPrefix}-${target.productPrefix} ${ctrl.ifTrue(" +ctrl")}${shift.ifTrue(" +shift")}")
            scribe.debug(s"drag combination not handled.")
          }:PartialFunction[(DragPayload, DragTarget, Boolean, Boolean),Unit]
          )
          afterDraggedAction.foreach(_.apply())
        case other                                                          => scribe.debug(s"incomplete drag action: $other")
      }
    } else {
      val draggingOpt = readDragPayload(e.dragEvent.source)
      val oldContainerOpt = readDragContainer(e.oldContainer)
      val newContainerOpt = if(e.newContainer != e.oldContainer) readDragContainer(e.newContainer) else oldContainerOpt

      (draggingOpt, oldContainerOpt, newContainerOpt) match {
        case (Some(dragging), Some(oldContainer), Some(newContainer)) =>
          sortableActions.applyOrElse((e, dragging, oldContainer, newContainer, ctrl, shift), (other: (SortableEvent, DragPayload, DragContainer, DragContainer, Boolean, Boolean)) => scribe.warn(s"sort combination not handled."))
        case other                                                    => scribe.warn(s"incomplete drag action: $other")
      }
    }
  }

}
