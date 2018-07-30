package wust.graph

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
    case Edge.DeletedParent(source, _, target) => Edge.Parent(source, target)
    case other                                    => other
  }

  def revert = GraphChanges(
    addEdges = consistent.delEdges.map(swapDeletedParent),
    delEdges = consistent.addEdges.map(swapDeletedParent)
  )

  lazy val consistent: GraphChanges = copy(addEdges = addEdges -- delEdges)

  def involvedNodeIds: collection.Set[NodeId] = addNodes.map(_.id)

  def involvedNodeIdsWithEdges: collection.Set[NodeId] =
    involvedNodeIds ++
      addEdges.flatMap(e => e.sourceId :: e.targetId :: Nil) ++
      delEdges.flatMap(e => e.sourceId :: e.targetId :: Nil)

  private val allProps = addNodes :: addEdges :: delEdges :: Nil

  lazy val isEmpty: Boolean = allProps.forall(s => s.isEmpty)
  def nonEmpty: Boolean = !isEmpty
  lazy val size: Int = allProps.foldLeft(0)(_ + _.size)
}
object GraphChanges {
  val empty = new GraphChanges()

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

  def newChannel(nodeId: NodeId, title: String, channelNodeId: NodeId): GraphChanges = {
    val post = new Node.Content(
      nodeId,
      NodeData.PlainText(title),
      NodeMeta(accessLevel = NodeAccess.Level(AccessLevel.Restricted))
    )
    GraphChanges.addNodeWithParent(post, channelNodeId)
  }

  def delete(nodeIds: Iterable[NodeId], graph: Graph, page: Page): GraphChanges = {
    if(nodeIds.isEmpty) empty
    else {
      val directParents = graph.parents(nodeIds.head).toSet
      val pageParents = page.parentIdSet
      val parentIds = directParents ++ pageParents
      delete(nodeIds, parentIds)
    }
  }
  def delete(node: Node, graph: Graph, page: Page): GraphChanges = delete(node.id, graph, page)
  def delete(nodeId: NodeId, graph: Graph, page: Page): GraphChanges = delete(nodeId :: Nil, graph, page)

  def delete(nodeIds: Iterable[NodeId], parentIds: Set[NodeId]): GraphChanges =
    nodeIds.foldLeft(empty)((acc, nextNode) => acc merge delete(nextNode, parentIds))
  def delete(node: Node, parentIds: Set[NodeId]): GraphChanges = delete(node.id, parentIds)
  def delete(nodeId: NodeId, parentIds: Set[NodeId]): GraphChanges = GraphChanges(
    addEdges = parentIds.map(
      parentId => Edge.DeletedParent(nodeId, EdgeData.DeletedParent(EpochMilli.now), parentId)
    ),
    delEdges = parentIds.map(parentId => Edge.Parent(nodeId, parentId))
  )

  class ConnectFactory[SOURCEID, TARGETID, EDGE <: Edge](edge:(SOURCEID,TARGETID) => EDGE, toGraphChanges:collection.Set[EDGE] => GraphChanges) {
    def apply(sourceId: SOURCEID, targetId: TARGETID): GraphChanges = 
      if(sourceId != targetId) toGraphChanges(Set(edge(sourceId, targetId))) else empty
    def apply(sourceId: SOURCEID, targetIds: Iterable[TARGETID]): GraphChanges =
      toGraphChanges(targetIds.collect{case targetId if targetId != sourceId => edge(sourceId, targetId)}(breakOut))
    def apply(sourceIds: Iterable[SOURCEID], targetId: TARGETID): GraphChanges =
      toGraphChanges(sourceIds.collect{case sourceId if sourceId != targetId => edge(sourceId, targetId)}(breakOut))
    def apply(sourceIds: Iterable[SOURCEID], targetIds: Iterable[TARGETID]): GraphChanges =
      toGraphChanges(sourceIds.flatMap(sourceId => targetIds.collect{case targetId if targetId != sourceId => edge(sourceId, targetId)})(breakOut))
  }

  class ConnectFactoryWithData[SOURCE, TARGET, DATA, EDGE <: Edge](edge:(SOURCE,DATA,TARGET) => EDGE, toGraphChanges:collection.Set[EDGE] => GraphChanges) {
    def apply(sourceId: SOURCE, data:DATA, targetId: TARGET): GraphChanges = 
      if(sourceId != targetId) toGraphChanges(Set(edge(sourceId, data, targetId))) else empty
    def apply(sourceId: SOURCE, data:DATA, targetIds: Iterable[TARGET]): GraphChanges =
      toGraphChanges(targetIds.collect{case targetId if targetId != sourceId => edge(sourceId, data, targetId)}(breakOut))
    def apply(sourceIds: Iterable[SOURCE], data:DATA, targetId: TARGET): GraphChanges =
      toGraphChanges(sourceIds.collect{case sourceId if sourceId != targetId => edge(sourceId, data, targetId)}(breakOut))
    def apply(sourceIds: Iterable[SOURCE], data:DATA, targetIds: Iterable[TARGET]): GraphChanges =
      toGraphChanges(sourceIds.flatMap(sourceId => targetIds.collect{case targetId if targetId != sourceId => edge(sourceId, data, targetId)})(breakOut))
  }

  def connect[SOURCE, TARGET, EDGE <: Edge](edge:(SOURCE,TARGET) => EDGE) = new ConnectFactory(edge, (edges:collection.Set[Edge]) => GraphChanges(addEdges = edges))
  def connect[SOURCE, TARGET, DATA, EDGE <: Edge](edge:(SOURCE,DATA,TARGET) => EDGE) = new ConnectFactoryWithData(edge, (edges:collection.Set[Edge]) => GraphChanges(addEdges = edges))
  def disconnect[SOURCE, TARGET, EDGE <: Edge](edge:(SOURCE,TARGET) => EDGE) = new ConnectFactory(edge, (edges:collection.Set[Edge]) => GraphChanges(delEdges = edges))
  def disconnect[SOURCE, TARGET, DATA, EDGE <: Edge](edge:(SOURCE,DATA,TARGET) => EDGE) = new ConnectFactoryWithData(edge, (edges:collection.Set[Edge]) => GraphChanges(delEdges = edges))

  def moveInto(graph: Graph, subject: NodeId, target: NodeId): GraphChanges = moveInto(graph, subject :: Nil, target)
  def moveInto(graph: Graph, subjects: Iterable[NodeId], target: NodeId): GraphChanges = {
    // TODO: only keep deepest parent in transitive chain
    val newParentships:Set[Edge] = subjects.map(subject => Edge.Parent(subject, target))(breakOut)
    val cycleFreeSubjects: Iterable[NodeId] = subjects.filterNot(subject => subject == target || (graph.ancestors(target) contains subject)) // avoid self loops and cycles
    val removeParentships: collection.Set[Edge] = (cycleFreeSubjects.flatMap(subject =>
        graph.parents(subject) map (Edge.Parent(subject, _): Edge)
    )(breakOut):collection.Set[Edge]) - newParentships.head

    GraphChanges(addEdges = newParentships, delEdges = removeParentships)
  }
}
