package wust.graph

import scala.collection.breakOut
import wust.ids.{EdgeData, EpochMilli, NodeId, NodeRole, UserId}
import wust.util.algorithm
import flatland._

import scala.math.BigDecimal.RoundingMode
import scala.util.Try

/**
  * Algorithms to define an ordering on nodes
  *
  * Default: NodeIds => time based (cuuid)
  */
object TaskOrdering {

  type Position = Int

  def constructOrderingOf[T](graph: Graph, parentId: NodeId, container: Seq[T], extractNodeId: T => NodeId): Seq[T] = {
    computeOrder(graph, parentId, container.map(extractNodeId)).flatMap(nodeId => container.find(t => extractNodeId(t) == nodeId))
  }
  // (Re)construct ordering of a parent container.
  def constructOrdering(graph: Graph, parentId: NodeId): Seq[NodeId] = {
    computeOrder(graph, parentId, graph.notDeletedChildrenIdx(graph.idToIdx(parentId)).map(graph.nodeIds))
  }

  private def computeOrder(graph: Graph, parentId: NodeId, container: Seq[NodeId]) = {
//    scribe.info(s"Computing order in parent ${graph.nodesById(parentId).str}")
    val sorted = container.sortWith { (id1, id2) =>
      val orderValue1 = getValueOfNodeId(graph, parentId, id1)._2
      val orderValue2 = getValueOfNodeId(graph, parentId, id2)._2
      orderValue1 < orderValue2
    }

//    scribe.info(s"${sorted.map(graph.nodesById).map(n => s"${n.str}${getValueOfNodeId(graph, parentId, n.id)}")}")

    sorted
  }

  @inline def abortSorting(errorMsg: String) = {
    scribe.error(errorMsg)
    GraphChanges.empty
  }

  @inline private def getNodeIdStr(graph: Graph, nodeId: NodeId) = graph.nodesById(nodeId).str
  @inline private def getNodeIdxStr(graph: Graph, indices: Seq[Int]) = indices.map(idx => graph.nodes(idx).str)
  @inline private def getNodeIdxStr(graph: Graph, idx: Int) = graph.nodes(idx).str

  // TODO: check if fixed parentEdgeIdx returns edgeIdx not parentIdx
  @inline def getParentEdge(graph: Graph, parentId: NodeId, nodeId: NodeId) = graph.parentEdgeIdx(graph.idToIdx(nodeId)).map(graph.edges).find(_.targetId == parentId).map(_.asInstanceOf[Edge.Parent])

  @inline private def getValueOfNodeId(graph: Graph, parentId: NodeId, nodeId: NodeId): (Option[EpochMilli], BigDecimal) = {
//    scribe.info(s"Getting parent edge of ${graph.nodesById(nodeId).str} and parent ${graph.nodesById(parentId).str}")
    val edge = getParentEdge(graph, parentId, nodeId)
//    if(edge.isDefined) scribe.info(s"PARENT EDGE FOUND IN ORDERING: ${graph.nodesById(nodeId).str} -> ${graph.nodesById(parentId).str}")
    def order = edge.flatMap(_.data.ordering) match {
//        case Some(orderValue) => scribe.info(s"FOUND ORDERING VALUE: $orderValue"); orderValue
        case Some(orderValue) => orderValue
        case _                => BigDecimal(graph.nodeCreated(graph.idToIdx(nodeId)))
      }
    (edge.flatMap(_.data.deletedAt), order)
  }

  @inline private def getValueBefore(newAfter: BigDecimal): BigDecimal = ???
  @inline private def getValueAfter(newBefore: BigDecimal): BigDecimal = ???
  @inline private def getValueBetween(newBefore: BigDecimal, newAfter: BigDecimal): BigDecimal = ???
  @inline private def checkMovedDownwards(previousPosition: Position, newPosition: Position) = previousPosition < newPosition


//  def computeOrderingValue(graph: Graph, parentId: NodeId, nodeId: NodeId, containerChanged: Boolean, previousDomPosition: Position, newDomPosition: Position, containerSize: Int): BigDecimal = {
//
//
//    //    val previousOrderingValue = TaskOrdering.getValueOfNodeId(graph, sortedNodeId)
//    //    val previousOrderingValue = TaskOrdering.getValueOfNodeId(graph, sortedNodeId)
//    if(containerChanged) {
//
//      // very last element
//      val movedToLastPosition = newDomPosition == containerSize + 1
//
//      val orderingValueOfBeforeElement = 1
//      val orderingValueOfAfterElement = 1
//
//
//    } else {
//      // Position / Index is correct in new container of sortable but is not known in/ added to the data structure yet
//      //      val offset = if(checkMovedDownwards(previousDomPosition, newDomPosition)) -1 else 0
//
//
//    }
//    ???
//  }


