package wust.graph

import wust.ids._
import wust.util.collection.RichCollection

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
    GraphChanges(
      addNodes = addNodes.filter(n => p(n.id)),
      addEdges = addEdges.filter(e => p(e.sourceId) && p(e.targetId)),
      delEdges = delEdges.filter(e => p(e.sourceId) && p(e.targetId))
    )

  def filterCheck(p: NodeId => Boolean, checkIdsFromEdge: Edge => List[NodeId]): GraphChanges = {
    // for each edge we need to check a certain number of ids.
    // checkIdsFromEdge returns exactly the ids we need to have permissions for (checked via p)
    GraphChanges(
      addNodes = addNodes.filter(n => p(n.id)),
      addEdges = addEdges.filter(e => checkIdsFromEdge(e).forall(p)),
      delEdges = delEdges.filter(e => p(e.sourceId) && p(e.targetId))
    )
  }

  lazy val consistent: GraphChanges = copy(addEdges = addEdges -- delEdges)

  def involvedNodeIds: collection.Set[NodeId] =
    addNodes.map(_.id) ++
      addEdges.flatMap(e => e.sourceId :: e.targetId :: Nil) ++
      delEdges.flatMap(e => e.sourceId :: e.targetId :: Nil)

  private val allProps = addNodes :: addEdges :: delEdges :: Nil

  lazy val isEmpty: Boolean = allProps.forall(s => s.isEmpty)
  def nonEmpty: Boolean = !isEmpty
  lazy val size: Int = allProps.foldLeft(0)(_ + _.size)

  override def toString = toPrettyString()

  def toPrettyString(graph:Graph = Graph.empty) = {
    // the graph can provide additional information about the edges

    val addNodeLookup = addNodes.by(_.id)

    def id(nid: NodeId): String = {
      val str = (
        for {
          node <- addNodeLookup.get(nid).orElse(graph.lookup.nodesByIdGet(nid))
        } yield node.str
      ).fold("")(str => s"${ "\"" }$str${ "\"" }")
      s"[${ nid.shortHumanReadable }]$str"
    }

    val sb = new StringBuilder
    sb ++= s"GraphChanges(\n"
    if(addNodes.nonEmpty) sb ++= s"  addNodes: ${ addNodes.map(n => s"${ id(n.id) }:${ n.tpe }/${n.role}  ${n.id.toBase58}  ${n.id.toUuid}").mkString("\n            ") }\n"
    if(addEdges.nonEmpty) sb ++= s"  addEdges: ${ addEdges.map(e => s"${ id(e.sourceId) } -${ e.data }-> ${ id(e.targetId) }").mkString("\n            ") }\n"
    if(delEdges.nonEmpty) sb ++= s"  delEdges: ${ delEdges.map(e => s"${ id(e.sourceId) } -${ e.data }-> ${ id(e.targetId) }").mkString("\n            ") }\n"
    sb ++= ")"

    sb.result()
  }
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

  def addMarkdownMessage(string: String): GraphChanges = addNode(Node.MarkdownMessage(string))
  def addMarkdownTask(string: String): GraphChanges = addNode(Node.MarkdownTask(string))

  def addNode(content: NodeData.Content, role: NodeRole) = GraphChanges(addNodes = Set(Node.Content(content, role)))
  def addNode(node: Node) = GraphChanges(addNodes = Set(node))
  def addNodeWithParent(node: Node, parentId: NodeId) =
    GraphChanges(addNodes = Set(node), addEdges = Set(Edge.Parent(node.id, parentId)))
  def addNodeWithParent(node: Node, parentIds: Iterable[NodeId]) =
    GraphChanges(addNodes = Set(node), addEdges = parentIds.map(parentId => Edge.Parent(node.id, parentId))(breakOut))
  def addNodeWithDeletedParent(node: Node, parentIds: Iterable[NodeId], deletedAt: EpochMilli) =
    GraphChanges(addNodes = Set(node), addEdges = parentIds.map(parentId => Edge.Parent(node.id, EdgeData.Parent(Some(deletedAt), None), parentId))(breakOut))

  def addToParent(nodeId: NodeId, parentId: NodeId): GraphChanges = addToParent(List(nodeId), parentId)
  def addToParent(nodeIds: Iterable[NodeId], parentId: NodeId): GraphChanges= GraphChanges(
    addEdges = nodeIds.map { channelId =>
      Edge.Parent(channelId, parentId)
    }(breakOut)
  )

  def addToParents(nodeId: NodeId, parentIds: Iterable[NodeId]): GraphChanges = GraphChanges(
    addEdges = parentIds.map { parentId =>
      Edge.Parent(nodeId, parentId)
    }(breakOut)
  )

  def newChannel(nodeId: NodeId, userId: UserId, title: String = "Untitled Workspace"): GraphChanges = {
    val post = new Node.Content(
      nodeId,
      NodeData.PlainText(title),
      NodeRole.Message, //TODO: something different?
      NodeMeta(accessLevel = NodeAccess.Level(AccessLevel.Restricted))
    )
    GraphChanges(addNodes = Set(post), addEdges = Set(Edge.Pinned(userId, nodeId), Edge.Notify(nodeId, userId)))
  }

  def undelete(nodeIds: Iterable[NodeId], parentIds: Iterable[NodeId]): GraphChanges = connect(Edge.Parent)(nodeIds, parentIds)
  def undelete(nodeId: NodeId, parentIds: Iterable[NodeId]): GraphChanges = undelete(nodeId :: Nil, parentIds)

  def delete(nodeIds: Iterable[NodeId], parentIds: Set[NodeId]): GraphChanges =
    nodeIds.foldLeft(empty)((acc, nextNode) => acc merge delete(nextNode, parentIds))
  def delete(nodeId: NodeId, parentIds: Iterable[NodeId], deletedAt: EpochMilli = EpochMilli.now): GraphChanges = GraphChanges(
    addEdges = parentIds.map(
      parentId => Edge.Parent.delete(nodeId, parentId, deletedAt)
    )(breakOut)
  )
  def delete(nodeId: NodeId, parentId: NodeId): GraphChanges = GraphChanges(
    addEdges = Set(Edge.Parent.delete(nodeId, parentId))
  )

  class ConnectFactory[SOURCEID, TARGETID, EDGE <: Edge](edge: (SOURCEID, TARGETID) => EDGE, toGraphChanges: collection.Set[EDGE] => GraphChanges) {
    def apply(sourceId: SOURCEID, targetId: TARGETID): GraphChanges =
      if(sourceId != targetId) toGraphChanges(Set(edge(sourceId, targetId))) else empty
    def apply(sourceId: SOURCEID, targetIds: Iterable[TARGETID]): GraphChanges =
      toGraphChanges(targetIds.collect { case targetId if targetId != sourceId => edge(sourceId, targetId) }(breakOut))
    def apply(sourceIds: Iterable[SOURCEID], targetId: TARGETID): GraphChanges
    =
      toGraphChanges(sourceIds.collect { case sourceId if sourceId != targetId => edge(sourceId, targetId) }(breakOut))
    def apply(sourceIds: Iterable[SOURCEID], targetIds: Iterable[TARGETID]): GraphChanges
    =
      toGraphChanges(sourceIds.flatMap(sourceId => targetIds.collect { case targetId if targetId != sourceId => edge(sourceId, targetId) })(breakOut))
  }

  class ConnectFactoryWithData[SOURCE, TARGET, DATA, EDGE <: Edge](edge: (SOURCE, DATA, TARGET) => EDGE, toGraphChanges: collection.Set[EDGE] => GraphChanges) {
    def apply(sourceId: SOURCE, data: DATA, targetId: TARGET): GraphChanges =
      if(sourceId != targetId) toGraphChanges(Set(edge(sourceId, data, targetId))) else empty
    def apply(sourceId: SOURCE, data: DATA, targetIds: Iterable[TARGET]): GraphChanges =
      toGraphChanges(targetIds.collect { case targetId if targetId != sourceId => edge(sourceId, data, targetId) }(breakOut))
    def apply(sourceIds: Iterable[SOURCE], data: DATA, targetId: TARGET): GraphChanges
    =
      toGraphChanges(sourceIds.collect { case sourceId if sourceId != targetId => edge(sourceId, data, targetId) }(breakOut))
    def apply(sourceIds: Iterable[SOURCE], data: DATA, targetIds: Iterable[TARGET]): GraphChanges
    =
      toGraphChanges(sourceIds.flatMap(sourceId => targetIds.collect { case targetId if targetId != sourceId => edge(sourceId, data, targetId) })(breakOut))
  }

  def connect[SOURCE <: NodeId, TARGET <: NodeId, EDGE <: Edge](edge: (SOURCE, TARGET) => EDGE) = new ConnectFactory(edge, (edges: collection.Set[Edge]) => GraphChanges(addEdges = edges))
  def connect[SOURCE <: NodeId, TARGET <: NodeId, DATA <: EdgeData, EDGE <: Edge](edge: (SOURCE, DATA, TARGET) => EDGE) = new ConnectFactoryWithData(edge, (edges: collection.Set[Edge]) => GraphChanges(addEdges = edges))
  def disconnect[SOURCE <: NodeId, TARGET <: NodeId, EDGE <: Edge](edge: (SOURCE, TARGET) => EDGE) = new ConnectFactory(edge, (edges: collection.Set[Edge]) => GraphChanges(delEdges = edges))
  def disconnect[SOURCE <: NodeId, TARGET <: NodeId, DATA <: EdgeData, EDGE <: Edge](edge: (SOURCE, DATA, TARGET) => EDGE) = new ConnectFactoryWithData(edge, (edges: collection.Set[Edge]) => GraphChanges(delEdges = edges))

  def changeTarget[SOURCE <: NodeId, TARGET <: NodeId, EDGE <: Edge](edge: (SOURCE, TARGET) => EDGE)(sourceIds: Iterable[SOURCE], oldTargetIds: Iterable[TARGET], newTargetIds: Iterable[TARGET]): GraphChanges = {
    val disconnect: GraphChanges = GraphChanges.disconnect(edge)(sourceIds, oldTargetIds)
    val connect: GraphChanges = GraphChanges.connect(edge)(sourceIds, newTargetIds)
    disconnect merge connect
  }

  def moveInto(graph: Graph, subjectIds: Iterable[NodeId], newParentIds: Iterable[NodeId]): GraphChanges =
    newParentIds.foldLeft(GraphChanges.empty) { (changes, targetId) => changes merge GraphChanges.moveInto(graph, subjectIds, targetId) }
  def moveInto(graph: Graph, subjectId: NodeId, newParentId: NodeId): GraphChanges = moveInto(graph, subjectId :: Nil, newParentId)
  def moveInto(graph: Graph, subjectIds: Iterable[NodeId], newParentId: NodeId): GraphChanges = {
    // TODO: only keep deepest parent in transitive chain
    val newParentships: collection.Set[Edge] = subjectIds
      .filterNot(_ == newParentId) // avoid creating self-loops
      .map { subjectId =>
        // if subject was not deleted in one of its parents => keep it
        // if it was deleted, take the latest deletion date
        val subjectIdx = graph.idToIdx(subjectId)
        val deletedAt = if(subjectIdx == -1) None else graph.combinedDeletedAt(subjectIdx)

        Edge.Parent(subjectId, EdgeData.Parent(deletedAt, None), newParentId)
      }(breakOut)

    val cycleFreeSubjects: Iterable[NodeId] = subjectIds.filterNot(subject => subject == newParentId || (graph.ancestors(newParentId) contains subject)) // avoid self loops and cycles
    val removeParentships = ((
      for {
        subject <- cycleFreeSubjects
        parent <- graph.parents(subject)
      } yield Edge.Parent(subject, parent)
      ) (breakOut): collection.Set[Edge]
      ) -- newParentships

    GraphChanges(addEdges = newParentships, delEdges = removeParentships)
  }
}
