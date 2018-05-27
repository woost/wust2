package wust.graph

import wust.ids._

import scala.collection.breakOut

case class GraphChanges(
  addPosts:        Set[Post]        = Set.empty,
  addConnections:  Set[Connection]  = Set.empty,
  updatePosts:     Set[Post]        = Set.empty,
  delPosts:        Set[PostId]      = Set.empty,
  // we do not really need a connection for deleting (ConnectionId instead), but we want to revert it again.
  delConnections:  Set[Connection]  = Set.empty
) {
  def merge(other: GraphChanges): GraphChanges = {
    val otherAddPosts = other.addPosts.map(_.id)
    GraphChanges(
      addPosts.filterNot(p => other.delPosts(p.id)) ++ other.addPosts,
      addConnections -- other.delConnections ++ other.addConnections,
      updatePosts.filterNot(p => other.delPosts(p.id)) ++ other.updatePosts,
      delPosts -- otherAddPosts ++ other.delPosts,
      (delConnections -- other.addConnections).filter(c => !otherAddPosts(c.sourceId) && !otherAddPosts(c.targetId)) ++ other.delConnections
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
    Set.empty, //TODO edit history
    addPosts.map(_.id),
    addConnections -- delConnections
  )

  lazy val consistent = GraphChanges(
    addPosts.filterNot(p => delPosts(p.id)),
    (addConnections -- delConnections).filter(c => c.sourceId != c.targetId),
    updatePosts.filterNot(p => delPosts(p.id)),
    delPosts,
    delConnections
  )

  def involvedPostIds: Set[PostId] = addPosts.map(_.id) ++ updatePosts.map(_.id) ++ delPosts

  private val allProps = addPosts :: addConnections :: updatePosts :: delPosts :: delConnections :: Nil

  lazy val isEmpty = allProps.forall(s => s.isEmpty)
  def nonEmpty = !isEmpty
  lazy val size = allProps.foldLeft(0)(_ + _.size)
}
object GraphChanges {
  def empty = GraphChanges()

  def from(
    addPosts:        Iterable[Post]        = Set.empty,
    addConnections:  Iterable[Connection]  = Set.empty,
    updatePosts:     Iterable[Post]        = Set.empty,
    delPosts:        Iterable[PostId]      = Set.empty,
    delConnections:  Iterable[Connection]  = Set.empty
  ) = GraphChanges(addPosts.toSet, addConnections.toSet, updatePosts.toSet, delPosts.toSet, delConnections.toSet)

  def addPost(content: PostContent, author:UserId) = GraphChanges(addPosts = Set(Post(content, author)))
  def addPost(post:Post) = GraphChanges(addPosts = Set(post))
  def addPostWithParent(post:Post, parentId:PostId) = GraphChanges(addPosts = Set(post), addConnections = Set(Connection(post.id, ConnectionContent.Parent, parentId)))

  def updatePost(post:Post) = GraphChanges(updatePosts = Set(post))

  def addToParent(postIds:Iterable[PostId], parentId:PostId) = GraphChanges(
    addConnections = postIds.map { channelId =>
      Connection(channelId, ConnectionContent.Parent, parentId)
    }(breakOut)
  )

  def delete(post:Post) = GraphChanges(delPosts = Set(post.id))
  def delete(postId:PostId) = GraphChanges(delPosts = Set(postId))

  def connect(source:PostId, content:ConnectionContent, target:PostId) = GraphChanges(addConnections = Set(Connection(source, content, target)))
  def disconnect(source:PostId, content: ConnectionContent, target:PostId) = GraphChanges(delConnections = Set(Connection(source, content, target)))

  def moveInto(graph:Graph, subject:PostId, target:PostId) = {
    // TODO: only keep deepest parent in transitive chain
    val newContainments = Set(Connection(subject, ConnectionContent.Parent, target))
    val removeContainments:Set[Connection] = if (graph.ancestors(target).toSet contains subject) { // creating cycle
      Set.empty // remove nothing, because in cycle
    } else { // no cycle
      (graph.parents(subject) map (Connection(subject, ConnectionContent.Parent, _))) - newContainments.head
    }
    GraphChanges(addConnections = newContainments, delConnections = removeContainments)
  }

  def tagWith(graph:Graph, subject:PostId, tag:PostId) = {
    GraphChanges.connect(subject, ConnectionContent.Parent, tag)
  }
}