  def constructGraphChangesByOrdering(graph: Graph, userId: UserId, nodeId: NodeId, containerChanged: Boolean, previousDomPosition: Position, newDomPosition: Position, from: NodeId, into: NodeId): GraphChanges = {

    // Reconstruct order of nodes in the `from` container
    val previousOrderedNodes = TaskOrdering.constructOrdering(graph, from)
    if(previousOrderedNodes.isEmpty) return abortSorting(s"Could not reconstruct ordering in node ${getNodeIdStr(graph, from)}")

    val previousPosition = previousOrderedNodes.indexOf(nodeId)
    if(previousPosition == -1) return abortSorting(s"Could not determine position of sorted node")

    // Data of dom and internal structure diverge
    if(previousPosition != previousDomPosition) return abortSorting(s"index of reconstruction and sort must match, oldPosition in parent ($previousPosition) != oldPosition in dom ($previousDomPosition)")

    // Reconstruct order of nodes in the `into` container
    val newOrderedNodes: Seq[NodeId] = if(containerChanged) TaskOrdering.constructOrdering(graph, into)
                                       else previousOrderedNodes

    if(newOrderedNodes.isEmpty) return abortSorting(s"Could not reconstruct ordering in node ${graph.nodesById(into).str}")

    // Get corresponding nodes that are before and after the dragged node
    val indexOffset = if(!containerChanged && checkMovedDownwards(previousDomPosition, newDomPosition)) 1 else 0

    // Instead of differentiate between multiple cases (container, move direction, {first,last} position, ...) just try to get the index
    val beforeNode = Try(newOrderedNodes(newDomPosition-1 + indexOffset)).toOption
    val afterNode = Try(newOrderedNodes(newDomPosition + indexOffset)).toOption


//    if(beforeNode.isDefined && afterNode.isDefined)
//      TaskOrdering.getValueBetween()
//    else if(beforeNode.isDefined)

    val beforeParentData = beforeNode.map(nodeId => getValueOfNodeId(graph, into, nodeId))
    val afterParentData = afterNode.map(nodeId => getValueOfNodeId(graph, into, nodeId))
    val beforeNodeOrderingValue = beforeParentData.map(_._2)
    val afterNodeOrderingValue = afterParentData.map(_._2)

    val newOrderingValue = {
      val (before: BigDecimal, after: BigDecimal) = if(beforeNodeOrderingValue.isDefined && afterNodeOrderingValue.isDefined) {
        (beforeNodeOrderingValue.get, afterNodeOrderingValue.get)
      } else if(beforeNodeOrderingValue.isDefined) {
        val before = beforeNodeOrderingValue.get
        val after = before.setScale(before.scale-1, RoundingMode.CEILING)
        (before, after)
      } else {
        val after = afterNodeOrderingValue.get
        val before = after.setScale(after.scale-1, RoundingMode.FLOOR)
        (before, after)
      }
      if(before < after) before + (after - before)/2
      else after + (before - after)/2
    }

    val newParentEdge = if(containerChanged) Edge.Parent(nodeId, EdgeData.Parent(newOrderingValue), into)
                        else {
      val keepedDeletedAt = getParentEdge(graph, from, nodeId).flatMap(_.data.deletedAt)
      Edge.Parent(nodeId, new EdgeData.Parent(keepedDeletedAt, Some(newOrderingValue)), into)
    }

    GraphChanges(addEdges = Set(newParentEdge))

//    val newOrderingValue = TaskOrdering.computeOrderingValue(graph, into, nodeId, containerChanged, previousDomPosition, newDomPosition, newOrderedNodes.size)
  }













