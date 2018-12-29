package wust.webApp.dragdrop

import draggable._
import monix.reactive.Observable
import googleAnalytics.Analytics
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import org.scalajs.dom.console
import org.scalajs.dom.ext.KeyCode
import wust.util._
import org.scalajs.dom.raw.HTMLElement
import wust.api.AuthUser
import wust.graph.{Edge, GraphChanges, Tree, _}
import wust.ids.{EdgeData, NodeId, NodeRole, UserId}
import wust.webApp.{BrowserDetect, DevOnly}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._

import scala.collection.breakOut
import scala.scalajs.js
import scala.scalajs.js.|


class SortableEvents(state: GlobalState, draggable: Draggable) {

  import TaskOrdering.Position

//  private val dragStartEvent = PublishSubject[DragStartEvent]
  private val dragOverEvent = PublishSubject[DragOverEvent]
  private val dragOverContainerEvent = PublishSubject[DragOverContainerEvent]
  private val dragOutEvent = PublishSubject[DragOutEvent]
  private val dragOutContainerEvent = PublishSubject[DragOutContainerEvent]
  private val sortableStartEvent = PublishSubject[SortableStartEvent]
  private val sortableStopEvent = PublishSubject[SortableStopEvent]
  private val sortableSortEvent = PublishSubject[SortableSortEvent]

  private val currentOverEvent = PublishSubject[js.UndefOr[DragOverEvent]] //TODO: observable derived from other subjects
  private val currentOverContainerEvent = PublishSubject[js.UndefOr[DragOverContainerEvent]] //TODO: observable derived from other subjects

  draggable.on[SortableStartEvent]("sortable:start", sortableStartEvent.onNext _)
  draggable.on[SortableSortEvent]("sortable:sort", (e: SortableSortEvent) => {
    sortableSortEvent.onNext(e)
   // DevOnly(console.log(e))
  })
  draggable.on[SortableStopEvent]("sortable:stop", (e: SortableStopEvent) => {
    sortableStopEvent.onNext(e)
    scribe.debug(s"moved from position ${ e.oldIndex } to new position ${ e.newIndex }")
  })

  draggable.on[DragStartEvent]("drag:start", (e:DragStartEvent) => {
//    dragStartEvent.onNext(e)
    DevOnly {
      val payload = readDragPayload(e.originalSource)
      scribe.info(s"\ndrag start: $payload")
    }
  })
  draggable.on[DragOverEvent]("drag:over", (e:DragOverEvent) => {
    dragOverEvent.onNext(e)
    // DevOnly(console.log(e))
  })
  draggable.on[DragOverContainerEvent]("drag:over:container", (e:DragOverContainerEvent) => {
    dragOverContainerEvent.onNext(e)
    // DevOnly(console.log(e))
  })
  draggable.on[DragOutEvent]("drag:out", dragOutEvent.onNext _)


  dragOverEvent.map { e =>
    DevOnly {
      readDragTarget(e.over).foreach { target => scribe.info(s"Dragging over: $target") }
    }
    js.defined(e)
  }.subscribe(currentOverEvent)

  dragOutEvent.map(_ => js.undefined).subscribe(currentOverEvent)


  dragOverContainerEvent.map { e =>
    DevOnly {
      readDragContainer(e.overContainer).foreach { container => scribe.info(s"Dragging over container: $container") }
    }
    js.defined(e)
  }.subscribe(currentOverContainerEvent)

  dragOutContainerEvent.map(_ => js.undefined).subscribe(currentOverContainerEvent)


  sortableStartEvent.foreachTry { e =>
    // copy dragpayload reference from source to mirror // https://github.com/Shopify/draggable/issues/245
    val payload: js.UndefOr[DragPayload] = readDragPayload(e.dragEvent.originalSource)
    payload.foreach(writeDragPayload(e.dragEvent.source, _))

    if(payload == js.defined(DragItem.DisableDrag)) { scribe.info("Drag is disabled on this element."); e.cancel() }
  }


