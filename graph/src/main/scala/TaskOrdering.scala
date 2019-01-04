package wust.graph

import wust.ids.{EdgeData, EpochMilli, NodeId, UserId}

/**
  * Algorithms to define an ordering on nodes
  *
  * Default: NodeIds => time based (cuuid)
  */
object TaskOrdering {

  type Position = Int

  // TODO: Allow definitions of Orderings
  def constructOrderingOf[T](graph: Graph, parentId: NodeId, container: Seq[T], extractNodeId: T => NodeId, customSortWith: Option[(BigDecimal, BigDecimal) => Boolean] = None): Seq[T] = {
    computeOrder(graph, parentId, container.map(extractNodeId), customSortWith).flatMap(nodeId => container.find(t => extractNodeId(t) == nodeId))
  }
  // (Re)construct ordering of a parent container.
  def constructOrdering(graph: Graph, parentId: NodeId, customSortWith: Option[(BigDecimal, BigDecimal) => Boolean] = None): Seq[NodeId] = {
    computeOrder(graph, parentId, graph.notDeletedChildrenIdx(graph.idToIdx(parentId)).map(graph.nodeIds), customSortWith)
  }

  // TODO: Make fast
  private def computeOrder(graph: Graph, parentId: NodeId, container: Seq[NodeId], customSortWith: Option[(BigDecimal, BigDecimal) => Boolean]) = {
    val sorted = customSortWith match {
      case None      => container.sortWith { (id1, id2) =>
        val orderValue1 = getValueOfNodeId(graph, parentId, id1)._2
        val orderValue2 = getValueOfNodeId(graph, parentId, id2)._2
        orderValue1 < orderValue2
      }
      case Some(csw) => container.sortWith { (id1, id2) =>
        val orderValue1 = getValueOfNodeId(graph, parentId, id1)._2
        val orderValue2 = getValueOfNodeId(graph, parentId, id2)._2
        csw(orderValue1, orderValue2)
      }
    }

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
    val edge = getParentEdge(graph, parentId, nodeId)
    def order = edge.flatMap(_.data.ordering) match {
        case Some(orderValue) => orderValue
        case _                => BigDecimal(graph.nodeCreated(graph.idToIdx(nodeId)))
      }
    (edge.flatMap(_.data.deletedAt), order)
  }

  @inline private def getValueBefore(newAfter: BigDecimal): BigDecimal = ???
  @inline private def getValueAfter(newBefore: BigDecimal): BigDecimal = ???
  @inline private def getValueBetween(newBefore: BigDecimal, newAfter: BigDecimal): BigDecimal = ???
  @inline private def checkMovedDownwards(previousPosition: Position, newPosition: Position) = previousPosition < newPosition

  @inline private def checkBounds(containerSize: Int, index: Int) = index >= 0 && index < containerSize

  def constructGraphChangesByContainer(graph: Graph, userId: UserId, nodeId: NodeId, containerChanged: Boolean, previousDomPosition: Position, newDomPosition: Position, from: NodeId, into: NodeId, fromItems: Seq[NodeId], intoItems: Seq[NodeId]): GraphChanges = {

    scribe.debug(s"calculate for movement: from position $previousDomPosition to new position $newDomPosition into ${if(containerChanged) "different" else "same"} container")

    // Reconstruct order of nodes in the `from` container
    val previousOrderedNodes = fromItems
    if(previousOrderedNodes.isEmpty) return abortSorting(s"Could not reconstruct ordering in node ${getNodeIdStr(graph, from)}")

    val previousPosition = previousOrderedNodes.indexOf(nodeId)
    if(previousPosition == -1) return abortSorting(s"Could not determine position of sorted node")

    // Data of dom and internal structure diverge
    scribe.debug(s"previous nodes: ${previousOrderedNodes.map(graph.nodesById(_).str)}")
    if(previousPosition != previousDomPosition) return abortSorting(s"index of reconstruction and sort must match, oldPosition in parent ($previousPosition) != oldPosition in dom ($previousDomPosition)")

    // Reconstruct order of nodes in the `into` container
    val newOrderedNodes: Seq[NodeId] = intoItems

    if(newOrderedNodes.isEmpty) return GraphChanges.connect(Edge.Parent)(nodeId, into)

    // Get corresponding nodes that are before and after the dragged node
    val indexOffset = if(!containerChanged && checkMovedDownwards(previousDomPosition, newDomPosition)) 1 else 0

    val beforeNodeIndex = newDomPosition - 1 + indexOffset
    val beforeNode = if(checkBounds(newOrderedNodes.size, beforeNodeIndex))
                       Some(newOrderedNodes(beforeNodeIndex))
                     else None
    scribe.debug(s"before index = $beforeNodeIndex, node = ${beforeNode.map(graph.nodesById)}") //beforeNode.map(n => getNodeIdStr(graph, n))

    val afterNodeIndex = newDomPosition + indexOffset
    val afterNode = if(checkBounds(newOrderedNodes.size, afterNodeIndex))
                      Some(newOrderedNodes(afterNodeIndex))
                    else None
                    scribe.debug(s"after index = $afterNodeIndex, node = ${afterNode.map(graph.nodesById)}") //afterNode.map(n => getNodeIdStr(graph, n))

    val beforeParentData = beforeNode.map(nodeId => getValueOfNodeId(graph, into, nodeId))
    val afterParentData = afterNode.map(nodeId => getValueOfNodeId(graph, into, nodeId))
    val beforeNodeOrderingValue = beforeParentData.map(_._2)
    val afterNodeOrderingValue = afterParentData.map(_._2)

    scribe.debug(s"before node ordering = $beforeNodeOrderingValue")
    scribe.debug(s"after node ordering = $afterNodeOrderingValue")
    val newOrderingValue = {
      if(beforeNodeOrderingValue.isDefined && afterNodeOrderingValue.isDefined) {
        val (before, after) = (beforeNodeOrderingValue.get, afterNodeOrderingValue.get)
        before + (after - before)/2
      } else if(beforeNodeOrderingValue.isDefined) {
        BigDecimal(beforeNodeOrderingValue.get.toBigInt + 1)
      } else if(afterNodeOrderingValue.isDefined) {
        BigDecimal(afterNodeOrderingValue.get.toBigInt - 1)
      } else { // workaround: infamous 'should never happen' case
        scribe.warn("NON-EMPTY but no before / after")
        BigDecimal(graph.nodeCreated(graph.idToIdx(nodeId)))
      }
    }

    val newParentEdge = if(containerChanged) Edge.Parent(nodeId, EdgeData.Parent(newOrderingValue), into)
                        else {
                          val keepedDeletedAt = getParentEdge(graph, from, nodeId).flatMap(_.data.deletedAt)
                          Edge.Parent(nodeId, new EdgeData.Parent(keepedDeletedAt, Some(newOrderingValue)), into)
                        }

    GraphChanges(addEdges = Set(newParentEdge))
  }

}
