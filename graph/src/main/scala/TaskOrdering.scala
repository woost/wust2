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

    val parentIdx = graph.idToIdxOrThrow(parentId)

    val sortable = container.map { elem =>
      val nodeId = extractNodeId(elem)
      val nodeIdx = graph.idToIdxOrThrow(nodeId)
      (elem, getChildEdgeOrThrow(graph, parentIdx = parentIdx, childIdx = nodeIdx).data.ordering)
    }

    sortable.sortWith(_._2 < _._2).map(_._1)
  }

  def getChildEdgeOrThrow(graph: Graph, parentIdx: Int, childIdx: Int): Edge.Child = {
    graph.parentEdgeIdx.foreachElement(childIdx) { edgeIdx =>
      if (graph.edgesIdx.a(edgeIdx) == parentIdx) return graph.edges(edgeIdx).as[Edge.Child]
    }

    throw new Exception(s"Cannot order nodes. Node ${graph.nodes(childIdx)} is not a child of ${graph.nodes(parentIdx)}")
  }
}