  def parseDomPositions(e: SortableStopEvent): Option[(Position, Position)] = {

    // Get index of moved node in previous container
    val origElem = e.dragEvent.originalSource

    // Get index of moved node in new container
    val sourceElem = e.dragEvent.source

    // workaround: use classList to explicitly filter elements (dragEvent.mirror does not work reliable)
    val previousContChildren: js.Array[HTMLElement] = e.oldContainer.children.asInstanceOf[js.Array[HTMLElement]].filterNot(f => f == e.dragEvent.source || f == e.dragEvent.mirror || f.classList.contains("draggable-mirror"))
    val newContChildren: js.Array[HTMLElement] = e.newContainer.children.asInstanceOf[js.Array[HTMLElement]].filterNot(f => f == e.dragEvent.originalSource || f == e.dragEvent.mirror || f.classList.contains("draggable-mirror"))

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
  def sortingChanges(graph: Graph, userId: UserId, e: SortableStopEvent, sortNode: NodeId, from: SortableContainer, into: SortableContainer): GraphChanges = {

    import DragContainer._
    scribe.debug("Computing sorting change")
    //TODO: Is a SortEvent triggered when a new card is created?
    parseDomPositions(e) match {
      case Some((previousDomPosition, newDomPosition)) =>
        val containerChanged = checkContainerChanged(from, into)

        val gc = if(!containerChanged && !checkPositionChanged(previousDomPosition, newDomPosition)) { scribe.debug("item dropped on same place (no movement)"); GraphChanges.empty }
                 else
                   TaskOrdering.constructGraphChangesByContainer(graph, userId, sortNode, containerChanged, previousDomPosition, newDomPosition, from.parentId, into.parentId, from.items, into.items)


        //                 else if(from.isInstanceOf[Kanban.Inbox] && into.isInstanceOf[Kanban.Inbox])
        //                   TaskOrdering.constructGraphChangesByContainer(graph, userId, sortNode, containerChanged, previousDomPosition, newDomPosition, from.parentId, into.parentId, from.asInstanceOf[Kanban.Inbox].items, into.asInstanceOf[Kanban.Inbox].items)
        //                 else
        //                   TaskOrdering.constructGraphChangesByOrdering(graph, userId, sortNode, containerChanged, previousDomPosition, newDomPosition, from.parentId, into.parentId)

        scribe.debug("Calculated new sorting graph change!")
        scribe.debug(gc.toPrettyString(graph))
        gc
      case _                                           =>
        TaskOrdering.abortSorting("Could not determine position of elements")
    }
  }

  @inline private def linkInto(nodeId: NodeId, tagId: NodeId, graph:Graph): GraphChanges = linkInto(nodeId :: Nil, tagId, graph)
  @inline private def linkInto(nodeId: NodeId, tagIds: Iterable[NodeId], graph:Graph): GraphChanges = linkInto(nodeId :: Nil, tagIds, graph)
  @inline private def linkInto(nodeIds: Iterable[NodeId], tagId: NodeId, graph:Graph): GraphChanges = linkInto(nodeIds, tagId :: Nil, graph)
  private def linkInto(nodeIds: Iterable[NodeId], tagIds: Iterable[NodeId], graph:Graph): GraphChanges = {
    // tags will be added with the same (latest) deletedAt date, which the node already has for other parents
    nodeIds.foldLeft(GraphChanges.empty) { (currentChange, nodeId) =>
      val subjectIdx = graph.idToIdx(nodeId)
      val deletedAt = if(subjectIdx == -1) None else graph.latestDeletedAt(subjectIdx)
      currentChange merge GraphChanges.connect((s, d, t) => new Edge.Parent(s, d, t))(nodeIds, EdgeData.Parent(deletedAt, None), tagIds)
    }
  }

  @inline private def moveInto(nodeId: NodeId, newParentId: NodeId, graph:Graph): GraphChanges = moveInto(nodeId :: Nil, newParentId :: Nil, graph)
  @inline private def moveInto(nodeId: Iterable[NodeId], newParentId: NodeId, graph:Graph): GraphChanges = moveInto(nodeId, newParentId :: Nil, graph)
  @inline private def moveInto(nodeId: NodeId, newParentIds: Iterable[NodeId], graph:Graph): GraphChanges = moveInto(nodeId :: Nil, newParentIds, graph)
  @inline private def moveInto(nodeIds: Iterable[NodeId], newParentIds: Iterable[NodeId], graph:Graph): GraphChanges = {
    GraphChanges.moveInto(graph, nodeIds, newParentIds)
  }
  private def movePinnedChannel(channelId: NodeId, targetChannelId: Option[NodeId], graph: Graph, userId: UserId): GraphChanges = {
    val channelIdx = graph.idToIdx(channelId)
    val directParentsInChannelTree = graph.notDeletedParentsIdx(channelIdx).collect {
      case parentIdx if graph.anyAncestorIsPinned(graph.nodeIds(parentIdx) :: Nil, userId) => graph.nodeIds(parentIdx)
    }

    val disconnect: GraphChanges = GraphChanges.disconnect(Edge.Parent)(channelId, directParentsInChannelTree)
    val connect: GraphChanges = targetChannelId.fold(GraphChanges.empty) {
      targetChannelId => GraphChanges.connect(Edge.Parent)(channelId, targetChannelId)
    }
    disconnect merge connect
  }

  @inline private def linkOrMoveInto(nodeId: NodeId, newParentId: NodeId, graph:Graph, link:Boolean): GraphChanges = linkOrMoveInto(nodeId :: Nil, newParentId :: Nil, graph, link)
  @inline private def linkOrMoveInto(nodeId: Iterable[NodeId], newParentId: NodeId, graph:Graph, link:Boolean): GraphChanges = linkOrMoveInto(nodeId, newParentId :: Nil, graph, link)
  @inline private def linkOrMoveInto(nodeId: NodeId, newParentIds: Iterable[NodeId], graph:Graph, link:Boolean): GraphChanges = linkOrMoveInto(nodeId :: Nil, newParentIds, graph, link)
  @inline private def linkOrMoveInto(nodeId: Iterable[NodeId], newParentId: Iterable[NodeId], graph:Graph, link:Boolean) = {
    if(link) linkInto(nodeId, newParentId, graph)
    else moveInto(nodeId, newParentId, graph)
  }


  private def assign(userId: UserId, nodeId: NodeId) = {
    GraphChanges.connect(Edge.Assigned)(userId, nodeId)
  }

  //  val ctrlDown = keyDown(KeyCode.Ctrl)
  val ctrlDown = if(BrowserDetect.isMobile) Observable.now(false) else keyDown(KeyCode.Ctrl)
  //  ctrlDown.foreach(down => println(s"ctrl down: $down"))

  //TODO: keyup-event for Shift does not work in chrome. It reports Capslock.
  val shiftDown = Observable.now(false)
  //  val shiftDown = keyDown(KeyCode.Shift)
  //  shiftDown.foreach(down => println(s"shift down: $down"))


  sortableSortEvent.withLatestFrom3(currentOverContainerEvent, ctrlDown, shiftDown)((sortableSortEvent, currentOverContainerEvent, ctrl, shift) => (sortableSortEvent, currentOverContainerEvent, ctrl, shift)).foreachTry {
    case (sortableSortEvent, currentOverContainerEvent, ctrl, shift) if currentOverContainerEvent.isDefined =>
      val overSortcontainer = readDragContainer(sortableSortEvent.dragEvent.overContainer).exists(_.isInstanceOf[SortableContainer])

      if(overSortcontainer) {
        scribe.info("over sortcontainer, validating sort information...")
        validateSortInformation(sortableSortEvent, currentOverContainerEvent.get, ctrl, shift)
      } else {
        // drag action is handled by dragOverEvent instead
        sortableSortEvent.cancel()
      }
    case (sortableSortEvent, _, _, _) => sortableSortEvent.cancel()
  }

  dragOverEvent.withLatestFrom2(ctrlDown, shiftDown)((e, ctrl, shift) => (e, ctrl, shift)).foreachTry {
    case (e, ctrl, shift) =>
      val notOverSortContainer = !readDragContainer(e.overContainer).exists(_.isInstanceOf[SortableContainer])

      if(notOverSortContainer) {
        scribe.info("not over sort container, validating drag information...")
        validateDragInformation(e, ctrl, shift)
      } else {
        // drag action is handled by sortableSortEvent instead
        e.cancel()
      }
  }

  // when dropping
  sortableStopEvent.withLatestFrom4(currentOverContainerEvent, currentOverEvent, ctrlDown, shiftDown)((e, currentOverContainerEvent, currentOverEvent, ctrl, shift) => (e, currentOverContainerEvent, currentOverEvent, ctrl, shift)).foreachTry {
    case (e, currentOverContainerEvent, currentOverEvent, ctrl, shift) if currentOverContainerEvent.isDefined =>
      val overSortcontainer = currentOverContainerEvent.flatMap(e => readDragContainer(e.overContainer)).exists(_.isInstanceOf[SortableContainer])

      if(overSortcontainer) {
        performSort(e, currentOverContainerEvent.get, currentOverEvent.get, ctrl, shift)
      } else {
        performDrag(e, currentOverEvent.get, ctrl, shift)
      }
    case _ =>
      scribe.info("dropped outside container or target")
  }

  // This partial function describes what happens, but also what is allowed to sort from where to where
  // Beware: Other functions rely on its partiality (isDefinedAt), therefore do not make it a full function
  // The booleans: Ctrl is down, Shift is down
  type SortAction = PartialFunction[
    (DragContainer, DragPayload, DragContainer, Boolean, Boolean),
    (SortableStopEvent,Graph,UserId) => GraphChanges
    ]
  val sortAction: SortAction = {
    // First, Sort actions:
    import DragContainer._
    {
      //// Kanban View ////
      case (from: Kanban.AreaForColumns, payload: DragItem.Stage, into: Kanban.AreaForColumns, false, false) =>
        (sortableStopEvent,graph,userId) =>
          //        val move = GraphChanges.changeTarget[NodeId, NodeId, Edge.Parent](Edge.Parent)(Some(dragging.nodeId), Some(from.parentId), Some(into.parentId))
          val sortChanges = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          val unstageChanges: GraphChanges = if(from.parentId != into.parentId) GraphChanges.disconnect(Edge.Parent)(payload.nodeId, from.parentId) else GraphChanges.empty
          unstageChanges merge sortChanges


      case (from: Kanban.Column, payload: DragItem.Task, into: Kanban.Column, false, false) =>
        (sortableStopEvent,graph,userId) =>
          val sortChanges = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          val unstageChanges: GraphChanges = if(from.parentId != into.parentId) GraphChanges.disconnect(Edge.Parent)(payload.nodeId, from.parentId) else GraphChanges.empty
          unstageChanges merge sortChanges


      case (from: Kanban.Card, payload: DragItem.Task, into: Kanban.Column, false, false) =>
        (sortableStopEvent,graph,userId) =>
          // the card changes its workspace from from:Card to into:Kanban.Column.workspace
          //        val move = GraphChanges.changeTarget(Edge.Parent)(Some(dragging.nodeId), stageParents, Some(into.parentId))
          val sortChanges = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          val changeWorkspace: GraphChanges = GraphChanges.changeTarget(Edge.Parent)(Some(payload.nodeId), from.parentId :: Nil, Some(into.workspace))
          // TODO: adding stageParents to fullChange results in a graphchange where the same parentedge
          // is introduced by sortChanges, but with an ordering. Graphchanges does NOT squash the edges. This is a bug in GraphChanges.
          // val stageParents: GraphChanges = GraphChanges.connect(Edge.Parent)(dragging.nodeId, into.parentId)
          sortChanges merge changeWorkspace //merge stageParents

      case (from: Kanban.Inbox, payload: DragItem.Task, into: Kanban.Column, false, false) =>
        (sortableStopEvent,graph,userId) =>
          //        val move = GraphChanges.changeTarget(Edge.Parent)(Some(payload.nodeId), stageParents, Some(into.parentId))
          val sortChanges = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          val stageParents = graph.getRoleParents(payload.nodeId, NodeRole.Stage).filterNot(_ == into.parentId)
          sortChanges

      case (from: Kanban.Column, payload: DragItem.Task, into: Kanban.Workspace, false, false) =>
        (sortableStopEvent,graph,userId) =>
          // disconnect from all stage parents
          val sortChanges = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          val stageParents = graph.getRoleParents(payload.nodeId, NodeRole.Stage)
          val unstageChanges: GraphChanges = GraphChanges.disconnect(Edge.Parent)(payload.nodeId, stageParents)
          val changeWorkspace: GraphChanges = if(from.workspace != into.parentId) GraphChanges.disconnect(Edge.Parent)(payload.nodeId, from.workspace :: Nil) else GraphChanges.empty
          unstageChanges merge sortChanges merge changeWorkspace

      case (from: Kanban.Workspace, payload: DragItem.Task, into: Kanban.Workspace, false, false) =>
        (sortableStopEvent,graph,userId) =>
          // disconnect from all stage parents
          val sortChanges = sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)
          val oldParents = graph.parents(payload.nodeId).filterNot(_ == into.parentId)
          val unstageChanges: GraphChanges = GraphChanges.disconnect(Edge.Parent)(payload.nodeId, oldParents)
          unstageChanges merge sortChanges


      //// List View ////
      case (from: List, payload: DragItem.Task, into: List, false, false) =>
        (sortableStopEvent,graph,userId) =>
          sortingChanges(graph, userId, sortableStopEvent, payload.nodeId, from, into)

    }
  }
  
