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

  lazy val consistent: GraphChanges = copy(addEdges = addEdges -- delEdges)

  def involvedNodeIds: collection.Set[NodeId] =
    addNodes.map(_.id) ++
      addEdges.flatMap(e => e.sourceId :: e.targetId :: Nil) ++
      delEdges.flatMap(e => e.sourceId :: e.targetId :: Nil)

  private val allProps = addNodes :: addEdges :: delEdges :: Nil

  lazy val isEmpty: Boolean = allProps.forall(s => s.isEmpty)
  def nonEmpty: Boolean = !isEmpty
  lazy val size: Int = allProps.foldLeft(0)(_ + _.size)
}
object GraphChanges {

  def log(changes: GraphChanges, graph: Option[Graph] = None, premsg: String = ""): Unit = {
    import changes._
    val addNodeLookup = changes.addNodes.by(_.id)
    def id(nid: NodeId): String = {
      val str = (for {
        g <- graph
        node = addNodeLookup.getOrElse(nid, g.lookup.nodesById(nid))
      } yield node.str).fold("")(str => s"${"\""}$str${"\""}")
      s"$str[${ nid.toBase58.takeRight(3) }]"
    }

    scribe.info(s"${premsg}GraphChanges(")
    if(addNodes.nonEmpty) scribe.info(s"  addNodes: ${ addNodes.map(n => s"${ id(n.id) }:${ n.tpe }").mkString(" ") }")
    if(addEdges.nonEmpty) scribe.info(s"  addEdges: ${ addEdges.map(e => s"${ id(e.sourceId) }-${ e.data }->${ id(e.targetId) }").mkString(" ") }")
    if(delEdges.nonEmpty) scribe.info(s"  delEdges: ${ delEdges.map(e => s"${ id(e.sourceId) }-${ e.data }->${ id(e.targetId) }").mkString(" ") }")
    scribe.info(")")
  }

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
  def addNodeWithParent(node: Node, parentIds: Iterable[NodeId]) =
    GraphChanges(addNodes = Set(node), addEdges = parentIds.map(parentId => Edge.Parent(node.id, parentId))(breakOut))

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

  def newChannel(nodeId: NodeId, title: String, userId: UserId): GraphChanges = {
    val post = new Node.Content(
      nodeId,
      NodeData.PlainText(title),
      NodeMeta(accessLevel = NodeAccess.Level(AccessLevel.Restricted))
    )
    GraphChanges(addNodes = Set(post), addEdges = Set(Edge.Pinned(userId, nodeId)))
  }

  def undelete(nodeIds: Iterable[NodeId], parentIds: Iterable[NodeId]): GraphChanges = connect(Edge.Parent)(nodeIds, parentIds)
  def undelete(nodeId: NodeId, parentIds: Iterable[NodeId]): GraphChanges = undelete(nodeId :: Nil, parentIds)

  def undelete(nodeIds: Iterable[NodeId], graph: Graph): GraphChanges = {
    if(nodeIds.isEmpty) empty
    else nodeIds.foldLeft(GraphChanges.empty) { (changes, id) =>
      changes merge undelete(id, graph.parents(id))
    }
  }

  def delete(nodeIds: Iterable[NodeId], graph: Graph): GraphChanges = {
    if(nodeIds.isEmpty) empty
    else nodeIds.foldLeft(GraphChanges.empty) { (changes, id) =>
      changes merge delete(id, graph.parents(id))
    }
  }
  def delete(nodeId: NodeId, graph: Graph): GraphChanges = delete(nodeId :: Nil, graph)
  def delete(nodeIds: Iterable[NodeId], parentIds: Set[NodeId]): GraphChanges =
    nodeIds.foldLeft(empty)((acc, nextNode) => acc merge delete(nextNode, parentIds))
  def delete(nodeId: NodeId, parentIds: Iterable[NodeId]): GraphChanges = GraphChanges(
    addEdges = parentIds.map(
      parentId => Edge.Parent.delete(nodeId, parentId)
    )(breakOut)
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

  def moveInto(graph: Graph, subjectIds: Iterable[NodeId], targetIds: Iterable[NodeId]): GraphChanges =
    targetIds.foldLeft(GraphChanges.empty) { (changes, targetId) => changes merge GraphChanges.moveInto(graph, subjectIds, targetId) }
  def moveInto(graph: Graph, subject: NodeId, target: NodeId): GraphChanges = moveInto(graph, subject :: Nil, target)
  def moveInto(graph: Graph, subjects: Iterable[NodeId], target: NodeId): GraphChanges = {
    // TODO: only keep deepest parent in transitive chain
    val newParentships: collection.Set[Edge] = subjects.filterNot(_ == target).map(subject => Edge.Parent(subject, target))(breakOut) // avoid creating self-loops
    val cycleFreeSubjects: Iterable[NodeId] = subjects.filterNot(subject => subject == target || (graph.ancestors(target) contains subject)) // avoid self loops and cycles
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
