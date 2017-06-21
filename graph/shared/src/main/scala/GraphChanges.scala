package wust.graph

import wust.ids._
import derive.derive

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
) {
  def merge(other: GraphChanges) = {
    val otherAddPosts = other.addPosts.map(_.id)
    GraphChanges(
      addPosts.filterNot(p => other.delPosts(p.id)) ++ other.addPosts,
      addConnections -- other.delConnections ++ other.addConnections,
      addContainments -- other.delContainments ++ other.addContainments,
      addOwnerships -- other.delOwnerships ++ other.addOwnerships,
      updatePosts.filterNot(p => other.delPosts(p.id)) ++ other.updatePosts,
      delPosts -- otherAddPosts ++ other.delPosts,
      (delConnections -- other.addConnections).filter(c => !otherAddPosts(c.sourceId) && !otherAddPosts(c.targetId)) ++ other.delConnections,
      (delContainments -- other.delContainments).filter(c => !otherAddPosts(c.parentId) && !otherAddPosts(c.childId)) ++ other.delContainments,
      (delOwnerships -- other.addOwnerships).filter(o => !otherAddPosts(o.postId)) ++ other.delOwnerships,
    )
  }

  def filter(postIds: Set[PostId]) = copy(
    addPosts = addPosts.filter(p => postIds(p.id)),
    updatePosts = updatePosts.filter(p => postIds(p.id)),
    delPosts = delPosts.filter(postIds)
  ).consistent

  def revert(deletedPostsById: collection.Map[PostId,Post]) = GraphChanges(
    delPosts.flatMap(deletedPostsById.get _),
    delConnections,
    delContainments,
    delOwnerships,
    Set.empty, //TODO edit history
    addPosts.map(_.id),
    addConnections -- delConnections,
    addContainments -- delContainments,
    addOwnerships -- delOwnerships,
  )

  lazy val consistent = GraphChanges(
    addPosts,
    (addConnections -- delConnections).filter(c => c.sourceId != c.targetId),
    (addContainments -- delContainments).filter(c => c.parentId != c.childId),
    addOwnerships -- delOwnerships,
    updatePosts,
    delPosts,
    delConnections,
    delContainments,
    delOwnerships
  )

  def nonEmpty = !isEmpty
  lazy val isEmpty = addPosts.isEmpty && addConnections.isEmpty && addContainments.isEmpty && addOwnerships.isEmpty && updatePosts.isEmpty && delPosts.isEmpty && delConnections.isEmpty && delContainments.isEmpty && delOwnerships.isEmpty

  lazy val size = addPosts.size + addConnections.size + addContainments.size + addOwnerships.size + updatePosts.size + delPosts.size + delConnections.size + delContainments.size + delOwnerships.size
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
