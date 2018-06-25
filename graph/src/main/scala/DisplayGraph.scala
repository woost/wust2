package wust.graph

import wust.ids._

case class LocalConnection(sourceId: NodeId, content:EdgeData, targetId: NodeId)
case class DisplayGraph(
  graph:                 Graph,
  redirectedConnections: collection.Set[LocalConnection]  = Set.empty,
  collapsedContainments: collection.Set[LocalConnection] = Set.empty
)
