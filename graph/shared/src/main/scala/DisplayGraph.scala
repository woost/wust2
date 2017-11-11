package wust.graph

import wust.ids._

case class LocalConnection(sourceId: PostId, targetId: PostId)
case class LocalContainment(parentId: PostId, childId: PostId)
case class DisplayGraph(
  graph:                 Graph,
  redirectedConnections: Set[LocalConnection]  = Set.empty,
  collapsedContainments: Set[LocalContainment] = Set.empty
)
