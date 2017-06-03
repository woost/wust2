package wust.graph

import wust.ids._

case class GraphChanges(
  addPosts:        Set[Post]        = Set.empty,
  addConnections:  Set[Connection]  = Set.empty,
  addContainments: Set[Containment] = Set.empty,
  addOwnerships:   Set[Ownership]   = Set.empty,
  updatePosts:     Set[Post]        = Set.empty,
  delPosts:        Set[PostId]      = Set.empty,
  delConnections:  Set[Connection]  = Set.empty,
  delContainments: Set[Containment] = Set.empty,
  delOwnerships:   Set[Ownership]   = Set.empty
) {
  def +(other: GraphChanges) = GraphChanges(
    addPosts ++ other.addPosts,
    addConnections ++ other.addConnections,
    addContainments ++ other.addContainments,
    addOwnerships ++ other.addOwnerships,
    updatePosts ++ other.updatePosts,
    delPosts ++ other.delPosts,
    delConnections ++ other.delConnections,
    delContainments ++ other.delContainments,
    delOwnerships ++ other.delOwnerships
  )

  lazy val consistent = GraphChanges(
    addPosts.filterNot(p => delPosts(p.id)),
    (addConnections -- delConnections).filterNot(c => delPosts(c.sourceId) || delPosts(c.targetId)),
    (addContainments -- delContainments).filterNot(c => delPosts(c.parentId) || delPosts(c.childId)),
    (addOwnerships -- delOwnerships).filterNot(o => delPosts(o.postId)),
    updatePosts,
    delPosts -- addPosts.map(_.id),
    delConnections -- addConnections,
    delContainments -- addContainments,
    delOwnerships -- addOwnerships
  )

  lazy val isEmpty = addPosts.isEmpty && addConnections.isEmpty && addContainments.isEmpty && addOwnerships.isEmpty && updatePosts.isEmpty && delPosts.isEmpty && delConnections.isEmpty && delContainments.isEmpty && delOwnerships.isEmpty
}
object GraphChanges {
  def empty = GraphChanges()

  def from(
    addPosts:        Iterable[Post]        = Set.empty,
    addConnections:  Iterable[Connection]  = Set.empty,
    addContainments: Iterable[Containment] = Set.empty,
    addOwnerships:   Iterable[Ownership]   = Set.empty,
    updatePosts:     Iterable[Post]        = Set.empty,
    delPosts:        Iterable[PostId]      = Set.empty,
    delConnections:  Iterable[Connection]  = Set.empty,
    delContainments: Iterable[Containment] = Set.empty,
    delOwnerships:   Iterable[Ownership]   = Set.empty
  ) = GraphChanges(addPosts.toSet, addConnections.toSet, addContainments.toSet, addOwnerships.toSet, updatePosts.toSet, delPosts.toSet, delConnections.toSet, delContainments.toSet, delOwnerships.toSet)
}
