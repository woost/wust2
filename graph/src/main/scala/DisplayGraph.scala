package wust.graph

import wust.ids._

case class LocalConnection(sourceId: NodeId, content:EdgeData, targetId: NodeId)
case class DisplayGraph(
  graph:                 Graph,
  redirectedConnections: Set[LocalConnection]  = Set.empty,
  collapsedContainments: Set[LocalConnection] = Set.empty
)