  // This partial function describes what happens, but also what is allowed to drag from where to where
  // Beware: Other functions rely on its partiality (isDefinedAt), therefore do not make it a full function
  // The booleans: Ctrl is down, Shift is down
  type DragAction = PartialFunction[
    (DragPayload, DragTarget, Boolean, Boolean),
    (SortableStopEvent,Graph,UserId) => GraphChanges
    ]
  val dragAction:DragAction = {
    // Drag actions are only dependent on payload and target. (independent of containers)
    import DragItem._
    {
      case (payload: ContentNode, target: ContentNode, ctrl, false)  => (sortableStopEvent,graph,userId) => linkOrMoveInto(payload.nodeId, target.nodeId, graph, ctrl)
      case (payload: ContentNode, target: Thread, ctrl, false) => (sortableStopEvent,graph,userId) => linkOrMoveInto(payload.nodeId, target.nodeIds, graph, ctrl)
      case (payload: ContentNode, target: Workspace, ctrl, false) => (sortableStopEvent,graph,userId) => linkOrMoveInto(payload.nodeId, target.nodeId, graph, ctrl)
      case (payload: ContentNode, target: Channel, ctrl, false) => (sortableStopEvent,graph,userId) => linkOrMoveInto(payload.nodeId, target.nodeId, graph, ctrl)

      case (payload: ContentNode, target: BreadCrumb, ctrl, false) => (sortableStopEvent,graph,userId) => linkOrMoveInto(payload.nodeId, target.nodeId, graph, ctrl)

      case (payload: SelectedNode, target: ContentNode, ctrl, false)  => (sortableStopEvent,graph,userId) => linkOrMoveInto(payload.nodeId, target.nodeId, graph, ctrl)
      case (payload: SelectedNodes, target: ContentNode, ctrl, false) => (sortableStopEvent,graph,userId) => linkOrMoveInto(payload.nodeIds, target.nodeId, graph, ctrl)
      case (payload: SelectedNodes, target: Workspace, ctrl, false) => (sortableStopEvent,graph,userId) => linkOrMoveInto(payload.nodeIds, target.nodeId, graph, ctrl)
      case (payload: SelectedNodes, target: Channel, ctrl, false) => (sortableStopEvent,graph,userId) => linkOrMoveInto(payload.nodeIds, target.nodeId, graph, ctrl)

      case (payload: Channel, target: Channel, false, false)      => (sortableStopEvent,graph,userId) => movePinnedChannel(payload.nodeId, Some(target.nodeId), graph, userId)
      case (payload: Channel, target: Sidebar.type, false, false) => (sortableStopEvent,graph,userId) => movePinnedChannel(payload.nodeId, None, graph, userId)

      case (payload: Tag, target: ContentNode, false, false)  => (sortableStopEvent,graph,userId) => linkInto(target.nodeId, payload.nodeId, graph)

      case (payload: User, target: Task, false, false)                => (sortableStopEvent,graph,userId) => assign(payload.userId, target.nodeId)
    }
  }

