package wust.graph

import wust.ids._

import scala.collection.breakOut

case class GraphChanges(
                         addNodes:        Set[Node]        = Set.empty,
                         addEdges:  Set[Edge]  = Set.empty,
                         updateNodes:     Set[Node]        = Set.empty,
                         delNodes:        Set[NodeId]      = Set.empty,
                         // we do not really need a connection for deleting (ConnectionId instead), but we want to revert it again.
                         delEdges:  Set[Edge]  = Set.empty
) {
  def withAuthor(userId: UserId, timestamp: EpochMilli = EpochMilli.now): GraphChanges =
    copy(addEdges = addEdges ++ (addNodes ++ updateNodes).map(node => Edge.Author(userId, EdgeData.Author(timestamp), node.id)))

  def merge(other: GraphChanges): GraphChanges = {
    val otherAddPosts = other.addNodes.map(_.id)
    GraphChanges.from(
      addNodes.filterNot(p => other.delNodes(p.id)) ++ other.addNodes,
      addEdges -- other.delEdges ++ other.addEdges,
      updateNodes.filterNot(p => other.delNodes(p.id)) ++ other.updateNodes,
      delNodes -- otherAddPosts ++ other.delNodes,
      (delEdges -- other.addEdges).filter(c => !otherAddPosts(c.sourceId) && !otherAddPosts(c.targetId)) ++ other.delEdges
    )
  }


  def filter(nodeIds: Set[NodeId]): GraphChanges = copy(
    addNodes = addNodes.filter(p => nodeIds(p.id)),
    updateNodes = updateNodes.filter(p => nodeIds(p.id)),
    delNodes = delNodes.filter(nodeIds)
  ).consistent

  def revert(deletedPostsById: collection.Map[NodeId,Node.Content]) = GraphChanges(
    //TODO: backend just undeletes on id clash when inserting into table. any changes we do here are just for our local state. here we also ignor undeletion on non Post.Content
    delNodes.flatMap(deletedPostsById.get _).map(post => post.copy(meta = post.meta.copy(deleted = DeletedDate.NotDeleted))),
    delEdges,
    Set.empty, //TODO edit history
    addNodes.map(_.id),
    addEdges -- delEdges
  )

  lazy val consistent = GraphChanges(
    addNodes.filterNot(p => delNodes(p.id)),
    (addEdges -- delEdges).filter(c => c.sourceId != c.targetId),
    updateNodes.filterNot(p => delNodes(p.id)),
    delNodes,
    delEdges
  )

  def involvedNodeIds: Set[NodeId] = addNodes.map(_.id) ++ updateNodes.map(_.id) ++ delNodes

  private val allProps = addNodes :: addEdges :: updateNodes :: delNodes :: delEdges :: Nil

  lazy val isEmpty = allProps.forall(s => s.isEmpty)
  def nonEmpty = !isEmpty
  lazy val size = allProps.foldLeft(0)(_ + _.size)
}
object GraphChanges {
  def empty = GraphChanges()

  def from(
            addPosts:        Iterable[Node]        = Set.empty,
            addConnections:  Iterable[Edge]  = Set.empty,
            updatePosts:     Iterable[Node]        = Set.empty,
            delPosts:        Iterable[NodeId]      = Set.empty,
            delConnections:  Iterable[Edge]  = Set.empty
  ) = GraphChanges(addPosts.toSet, addConnections.toSet, updatePosts.toSet, delPosts.toSet, delConnections.toSet)

  def addNode(content: NodeData.Content) = GraphChanges(addNodes = Set(Node.Content(content)))
  def addNode(post:Node) = GraphChanges(addNodes = Set(post))
  def addNodeWithParent(post:Node, parentId:NodeId) = GraphChanges(addNodes = Set(post), addEdges = Set(Edge.Parent(post.id, parentId)))

  def updatePost(post:Node) = GraphChanges(updateNodes = Set(post))

  def addToParent(nodeIds:Iterable[NodeId], parentId:NodeId) = GraphChanges(
    addEdges = nodeIds.map { channelId =>
      Edge.Parent(channelId, parentId)
    }(breakOut)
  )

  def delete(post:Node) = GraphChanges(delNodes = Set(post.id))
  def delete(nodeId:NodeId) = GraphChanges(delNodes = Set(nodeId))

  def connect(source:NodeId, content:EdgeData.Label, target:NodeId) = GraphChanges(addEdges = Set(Edge.Label(source, content, target)))
  def disconnect(source:NodeId, content: EdgeData.Label, target:NodeId) = GraphChanges(delEdges = Set(Edge.Label(source, content, target)))
  def connectParent(source:NodeId, target:NodeId) = GraphChanges(addEdges = Set(Edge.Parent(source, target)))
  def disconnectParent(source:NodeId, target:NodeId) = GraphChanges(delEdges = Set(Edge.Parent(source, target)))

  def moveInto(graph:Graph, subject:NodeId, target:NodeId) = {
    // TODO: only keep deepest parent in transitive chain
    val newContainments = Set[Edge](Edge.Parent(subject, target))
    val removeContainments:Set[Edge] = if (graph.ancestors(target).toSet contains subject) { // creating cycle
      Set.empty // remove nothing, because in cycle
    } else { // no cycle
      (graph.parents(subject) map (Edge.Parent(subject, _) : Edge)) - newContainments.head
    }
    GraphChanges(addEdges = newContainments, delEdges = removeContainments)
  }

  def tagWith(graph:Graph, subject:NodeId, tag:NodeId) = {
    GraphChanges.connectParent(subject, tag)
  }
}
