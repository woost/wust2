package wust.graph

import scala.collection.breakOut
import wust.ids.{NodeId, NodeRole, UserId}
import wust.util.algorithm
import flatland._

/**
  * Algorithms to define an ordering on nodes
  *
  * Default: NodeIds => time based (cuuid)
  */
object BeforeOrdering {

  private def parentBeforeIdx(graph: Graph, parentId: NodeId): NestedArrayInt = {

//    scribe.debug(s"Parent: id = $parentId, base58 = ${parentId.toBase58}, idx = ${graph.idToIdx(parentId)}")
    val parentBefores = graph.beforeOrderingByParent(parentId)
//    scribe.debug(s"parentBefores = ${parentBefores.mkString(", ")}")

//    scribe.debug(s"Computing degree of parentBefores")
    val beforeParentDegree = new Array[Int](graph.edges.length)
    parentBefores.foreachElement { before =>
      val sourceIdx = graph.idToIdx(before.sourceId)
      if(sourceIdx != -1) beforeParentDegree(sourceIdx) += 1
    }

//    scribe.debug(s"Building parentBeforesIdx")
    val beforeParentIdxBuilder = NestedArrayInt.builder(beforeParentDegree)
    parentBefores.foreachElement { before =>
      val sourceIdx = graph.idToIdx(before.sourceId)
      val targetIdx = graph.idToIdx(before.targetId)
      beforeParentIdxBuilder.add(sourceIdx, targetIdx)
    }
    val nested = beforeParentIdxBuilder.result()

//    scribe.debug(s"nested array = ${nested.mkString(", ")}")

    nested
  }

  def breakCyclesGraphChanges(graph: Graph, beforeIdx: NestedArrayInt, parentId: NodeId, container: Seq[Int]): GraphChanges = {
    //    val cyclesIdx = algorithm.linearInvolmentsOfCycleSearch(container.toArray, graph.beforeIdx).map(graph.nodeIds)
    scribe.info(s"BREAKING CYCLES")
    if(container.isEmpty) return GraphChanges.empty


    val cyclesIdx = algorithm.containmentsOfCycle(container.toArray, beforeIdx)

    val breakCyclesGraphChanges = if(cyclesIdx.nonEmpty) {
      scribe.info(s"NONEMPTY: ${cyclesIdx.mkString(", ")}")
//      val delBeforeEdges = cyclesIdx.map(graph.nodeIds).sliding(2).map(pair =>
//        Edge.Before(pair.head, wust.ids.EdgeData.Before(parentId), pair.last)
//      ).toIterable
      val beforeEdgesIds: Iterable[(NodeId, NodeId)] = cyclesIdx.flatMap(idx => beforeIdx(idx).map(succIdx => (graph.nodeIds(idx), graph.nodeIds(succIdx))))
      val delBeforeEdges = beforeEdgesIds.map(pair =>
          Edge.Before(pair._1, wust.ids.EdgeData.Before(parentId), pair._2)
        ).toIterable
      GraphChanges.from(delEdges = delBeforeEdges)
    } else GraphChanges.empty

    scribe.info(s"graphChanges = $breakCyclesGraphChanges")
    breakCyclesGraphChanges
  }

  private def taskFilter: Node => Boolean = { node =>
    @inline def isContent = node.isInstanceOf[Node.Content]
    @inline def isTask = node.role.isInstanceOf[NodeRole.Task.type]
    isContent && isTask
  }

  private def categorizationFilter(graph: Graph, parents: Int => Boolean, nodeIdx: Int, userId: UserId) = {
    @inline def isOnlyToplevel = graph.parentsIdx.forall(nodeIdx)(parents)
    @inline def isExpanded = graph.isExpanded(userId, graph.nodeIds(nodeIdx))
    @inline def hasChildren = graph.hasNotDeletedChildrenIdx(nodeIdx)
    !isOnlyToplevel || isExpanded || hasChildren
  }

