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
object TaskOrdering {

  type Position = Int

  // (Re)construct ordering of a parent container.
  def constructOrdering(graph: Graph, parentId: NodeId, userId: UserId): Seq[NodeId] = {
    ???
  }

  def getValueOfNodeId(graph: Graph, nodeId: NodeId): BigDecimal = {
    ???
  }

  def getValueBefore(newAfter: BigDecimal): BigDecimal = ???
  def getValueAfter(newBefore: BigDecimal): BigDecimal = ???
  def getValueBetween(newBefore: BigDecimal, newAfter: BigDecimal): BigDecimal = ???



















  // Task filters

  private def taskFilter: Node => Boolean = { node =>
    @inline def isContent = node.isInstanceOf[Node.Content]
    @inline def isTask = node.role.isInstanceOf[NodeRole.Task.type]
    isContent && isTask
  }

  private def categorizationFilter(graph: Graph, parentIdx: Int, nodeIdx: Int, userId: UserId) = {
    @inline def isOnlyToplevel = graph.parentsIdx.contains(nodeIdx)(parentIdx)
    @inline def isExpanded = graph.isExpanded(userId, graph.nodeIds(nodeIdx))
    @inline def hasChildren = graph.hasNotDeletedChildrenIdx(nodeIdx)
    !isOnlyToplevel || isExpanded || hasChildren
  }

  def sort[T](graph: Graph, parentId: NodeId, container: Seq[T], extractNodeId: T => NodeId): Seq[T] = {
    sortIdxGen(graph, parentId, container, graph.idToIdx compose extractNodeId, (i: Int) => container.find(t => extractNodeId(t) == graph.nodeIds(i)))
  }
  def sortIdx(graph: Graph, parentId: NodeId, container: Seq[Int]): Seq[Int] = {
    sortIdxGen[Int](graph, parentId, container, identity, Some(_))
  }
  def sortIdxGen[T](graph: Graph, parentId: NodeId, container: Seq[T], extractIdx: T => Int, liftIdx: Int => Option[T]): Seq[T] = {
    val sorted = graph.topologicalSortByIdx[T](container, extractIdx, liftIdx)
    sorted
  }

  def nodesOfInterest(graph: Graph, pageParentId: NodeId, userId: UserId, parentId: NodeId): Seq[Int] = {
    val parentIdx = graph.idToIdx(parentId)
    val pageParentIdx = graph.idToIdx(pageParentId)

    val nodesOfInterest: Seq[Int] = graph.notDeletedChildrenIdx(parentIdx) // nodes in container
    //    val toplevel = graph.notDeletedChildrenIdx(pageParentIdx)
    nodesOfInterest.filter(idx => {
      taskFilter(graph.nodes(idx)) && categorizationFilter(graph, pageParentIdx, idx, userId)
    })
  }

  def constructOrderingIdx(graph: Graph, pageParentId: NodeId, userId: UserId, parentId: NodeId): Seq[Int] = {
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
      categorizationFilter(graph, pageParentIdx, nodeIdx, userId)
    }

    (categorizedTasks, uncategorizedTasks)
  }

  def extractAndPartitionTasks(graph: Graph, pageId: NodeId, userId: UserId): (ArraySet, ArraySet) =  partitionTasks(graph, userId, pageId)(extractTasksWithParents(graph))

  // TODO: Empty toplevel crashes
  def taskGraphToSortedForest(graph: Graph, userId: UserId, pageParentId: NodeId): (ArraySet, Seq[Tree]) = {

    val (categorizedTasks, inboxTasks) = extractAndPartitionTasks(graph, pageParentId, userId)
    val pageParentIdx = graph.idToIdx(pageParentId)
    val taskGraph = graph.filterIdx(idx => categorizedTasks.contains(idx) || idx == pageParentIdx)
    val toplevelIds = taskGraph.notDeletedChildrenIdx(taskGraph.idToIdx(pageParentId))

    val unsortedForest = (toplevelIds.map(idx => taskGraph.redundantTree(idx, excludeCycleLeafs = false))(breakOut): List[Tree])
      .sortBy(_.node.id)

    val sortedColumnForest = sort[Tree](taskGraph, pageParentId, unsortedForest, (t: Tree) => t.node.id)

    (inboxTasks, sortedColumnForest)
  }

}
