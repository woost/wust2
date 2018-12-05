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
import wust.ids.{EdgeData, NodeId, UserId}
import wust.webApp.{BrowserDetect, DevOnly}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._

import scala.collection.breakOut
import scala.scalajs.js


class SortableEvents(state: GlobalState, draggable: Draggable) {
  type PositionIndex = Int

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
  draggable.on[SortableStopEvent]("sortable:stop", sortableStopEvent.onNext _)

  draggable.on[DragStartEvent]("drag:start", dragStartEvent.onNext _)
  draggable.on[DragOverEvent]("drag:over", dragOverEvent.onNext _)
  draggable.on[DragOutEvent]("drag:out", dragOutEvent.onNext _)

  //  draggable.on("sortable:start", (e:SortableEvent) => console.log("sortable:start", e))
//    draggable.on("sortable:sort", () => console.log("sortable:sort"))
  //  draggable.on("sortable:sorted", () => console.log("sortable:sorted"))
  //  draggable.on("sortable:stop", () => console.log("sortable:stop"))

  // TODO: Generalize and outsource to DataOrdering
  def beforeChanges(graph: Graph, userId: UserId, e: SortableStopEvent, sortNode: DragItem.Kanban.Item, from: DragContainer.Kanban.Area, into: DragContainer.Kanban.Area): GraphChanges = {
    // Hints:
    // - Most outer container contains unclassified nodes as well
    // - Only one "big" sortable => container always the same (oldContainer == newContainer)
    // - A container corresponds to a parent node
    // - The index in a container correspond to the index in the topological sorted node list of the corresponding parent node

    def getNodeStr(graph: Graph, indices: Seq[Int]) = {
      indices.map(idx => graph.nodes(idx).str)
    }

    state.page.now.parentId match {
      case Some(pageParentId) =>

        // Get index of moved node in previous container
        @inline def origElem = e.dragEvent.originalSource

        // workaround: use classList to explicitly filter elements (dragEvent.mirror does not work reliable)
        val oldContChilds = e.oldContainer.children.asInstanceOf[js.Array[HTMLElement]].filterNot(f => f == e.dragEvent.source || f == e.dragEvent.mirror || f.classList.contains("draggable-mirror") )
        val oldSortIndex = oldContChilds.indexOf(origElem)

        // Get index of moved node in new container
        @inline def sourceElem = e.dragEvent.source

        // workaround: use classList to explicitly filter elements (dragEvent.mirror does not work reliable)
        val newContChilds = e.newContainer.children.asInstanceOf[js.Array[HTMLElement]].filterNot(f => f == e.dragEvent.originalSource || f == e.dragEvent.mirror || f.classList.contains("draggable-mirror") )
        val newSortIndex = newContChilds.indexOf(sourceElem)

        //    val containerChanged = from.parentIds != into.parentIds
        val containerChanged = from != into
        val movedDownwards = oldSortIndex < newSortIndex

        // Kanban item dropped on the previous / same place
        if(!containerChanged && oldSortIndex == newSortIndex) {
          scribe.info("item dropped on same place (no movement)")
          return GraphChanges.empty
        }

        // Node id of sorted node
        val sortedNodeId = sortNode.nodeId

        scribe.info("SORTING PREBUILDS\n" +
          //      s"in graph: ${g.toPrettyString}\n\t" +
          s"dragged node: ${graph.nodesById(sortNode.nodeId).str}\n\t" +
          s"from index $oldSortIndex / ${oldContChilds.length - 1}\n\t" +
          s"to index $newSortIndex / ${newContChilds.length - 1}")

        val cycleDeletionGraphChanges = BeforeOrdering.breakCyclesGraphChanges(graph, graph.beforeIdx, into.parentId, BeforeOrdering.nodesOfInterest(graph, pageParentId, userId, into.parentId))
        val g = graph.applyChanges(cycleDeletionGraphChanges)
//        return GraphChanges.empty
        // Reconstruct ordering
        val oldOrderedNodes = BeforeOrdering.constructOrderingIdx(g, pageParentId, userId, from.parentId)._1
        //    val oldOrderedNodes: Seq[Int] = BeforeOrdering.taskGraphToSortedForest(g, userId, from.parentIds)


        // Kanban item dropped at an edge of a container
        if(oldOrderedNodes.size != oldContChilds.length) {
          // console.log("Kanban elements by sortable: ", oldContChilds)
          scribe.error(s"Kanban elements by orderedNodes: ${ getNodeStr(g, oldOrderedNodes) }")
          scribe.error(s"oldOrderedNodes.size(${ oldOrderedNodes.size }) != oldContChilds.length(${ oldContChilds.length }) => Kanban item dropped at an edge of a container")
          return GraphChanges.empty merge cycleDeletionGraphChanges
        }

        val oldIndex = oldOrderedNodes.indexOf(g.idToIdx(sortedNodeId))

        // Kanban data and node data diverge
        if(oldIndex != oldSortIndex) {
          scribe.error(s"index of reconstruction and sort must match, oldPosition(${ oldIndex + 1 }) != oldSortPosition(${ oldSortIndex + 1 })")
          return GraphChanges.empty merge cycleDeletionGraphChanges
        }

        // Cleanup previous edges
        val (previousBefore, previousAfter) = BeforeOrdering.getBeforeAndAfter(g, oldIndex, oldOrderedNodes)

        val previousEdges: Set[Option[Edge]] = Set(
          previousBefore match {
            case Some(b) => Some(Edge.Before(b, sortedNodeId, from.parentId))
            case _       => None
          },
          previousAfter match {
            case Some(a) => Some(Edge.Before(sortedNodeId, a, from.parentId))
            case _       => None
          }
        )

        @inline def relinkEdges = if(previousBefore.isDefined && previousAfter.isDefined) Set(Some(Edge.Before(previousBefore.get, previousAfter.get, into.parentId))) else Set.empty[Option[Edge]]

        val newOrderedNodes: Seq[Int] = BeforeOrdering.constructOrderingIdx(g, pageParentId, userId, into.parentId)._1

        if(newOrderedNodes.isEmpty)
          return GraphChanges.empty merge cycleDeletionGraphChanges

        // Kanban item dropped at an edge of a container
        if(newOrderedNodes.size + 1 < newContChilds.length) {
          //TODO: what is the difference to the similar if-block before: if(oldOrderedNodes.size != oldContChilds.length)
          // console.log("Kanban elements by sortable: ", newContChilds)
          scribe.warn(s"Kanban elements by orderedNodes: ${ getNodeStr(g, newOrderedNodes) }")
          scribe.warn(s"newOrderedNodes.size(${ newOrderedNodes.size }) != newContChilds.length(${ newContChilds.length }) => Kanban item dropped at an edge of a container")
          return GraphChanges.empty merge cycleDeletionGraphChanges
        }

        val gcProposal: GraphChanges = if(containerChanged && newSortIndex == newOrderedNodes.size) {
          // very end !!! special case of before semantic !!!

          //      scribe.debug("!!!handling special case of before semantic in CHANGED container!!!")

          val chronologicalOverwrites = newOrderedNodes.reverse.takeWhile(g.chronologicalNodesAscending(_).id != sortedNodeId)

          val newBeforeEdges: Set[Edge] = (chronologicalOverwrites.sliding(2).toSeq.map(l => {
            Edge.Before(g.nodes(l.last).id, g.nodes(l.head).id, into.parentId)
          })(breakOut):Set[Edge]) + Edge.Before(g.nodeIds(newOrderedNodes.last), sortedNodeId, into.parentId)

          GraphChanges(
            addEdges = newBeforeEdges ++ relinkEdges.flatten,
            delEdges = previousEdges.flatten
          )
        } else  if(!containerChanged && newSortIndex == newOrderedNodes.size - 1) {

          //      scribe.debug("!!!handling special case of before semantic in SAME container!!!")

          val chronologicalOverwrites = oldOrderedNodes.reverse.takeWhile(g.chronologicalNodesAscending(_).id != sortedNodeId)

          val newBeforeEdges: Set[Edge] = (chronologicalOverwrites.sliding(2).toSeq.map(l => {
            Edge.Before(g.nodes(l.last).id, g.nodes(l.head).id, into.parentId)
          })(breakOut):Set[Edge]) + Edge.Before(g.nodeIds(oldOrderedNodes.last), sortedNodeId, into.parentId)

          GraphChanges(
            addEdges = newBeforeEdges ++ relinkEdges.flatten,
            delEdges = previousEdges.flatten
          )

        } else {
          val (nowBefore, nowMiddle, nowAfter) = {

            val (newBefore, newAfter) = if(containerChanged) {

              val (b, a) = (newSortIndex - 1, newSortIndex)

              (b, a)
            } else {
              //      scribe.debug(s"Container not changed")

              val downCorrection = if(movedDownwards) 1 else 0 // before correction
              val upCorrection = if(!movedDownwards) -1 else 0 // after correction
              val beforeIndexShift = if((newSortIndex - 1 + downCorrection) == oldSortIndex) -2 else -1
              val afterIndexShift = if((newSortIndex + 1 + upCorrection) == oldSortIndex) 2 else 1

              val (b, a) = (newSortIndex + beforeIndexShift + downCorrection, newSortIndex + afterIndexShift + upCorrection)

              // If newBefore or newAfter equals oldSortIndex in the same container (outer if condition), we would create a selfloop
              if(b == oldSortIndex || a == oldSortIndex) {
                scribe.error(s"Index conflict: old position ${ oldSortIndex + 1 }, before: ${ b + 1 }, after: ${ a + 1 }")
                return GraphChanges.empty merge cycleDeletionGraphChanges
              }

              (b, a)
            }

            val b = if(newBefore > -1) Some(g.nodeIds(newOrderedNodes(newBefore))) else None
            val a = if(newAfter < newOrderedNodes.size) Some(g.nodeIds(newOrderedNodes(newAfter))) else None
            val m = Some(sortedNodeId)
            (b, m, a)
          }

          val cleanupBeforeEdges = if(nowBefore.isDefined && nowAfter.isDefined) Set(Some(Edge.Before(nowBefore.get, nowAfter.get, from.parentId))) else Set.empty[Option[Edge]]

          val newBeforeEdges = Set(
            if(nowBefore.isDefined && nowMiddle.isDefined)
              Some(Edge.Before(nowBefore.get, nowMiddle.get, into.parentId))
            else
              None
            ,
            if(nowAfter.isDefined && nowMiddle.isDefined)
              Some(Edge.Before(nowMiddle.get, nowAfter.get, into.parentId))
            else
              None
          )

          GraphChanges(
            addEdges = (newBeforeEdges ++ relinkEdges).flatten,
            delEdges = (previousEdges ++ cleanupBeforeEdges).flatten
          )
        }

        val gc = (gcProposal.copy(addEdges = gcProposal.addEdges.filterNot(e => e.sourceId == e.targetId)) merge cycleDeletionGraphChanges).consistent
//        val gc = (gcProposal.copy(addEdges = gcProposal.addEdges.filterNot(e => e.sourceId == e.targetId))).consistent
//        scribe.debug("SORTING\n" +
//          s"dragged node: ${ g.nodesById(sortedNodeId).str }\n\t" +
//          s"from ${ g.nodesById(from.parentId).str } containing ${ getNodeStr(g, oldOrderedNodes) }\n\t" +
//          s"from position ${ oldIndex + 1 } / ${ oldOrderedNodes.length }\n\t" +
//          s"into ${ g.nodesById(into.parentId).str } containing ${ getNodeStr(g, newOrderedNodes) }\n\t" +
//          s"to position ${ newSortIndex + 1 } / ${ newOrderedNodes.length }")

//        GraphChanges.log(gc, Some(state.graph.now))
        scribe.info("SUCCESS: Calculated new before edges!")
        gc

      case _ =>
        scribe.warn("Sortable need a page to determine toplevel elements")
        GraphChanges.empty
    }
  }