  def extractSortInformation(e:SortableEvent, lastDragOverContainerEvent: DragOverContainerEvent):(js.UndefOr[DragContainer], js.UndefOr[DragPayload], js.UndefOr[DragContainer]) = {
    val overContainerWorkaround = e.dragEvent.asInstanceOf[js.Dynamic].overContainer.asInstanceOf[js.UndefOr[dom.html.Element]] // https://github.com/Shopify/draggable/issues/256
    val overContainer = overContainerWorkaround.getOrElse(lastDragOverContainerEvent.overContainer)
    val sourceContainerWorkaround = e.dragEvent.asInstanceOf[js.Dynamic].sourceContainer.asInstanceOf[dom.html.Element] // TODO: report as feature request
    val payloadOpt = readDragPayload(e.dragEvent.source)
    //      val targetOpt = readDragTarget(e.dragEvent.over)
    // containers are written by registerDragContainer
    val targetContainerOpt = readDragContainer(overContainer)
    val sourceContainerOpt = readDragContainer(sourceContainerWorkaround)
    (sourceContainerOpt, payloadOpt, targetContainerOpt)
  }


  private def validateSortInformation(e: SortableSortEvent, lastDragOverContainerEvent: DragOverContainerEvent, ctrl: Boolean, shift: Boolean): Unit = {
    extractSortInformation(e, lastDragOverContainerEvent) match {
      case (sourceContainer, payload, overContainer) if sourceContainer.isDefined && payload.isDefined && overContainer.isDefined =>
        if(sortAction.isDefinedAt((sourceContainer.get, payload.get, overContainer.get, ctrl, shift))) {
          scribe.info(s"valid sort action: $payload: $sourceContainer -> $overContainer")
        } else {
          e.cancel()
          scribe.info(s"sort not allowed: $payload: $sourceContainer -> $overContainer (trying drag instead...)")
          validateDragInformation(e.dragEvent, ctrl, shift)
        }
      case (sourceContainer, payload, overContainer)                                                                              =>
        e.cancel()
        scribe.info(s"incomplete sort information: $payload: $sourceContainer -> $overContainer")
    }
  }

