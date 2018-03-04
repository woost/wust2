package wust.graph

import wust.ids._

case class GraphChanges(
  addPosts:        Set[Post]        = Set.empty,
  addConnections:  Set[Connection]  = Set.empty,
  addOwnerships:   Set[Ownership]   = Set.empty,
  updatePosts:     Set[Post]        = Set.empty,
  delPosts:        Set[PostId]      = Set.empty,
  delConnections:  Set[Connection]  = Set.empty,
  delOwnerships:   Set[Ownership]   = Set.empty
) {
  def merge(other: GraphChanges): GraphChanges = {
    val otherAddPosts = other.addPosts.map(_.id)
    GraphChanges(
      addPosts.filterNot(p => other.delPosts(p.id)) ++ other.addPosts,
      addConnections -- other.delConnections ++ other.addConnections,
      addOwnerships -- other.delOwnerships ++ other.addOwnerships,
      updatePosts.filterNot(p => other.delPosts(p.id)) ++ other.updatePosts,
      delPosts -- otherAddPosts ++ other.delPosts,
      (delConnections -- other.addConnections).filter(c => !otherAddPosts(c.sourceId) && !otherAddPosts(c.targetId)) ++ other.delConnections,
      (delOwnerships -- other.addOwnerships).filter(o => !otherAddPosts(o.postId)) ++ other.delOwnerships
    )
  }

  def filter(postIds: Set[PostId]): GraphChanges = copy(
    addPosts = addPosts.filter(p => postIds(p.id)),
    updatePosts = updatePosts.filter(p => postIds(p.id)),
    delPosts = delPosts.filter(postIds)
  ).consistent

  def revert(deletedPostsById: collection.Map[PostId,Post]) = GraphChanges(
    delPosts.flatMap(deletedPostsById.get _),
    delConnections,
    delOwnerships,
    Set.empty, //TODO edit history
    addPosts.map(_.id),
    addConnections -- delConnections,
    addOwnerships -- delOwnerships
  )

  lazy val consistent = GraphChanges(
    addPosts.filterNot(p => delPosts(p.id)),
    (addConnections -- delConnections).filter(c => c.sourceId != c.targetId),
    addOwnerships -- delOwnerships,
    updatePosts.filterNot(p => delPosts(p.id)),
    delPosts,
    delConnections,
    delOwnerships
  )

  private val allProps = addPosts :: addConnections :: addOwnerships :: updatePosts :: delPosts :: delConnections :: delOwnerships :: Nil

  lazy val isEmpty = allProps.forall(s => s.isEmpty)
  def nonEmpty = !isEmpty
  lazy val size = allProps.foldLeft(0)(_ + _.size)
}
object GraphChanges {
  def empty = GraphChanges()

  def from(
    addPosts:        Iterable[Post]        = Set.empty,
    addConnections:  Iterable[Connection]  = Set.empty,
    addOwnerships:   Iterable[Ownership]   = Set.empty,
    updatePosts:     Iterable[Post]        = Set.empty,
    delPosts:        Iterable[PostId]      = Set.empty,
    delConnections:  Iterable[Connection]  = Set.empty,
    delOwnerships:   Iterable[Ownership]   = Set.empty
  ) = GraphChanges(addPosts.toSet, addConnections.toSet, addOwnerships.toSet, updatePosts.toSet, delPosts.toSet, delConnections.toSet, delOwnerships.toSet)

  def addPost(content:String, author:UserId) = GraphChanges(addPosts = Set(Post(content, author)))
  def addPost(post:Post) = GraphChanges(addPosts = Set(post))

  def connect(source:PostId, label:Label, target:PostId) = GraphChanges(addConnections = Set(Connection(source, label, target)))

  def moveInto(graph:Graph, subject:PostId, target:PostId) = {
    // TODO: only keep deepest parent in transitive chain
    val newContainments = Set(Connection(subject, Label.parent, target))
    val removeContainments:Set[Connection] = if (graph.ancestors(target).toSet contains subject) { // creating cycle
      Set.empty // remove nothing, because in cycle
    } else { // no cycle
      (graph.parents(subject) map (Connection(subject, Label.parent, _))) - newContainments.head
    }
    GraphChanges(addConnections = newContainments, delConnections = removeContainments)
  }

  def tagWith(graph:Graph, subject:PostId, tag:PostId) = {
    GraphChanges.connect(subject, Label.parent, tag)
  }
}
