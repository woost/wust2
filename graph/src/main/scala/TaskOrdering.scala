package wust.graph

import wust.ids._

import scala.collection.breakOut
import scala.reflect.ClassTag

/**
  * Algorithms to define an ordering on nodes
  *
  * Default: NodeIds => time based (cuuid)
  */
object TaskOrdering {

  type Position = Int

  def constructOrderingOf[T: ClassTag](graph: Graph, parentId: NodeId, container: Seq[T], extractNodeId: T => NodeId): Seq[T] = {
    assert(container.forall(t => graph.idToIdx(extractNodeId(t)).isDefined), "every item in container has to be in the graph")
    assert(container.forall(t => graph.parentsIdx.exists(graph.idToIdxOrThrow(extractNodeId(t)))(idx => graph.nodeIds(idx) == parentId)), "parentId has to be a direct parent of all items in container")

    val sortable = container.map { elem =>
      val nodeId = extractNodeId(elem)
      val nodeIdx = graph.idToIdxOrThrow(nodeId)
      (elem, getChildEdgeOrThrow(graph, parentId, nodeIdx).data.ordering)
    }

    computeOrder(graph, parentId, sortable).map(_._1)
  }

  def getChildEdgeOrThrow(graph: Graph, parentId: NodeId, nodeIdx: Int): Edge.Child = {
    graph.parentEdgeIdx.foreachElement(nodeIdx) { edgeIdx =>
      val edge = graph.edges(edgeIdx).as[Edge.Child]
      if (edge.parentId == parentId) return edge
    }

    throw new Exception(s"Cannot order nodes. Node ${graph.nodes(nodeIdx)} is not a child of $parentId")
  }

  @inline private def computeOrder[T](graph: Graph, parentId: NodeId, container: Seq[(T, BigDecimal)]): Seq[(T, BigDecimal)] = container.sortWith(_._2 < _._2)
}