  private def validateDragInformation(e: DragOverEvent, ctrl: Boolean, shift: Boolean): Unit = {
    val targetOpt = readDragTarget(e.over)
    val payloadOpt = readDragPayload(e.originalSource)
    (payloadOpt, targetOpt) match {
      case (payload, target) if payload.isDefined && target.isDefined =>
        if(dragAction.isDefinedAt((payload.get, target.get, ctrl, shift))) {
          scribe.info(s"valid drag action: $payload -> $target)")
        } else {
          e.cancel()
          scribe.info(s"drag not allowed: $payload -> $target)")
        }
      case (payload, target)                                          =>
        e.cancel()
        scribe.info(s"incomplete drag information: $payload -> $target)")
    }
  }

  private def performSort(e: SortableStopEvent, currentOverContainerEvent:DragOverContainerEvent, currentOverEvent:DragOverEvent, ctrl: Boolean, shift: Boolean): Unit = {
    extractSortInformation(e, currentOverContainerEvent) match {
      case (sourceContainer, payload, overContainer) if sourceContainer.isDefined && payload.isDefined && overContainer.isDefined =>
        // target is null, since sort actions do not look at the target. The target moves away automatically.
        val successful = sortAction.runWith { calculateChange =>
          state.eventProcessor.changes.onNext(calculateChange(e, state.graph.now, state.user.now.id))
        }((sourceContainer.get, payload.get, overContainer.get, ctrl, shift))

        if(successful)
          scribe.info(s"sort action successful: $payload: $sourceContainer -> $overContainer")
        else {
          scribe.info(s"sort action not defined: $payload: $sourceContainer -> $overContainer (trying drag instead...)")
          performDrag(e,currentOverEvent,ctrl, shift)
        }
      case (sourceContainerOpt, payloadOpt, overContainerOpt)                                                                     =>
        scribe.info(s"incomplete sort information: $payloadOpt: $sourceContainerOpt -> $overContainerOpt")
    }
  }

