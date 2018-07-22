package wust.graph

import wust.graph.Edge.Parent
import wust.ids.EdgeData.DeletedParent
import wust.ids._

import scala.collection.breakOut

case class GraphChanges(
    addNodes: collection.Set[Node] = Set.empty,
    addEdges: collection.Set[Edge] = Set.empty,
    // we do not really need a connection for deleting (ConnectionId instead), but we want to revert it again.
    delEdges: collection.Set[Edge] = Set.empty
) {
  def withAuthor(userId: UserId, timestamp: EpochMilli = EpochMilli.now): GraphChanges =
    copy(
      addEdges = addEdges ++
        addNodes.map(node => Edge.Author(userId, EdgeData.Author(timestamp), node.id))
    )

  def merge(other: GraphChanges): GraphChanges = {
    val otherAddNodeIds = other.addNodes.map(_.id)
    GraphChanges.from(
      addNodes = addNodes ++ other.addNodes,
      addEdges = addEdges ++ other.addEdges,
      delEdges = delEdges -- other.addEdges ++ other.delEdges
      //FIXME: why was i here? inconsistent changes? .filter(c => !otherAddNodeIds(c.sourceId) && !otherAddNodeIds(c.targetId))
    )
  }

  def filter(p: NodeId => Boolean): GraphChanges =
    copy(
      addNodes = addNodes.filter(n => p(n.id))
    ).consistent

  private val swapDeletedParent: Edge => Edge = {
    case Edge.Parent(source, target) =>
      Edge.DeletedParent(source, EdgeData.DeletedParent(EpochMilli.now), target)
    case Edge.DeletedParent(source, data, target) => Edge.Parent(source, target)
    case other                                    => other
  }

  def revert = GraphChanges(
    addEdges = consistent.delEdges.map(swapDeletedParent),
    delEdges = consistent.addEdges.map(swapDeletedParent)
  )

  lazy val consistent = copy(addEdges = addEdges -- delEdges)

  def involvedNodeIds: collection.Set[NodeId] = addNodes.map(_.id)

  def involvedNodeIdsWithEdges: collection.Set[NodeId] =
    involvedNodeIds ++
      addEdges.flatMap(e => e.sourceId :: e.targetId :: Nil) ++
      delEdges.flatMap(e => e.sourceId :: e.targetId :: Nil)

  private val allProps = addNodes :: addEdges :: delEdges :: Nil

  lazy val isEmpty = allProps.forall(s => s.isEmpty)
  def nonEmpty = !isEmpty
  lazy val size = allProps.foldLeft(0)(_ + _.size)
}
object GraphChanges {
  def empty = GraphChanges()

  def from(
      addNodes: Iterable[Node] = Set.empty,
      addEdges: Iterable[Edge] = Set.empty,
      delEdges: Iterable[Edge] = Set.empty
  ) =
    GraphChanges(
      addNodes.toSet,
      addEdges.toSet,
      delEdges.toSet
    )

  def addNode(content: NodeData.Content) = GraphChanges(addNodes = Set(Node.Content(content)))
  def addNode(node: Node) = GraphChanges(addNodes = Set(node))
  def addNodeWithParent(node: Node, parentId: NodeId) =
    GraphChanges(addNodes = Set(node), addEdges = Set(Edge.Parent(node.id, parentId)))

  def addToParent(nodeIds: Iterable[NodeId], parentId: NodeId) = GraphChanges(
    addEdges = nodeIds.map { channelId =>
      Edge.Parent(channelId, parentId)
    }(breakOut)
  )

  def addToParents(nodeId: NodeId, parentIds: Iterable[NodeId]) = GraphChanges(
    addEdges = parentIds.map { parentId =>
      Edge.Parent(nodeId, parentId)
    }(breakOut)
  )

  def newGroup(nodeId: NodeId, title: String, channelNodeId: NodeId) = {
    val post = new Node.Content(
      nodeId,
      NodeData.PlainText(title),
      NodeMeta(accessLevel = NodeAccess.Level(AccessLevel.Restricted))
    )
    GraphChanges.addNodeWithParent(post, channelNodeId)
  }

  def delete(nodeIds: Iterable[NodeId], graph: Graph, page: Page): GraphChanges = {
    if(nodeIds.isEmpty) GraphChanges.empty
    else {
      val directParents = graph.parents(nodeIds.head).toSet
      val pageParents = page.parentIdSet
      val parentIds = directParents intersect pageParents
      delete(nodeIds, parentIds)
    }
  }
  def delete(node: Node, graph: Graph, page: Page): GraphChanges = delete(node.id, graph, page)
  def delete(nodeId: NodeId, graph: Graph, page: Page): GraphChanges = delete(nodeId :: Nil, graph, page)

  def delete(nodeIds: Iterable[NodeId], parentIds: Set[NodeId]): GraphChanges =
    nodeIds.foldLeft(GraphChanges.empty)((acc, nextNode) => acc merge delete(nextNode, parentIds))
  def delete(node: Node, parentIds: Set[NodeId]): GraphChanges = delete(node.id, parentIds)
  def delete(nodeId: NodeId, parentIds: Set[NodeId]): GraphChanges = GraphChanges(
    addEdges = parentIds.map(
      parentId => Edge.DeletedParent(nodeId, EdgeData.DeletedParent(EpochMilli.now), parentId)
    ),
    delEdges = parentIds.map(parentId => Edge.Parent(nodeId, parentId))
  )

  def connect(source: NodeId, content: EdgeData.Label, target: NodeId) =
    GraphChanges(addEdges = Set(Edge.Label(source, content, target)))
  def disconnect(source: NodeId, content: EdgeData.Label, target: NodeId) =
    GraphChanges(delEdges = Set(Edge.Label(source, content, target)))
  def connectParent(child: NodeId, parent: NodeId) =
    GraphChanges(addEdges = Set(Edge.Parent(child, parent)))
  def disconnectParent(child: NodeId, parent: NodeId) =
    GraphChanges(delEdges = Set(Edge.Parent(child, parent)))

  def moveInto(graph: Graph, subject: NodeId, target: NodeId) = {
    // TODO: only keep deepest parent in transitive chain
    val newContainments = Set[Edge](Edge.Parent(subject, target))
    val removeContainments: collection.Set[Edge] =
      if (graph.ancestors(target).toSet contains subject) { // creating cycle
        Set.empty // remove nothing, because in cycle
      } else { // no cycle
        (graph.parents(subject) map (Edge.Parent(subject, _): Edge)) - newContainments.head
      }
    GraphChanges(addEdges = newContainments, delEdges = removeContainments)
  }

  def tagWith(graph: Graph, subject: NodeId, tag: NodeId) = {
    GraphChanges.connectParent(subject, tag)
  }
}