  private def submit(changes:GraphChanges) = {
    state.eventProcessor.enriched.changes.onNext(changes)
  }
  private def addTag(nodeId:NodeId, tagId:NodeId):Unit = addTag(nodeId :: Nil, tagId)
  private def addTag(nodeIds:Iterable[NodeId], tagId:NodeId):Unit = addTag(nodeIds, tagId :: Nil)
  private def addTag(nodeIds:Iterable[NodeId], tagIds:Iterable[NodeId]):Unit = {
    val changes = nodeIds.foldLeft(GraphChanges.empty) {(currentChange, nodeId) =>
      val graph = state.graph.now
      val subjectIdx = graph.idToIdx(nodeId)
      val deletedAt = if(subjectIdx == -1) None else graph.combinedDeletedAt(subjectIdx)
      currentChange merge GraphChanges.connect((s,d,t) => new Edge.Parent(s,d,t))(nodeIds, EdgeData.Parent(deletedAt), tagIds)
    }
    submit(changes)
  }

  private def moveInto(nodeId:NodeId, newParentId:NodeId):Unit = moveInto(nodeId :: Nil, newParentId :: Nil)
  private def moveInto(nodeId:NodeId, newParentIds:Iterable[NodeId]):Unit = moveInto(nodeId :: Nil, newParentIds)
  private def moveInto(nodeIds:Iterable[NodeId], newParentIds:Iterable[NodeId]):Unit = {
    submit(GraphChanges.moveInto(state.graph.now, nodeIds, newParentIds))
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

    val topologicalChannelParents = state.channelForest.now.flatMap(filterParents(channelId, _))
    val disconnect:GraphChanges = GraphChanges.disconnect(Edge.Parent)(channelId, topologicalChannelParents)
    val connect:GraphChanges = GraphChanges.connect(Edge.Parent)(channelId, targetChannelId)
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
      case (dragging: SelectedNodes, SelectedNodesBar, false, false) => // do nothing, since already selected

      case (dragging: Channel, target: Channel, false, false) => moveChannel(dragging.nodeId, target.nodeId)
      case (dragging: AnyNodes, target: Channel, false, false) => moveInto(dragging.nodeIds, target.nodeId :: Nil)
      case (dragging: Channel, target: SingleNode, false, false) => addTag(dragging.nodeId, target.nodeId)

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
  sortableActions:PartialFunction[(SortableStopEvent, DragPayload, DragContainer, DragContainer, Boolean, Boolean),Unit] = {
    import DragContainer._
    def graph = state.graph.now
    def userId = state.user.now.id

    {
      case (e, dragging: DragItem.Kanban.Column, from: Kanban.Area, into: Kanban.ColumnArea, false, false) =>
        val move = GraphChanges.changeTarget[NodeId, NodeId, Edge.Parent](Edge.Parent)(Some(dragging.nodeId), Some(from.parentId), Some(into.parentId))
        val beforeEdges = beforeChanges(graph, userId, e, dragging, from, into)
        state.eventProcessor.enriched.changes.onNext(move merge beforeEdges)

      case (e, dragging: DragItem.Kanban.Item, from: Kanban.Area, into: Kanban.Column, false, false) =>
        val move = GraphChanges.changeTarget(Edge.Parent)(Some(dragging.nodeId), Some(from.parentId), Some(into.parentId))
        val beforeEdges = beforeChanges(graph, userId, e, dragging, from, into)
        state.eventProcessor.enriched.changes.onNext(move merge beforeEdges)

      case (e, dragging: DragItem.Kanban.Card, from: Kanban.Area, into: Kanban.Uncategorized, false, false) =>
        val move = GraphChanges.changeTarget(Edge.Parent)(Some(dragging.nodeId), Some(from.parentId), Some(into.parentId))
        val beforeEdges = beforeChanges(graph, userId, e, dragging, from, into)
        state.eventProcessor.enriched.changes.onNext(move merge beforeEdges)
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
    dom.console.log(e.dragEvent)

    if(disableSort) {
      println("sort is disabled")
      e.cancel()
    } else {
      println("sorting....")
      val overContainerWorkaround = e.dragEvent.asInstanceOf[js.Dynamic].overContainer.asInstanceOf[dom.html.Element] // https://github.com/Shopify/draggable/issues/256
      val sourceContainerWorkaround = e.dragEvent.asInstanceOf[js.Dynamic].sourceContainer.asInstanceOf[dom.html.Element] // TODO: report as feature request
      val draggingOpt = readDragPayload(e.dragEvent.source)
      val overContainerOpt = readDragContainer(overContainerWorkaround)
      val sourceContainerOpt = readDragContainer(sourceContainerWorkaround) // will be written by registerSortableContainer

      println(s"$draggingOpt, $overContainerOpt, $sourceContainerOpt")
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