  private def performDrag(e: SortableStopEvent, currentOverEvent:DragOverEvent, ctrl: Boolean, shift: Boolean): Unit = {
    scribe.info("performing drag...")
         val afterDraggedActionOpt = readDraggableDraggedAction(e.dragEvent.originalSource)
         val payloadOpt = readDragPayload(e.dragEvent.originalSource)
         val targetOpt = readDragTarget(currentOverEvent.over)
         (payloadOpt, targetOpt) match {
           case (payload, target) if payload.isDefined && target.isDefined =>
             val successful = dragAction.runWith { calculateChange =>
               state.eventProcessor.changes.onNext(calculateChange(e, state.graph.now, state.user.now.id))
             }((payload.get, target.get, ctrl, shift))

             if(successful) {
               scribe.info(s"drag action successful: $payload -> $target")
               afterDraggedActionOpt.foreach{action =>
                 scribe.info(s"performing afterDraggedAction...")
                 action.apply()
               }
             }
             else {
               scribe.info(s"drag action not defined: $payload -> $target")
               // TODO:              Analytics.sendEvent("drag", "nothandled", s"${ payload.productPrefix }-${ target.productPrefix } ${ ctrl.ifTrue(" +ctrl") }${ shift.ifTrue(" +shift") }")
             }
           case (payload, target) =>
             scribe.info(s"incomplete drag information: $payload -> $target)")
         }
  }
}