  // Task filters

//  private def taskFilter: Node => Boolean = { node =>
//    @inline def isContent = node.isInstanceOf[Node.Content]
//    @inline def isTask = node.role.isInstanceOf[NodeRole.Task.type]
//    isContent && isTask
//  }
//
//  private def categorizationFilter(graph: Graph, parentIdx: Int, nodeIdx: Int, userId: UserId) = {
//    @inline def isOnlyToplevel = graph.parentsIdx.contains(nodeIdx)(parentIdx)
//    @inline def isExpanded = graph.isExpanded(userId, graph.nodeIds(nodeIdx))
//    @inline def hasChildren = graph.hasNotDeletedChildrenIdx(nodeIdx)
//    !isOnlyToplevel || isExpanded || hasChildren
//  }
//
//  def sort[T](graph: Graph, parentId: NodeId, container: Seq[T], extractNodeId: T => NodeId): Seq[T] = {
//    sortIdxGen(graph, parentId, container, graph.idToIdx compose extractNodeId, (i: Int) => container.find(t => extractNodeId(t) == graph.nodeIds(i)))
//  }
//  def sortIdx(graph: Graph, parentId: NodeId, container: Seq[Int]): Seq[Int] = {
//    sortIdxGen[Int](graph, parentId, container, identity, Some(_))
//  }
//  def sortIdxGen[T](graph: Graph, parentId: NodeId, container: Seq[T], extractIdx: T => Int, liftIdx: Int => Option[T]): Seq[T] = {
//    val sorted = graph.topologicalSortByIdx[T](container, extractIdx, liftIdx)
//    sorted
//  }

//  def nodesOfInterest(graph: Graph, pageParentId: NodeId, userId: UserId, parentId: NodeId): Seq[Int] = {
//    val parentIdx = graph.idToIdx(parentId)
//    val pageParentIdx = graph.idToIdx(pageParentId)
//
//    val nodesOfInterest: Seq[Int] = graph.notDeletedChildrenIdx(parentIdx) // nodes in container
//    //    val toplevel = graph.notDeletedChildrenIdx(pageParentIdx)
//    nodesOfInterest.filter(idx => {
//      taskFilter(graph.nodes(idx)) && categorizationFilter(graph, pageParentIdx, idx, userId)
//    })
//  }

//  def constructOrderingIdx(graph: Graph, pageParentId: NodeId, userId: UserId, parentId: NodeId): Seq[Int] = {
//    sortIdx(graph, parentId, nodesOfInterest(graph, pageParentId, userId, parentId))
//  }

//  def getBeforeAndAfter(g: Graph, index: Int, orderedNodes: Seq[Int]): (Option[NodeId], Option[NodeId]) = {
//    val previousBefore = if(index > 0) Some(g.nodeIds(orderedNodes(index - 1))) else None // Moved from very beginning
//    val previousAfter = if(index < orderedNodes.size - 1) Some(g.nodeIds(orderedNodes(index + 1))) else None // Moved from very end
//    (previousBefore, previousAfter)
//  }




  /*
   * FROM kanban data
   */

//  def extractTasksWithoutParents(graph: Graph, pageParentArraySet: ArraySet): ArraySet = graph.subset { nodeIdx =>
//    val node = graph.nodes(nodeIdx)
//    @inline def noPage = pageParentArraySet.containsNot(nodeIdx)
//
//    taskFilter(node) && noPage
//  }

//  def extractTasksWithParents(graph: Graph): ArraySet = graph.subset { nodeIdx =>
//    taskFilter(graph.nodes(nodeIdx))
//  }

//  def partitionTasks(graph: Graph, userId: UserId, pageParentId: NodeId)(allTasks: ArraySet): (ArraySet, ArraySet) = {
//    val pageParentIdx = graph.idToIdx(pageParentId)
//    val (categorizedTasks, uncategorizedTasks) = allTasks.partition { nodeIdx =>
//      categorizationFilter(graph, pageParentIdx, nodeIdx, userId)
//    }
//
//    (categorizedTasks, uncategorizedTasks)
//  }

//  def extractAndPartitionTasks(graph: Graph, pageId: NodeId, userId: UserId): (ArraySet, ArraySet) =  partitionTasks(graph, userId, pageId)(extractTasksWithParents(graph))

  // TODO: Empty toplevel crashes
//  def taskGraphToSortedForest(graph: Graph, userId: UserId, pageParentId: NodeId): (ArraySet, Seq[Tree]) = {
//
//    val (categorizedTasks, inboxTasks) = extractAndPartitionTasks(graph, pageParentId, userId)
//    val pageParentIdx = graph.idToIdx(pageParentId)
//    val taskGraph = graph.filterIdx(idx => categorizedTasks.contains(idx) || idx == pageParentIdx)
//    val toplevelIds = taskGraph.notDeletedChildrenIdx(taskGraph.idToIdx(pageParentId))
//
//    val unsortedForest = (toplevelIds.map(idx => taskGraph.redundantTree(idx, excludeCycleLeafs = false))(breakOut): List[Tree])
//      .sortBy(_.node.id)
//
//    val sortedColumnForest = sort[Tree](taskGraph, pageParentId, unsortedForest, (t: Tree) => t.node.id)
//
//    (inboxTasks, sortedColumnForest)
//  }

}