  def sort[T](graph: Graph, parentId: NodeId, container: Seq[T], extractNodeId: T => NodeId, breakCycles: Boolean = false): (Seq[T], GraphChanges) = {
    sortIdxGen(graph, parentId, container, graph.idToIdx compose extractNodeId, (i: Int) => container.find(t => extractNodeId(t) == graph.nodeIds(i)), breakCycles)
  }
  def sortIdx(graph: Graph, parentId: NodeId, container: Seq[Int], breakCycles: Boolean = false): (Seq[Int], GraphChanges) = {
    sortIdxGen[Int](graph, parentId, container, identity, Some(_), breakCycles)
  }
  def sortIdxGen[T](graph: Graph, parentId: NodeId, container: Seq[T], extractIdx: T => Int, liftIdx: Int => Option[T], breakCycles: Boolean): (Seq[T], GraphChanges) = {
    //    scribe.debug(s"PARENT = ${graph.nodesById(parentId).str} (id = $parentId)")
    val beforeIdx = parentBeforeIdx(graph, parentId)
    val gc = if(breakCycles) breakCyclesGraphChanges(graph, beforeIdx, parentId, container.map(extractIdx)) else GraphChanges.empty
    val sortGraph = if(gc.nonEmpty) {
      scribe.info(s"NON-EMPTY BREAK CYCLES: $gc")
      scribe.info(s"NODES: ${container.map(extractIdx).map(graph.nodes).map(_.str)}")
      graph.applyChanges(gc)
    } else graph
    scribe.info("sorting")
    val sorted = sortGraph.topologicalSortByIdx[T](container, extractIdx, liftIdx)
    scribe.info("sorted")
    (sorted, gc)
  }

  def nodesOfInterest(graph: Graph, pageParentId: NodeId, userId: UserId, parentId: NodeId): Seq[Int] = {
    val parentIdx = graph.idToIdx(parentId)
    val pageParentIdx = graph.idToIdx(pageParentId)

    val nodesOfInterest: Seq[Int] = graph.notDeletedChildrenIdx(parentIdx) // nodes in container
    //    val toplevel = graph.notDeletedChildrenIdx(pageParentIdx)
    nodesOfInterest.filter(idx => {
      taskFilter(graph.nodes(idx)) && categorizationFilter(graph, (idx: Int) => idx == pageParentIdx, idx, userId)
    })
  }

  def constructOrderingIdx(graph: Graph, pageParentId: NodeId, userId: UserId, parentId: NodeId): (Seq[Int], GraphChanges) = {
    sortIdx(graph, parentId, nodesOfInterest(graph, pageParentId, userId, parentId))
  }

  def getBeforeAndAfter(g: Graph, index: Int, orderedNodes: Seq[Int]): (Option[NodeId], Option[NodeId]) = {
    val previousBefore = if(index > 0) Some(g.nodeIds(orderedNodes(index - 1))) else None // Moved from very beginning
    val previousAfter = if(index < orderedNodes.size - 1) Some(g.nodeIds(orderedNodes(index + 1))) else None // Moved from very end
    (previousBefore, previousAfter)
  }

  /*
   * FROM kanban data
   */

  def extractTasksWithoutParents(graph: Graph, pageParentArraySet: ArraySet): ArraySet = graph.subset { nodeIdx =>
    val node = graph.nodes(nodeIdx)
    @inline def noPage = pageParentArraySet.containsNot(nodeIdx)

    taskFilter(node) && noPage
  }

  def extractTasksWithParents(graph: Graph): ArraySet = graph.subset { nodeIdx =>
    taskFilter(graph.nodes(nodeIdx))
  }

  def partitionTasks(graph: Graph, userId: UserId, pageParentId: NodeId)(allTasks: ArraySet): (ArraySet, ArraySet) = {
    val pageParentIdx = graph.idToIdx(pageParentId)
    val (categorizedTasks, uncategorizedTasks) = allTasks.partition { nodeIdx =>
      categorizationFilter(graph, (idx: Int) => idx == pageParentIdx, nodeIdx, userId)
    }

    (categorizedTasks, uncategorizedTasks)
  }

  def extractAndPartitionTasks(graph: Graph, pageId: NodeId, userId: UserId): (ArraySet, ArraySet) =  partitionTasks(graph, userId, pageId)(extractTasksWithParents(graph))

  // TODO: Empty toplevel crashes
  def taskGraphToSortedForest(graph: Graph, userId: UserId, pageParentId: NodeId): (ArraySet, Seq[Tree]) = {

    val (categorizedTasks, uncategorizedTasks) = extractAndPartitionTasks(graph, pageParentId, userId)
    val taskGraph = graph.filterIdx(idx => categorizedTasks.contains(idx) || idx == graph.idToIdx(pageParentId))
    val toplevelIds = taskGraph.notDeletedChildrenIdx(taskGraph.idToIdx(pageParentId))

    val unsortedForest = (toplevelIds.map(idx => taskGraph.redundantTree(idx, excludeCycleLeafs = false))(breakOut): List[Tree])
      .sortBy(_.node.id)

    val (sortedForest, _) = sort[Tree](taskGraph, pageParentId, unsortedForest, (t: Tree) => t.node.id)

    (uncategorizedTasks, sortedForest)
  }

}
