package wust.graph

import wust.ids._

case class LocalConnection(sourceId: PostId, label:Label, targetId: PostId)
case class DisplayGraph(
  graph:                 Graph,
  redirectedConnections: Set[LocalConnection]  = Set.empty,
  collapsedContainments: Set[LocalConnection] = Set.empty
)
