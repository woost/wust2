package wust.graph

import wust.ids._
import derive.derive

@derive(copyF)
case class GraphChanges(
  addPosts:        Set[Post]        = Set.empty,
  addConnections:  Set[Connection]  = Set.empty,
  addContainments: Set[Containment] = Set.empty,
  addOwnerships:   Set[Ownership]   = Set.empty,
  updatePosts:     Set[Post]        = Set.empty,
  delPosts:        Set[PostId]      = Set.empty,
  delConnections:  Set[Connection]  = Set.empty,
  delContainments: Set[Containment] = Set.empty,
  delOwnerships:   Set[Ownership]   = Set.empty,
  undeletePosts:   Set[PostId]      = Set.empty
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
    delOwnerships ++ other.delOwnerships,
    undeletePosts ++ other.undeletePosts
  )

  def -(other: GraphChanges) = GraphChanges(
    addPosts -- other.addPosts,
    addConnections -- other.addConnections,
    addContainments -- other.addContainments,
    addOwnerships -- other.addOwnerships,
    updatePosts -- other.updatePosts,
    delPosts -- other.delPosts,
    delConnections -- other.delConnections,
    delContainments -- other.delContainments,
    delOwnerships -- other.delOwnerships,
    undeletePosts -- other.undeletePosts
  )

  def filter(postIds: Set[PostId]) = copy(
    addPosts = addPosts.filter(p => postIds(p.id)),
    updatePosts = updatePosts.filter(p => postIds(p.id)),
    delPosts = delPosts.filter(postIds)
  ).consistent

  lazy val revert = {
    val addPostIds = addPosts.map(_.id)
    GraphChanges(
    Set.empty,
    delConnections.filter(c => !addPostIds(c.sourceId) && !addPostIds(c.targetId)),
    delContainments.filter(c => !addPostIds(c.parentId) && !addPostIds(c.childId)),
    delOwnerships.filter(c => !addPostIds(c.postId)),
    Set.empty, //TODO edit history
    addPosts.map(_.id) ++ undeletePosts,
    addConnections,
    addContainments,
    addOwnerships,
    delPosts
  )
  }

  lazy val consistent = GraphChanges(
    addPosts.filter(p => !delPosts(p.id) && !undeletePosts(p.id)),
    (addConnections -- delConnections).filter(c => !delPosts(c.sourceId) && !delPosts(c.targetId) && c.sourceId != c.targetId),
    (addContainments -- delContainments).filter(c => !delPosts(c.parentId) && !delPosts(c.childId) && c.parentId != c.childId),
    (addOwnerships -- delOwnerships).filter(o => !delPosts(o.postId)),
    updatePosts.filterNot(p => delPosts(p.id)),
    delPosts -- addPosts.map(_.id) -- undeletePosts,
    delConnections -- addConnections,
    delContainments -- addContainments,
    delOwnerships -- addOwnerships,
    undeletePosts,
  )

  lazy val isEmpty = addPosts.isEmpty && addConnections.isEmpty && addContainments.isEmpty && addOwnerships.isEmpty && updatePosts.isEmpty && delPosts.isEmpty && delConnections.isEmpty && delContainments.isEmpty && delOwnerships.isEmpty && undeletePosts.isEmpty

  lazy val size = addPosts.size + addConnections.size + addContainments.size + addOwnerships.size + updatePosts.size + delPosts.size + delConnections.size + delContainments.size + delOwnerships.size + undeletePosts.size
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
    delOwnerships:   Iterable[Ownership]   = Set.empty,
    undeletePosts:   Iterable[PostId]      = Set.empty
  ) = GraphChanges(addPosts.toSet, addConnections.toSet, addContainments.toSet, addOwnerships.toSet, updatePosts.toSet, delPosts.toSet, delConnections.toSet, delContainments.toSet, delOwnerships.toSet, undeletePosts.toSet)
}
