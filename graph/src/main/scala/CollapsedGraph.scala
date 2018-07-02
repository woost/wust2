package wust.graph

import wust.ids._

object CollapsedGraph {
  case class LocalConnection(sourceId: NodeId, content: EdgeData, targetId: NodeId)
}

case class CollapsedGraph(
    graph: Graph,
    redirectedConnections: collection.Set[CollapsedGraph.LocalConnection] = Set.empty,
    collapsedContainments: collection.Set[CollapsedGraph.LocalConnection] = Set.empty
)
