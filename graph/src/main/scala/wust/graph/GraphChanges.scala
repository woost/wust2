package wust.graph

import wust.ids._
import wust.util.algorithm.dfs
import wust.util.collection.{HashSetFromArray, RichCollection}
import wust.util.macros.InlineList

import scala.collection.{breakOut, mutable}
import scala.reflect.ClassTag

final case class GraphChanges(
  addNodes: Array[Node] = Array.empty,
  addEdges: Array[Edge] = Array.empty,
  // we do not really need a connection for deleting (ConnectionId instead), but we want to revert it again.
  delEdges: Array[Edge] = Array.empty
) {

  def withAuthor(userId: UserId, timestamp: EpochMilli = EpochMilli.now): GraphChanges = {
    if (addNodes.isEmpty) this
    else {
      val existingAuthors: mutable.HashSet[NodeId] = addEdges.collect { case edge: Edge.Author => edge.nodeId }(breakOut)
      val allAddEdges = Array.newBuilder[Edge]
      allAddEdges ++= addEdges
      addNodes.foreach { node =>
        allAddEdges += Edge.Read(node.id, EdgeData.Read(timestamp), userId)
        if (!existingAuthors(node.id)) {
          allAddEdges += Edge.Author(node.id, EdgeData.Author(timestamp), userId)
        }
      }
      copy(addEdges = allAddEdges.result)
    }
  }

  def merge(other: GraphChanges): GraphChanges = {
    val delEdgesBuilder = Array.newBuilder[Edge]
    val otherAddEdgesSet = HashSetFromArray(other.addEdges)
    delEdges.foreach { delEdge =>
      if (!otherAddEdgesSet(delEdge)) delEdgesBuilder += delEdge
    }
    delEdgesBuilder ++= other.delEdges

    GraphChanges.from(
      addNodes = addNodes ++ other.addNodes,
      addEdges = addEdges ++ other.addEdges,
      delEdges = delEdgesBuilder.result()
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
      delEdges = delEdges.filter(e => checkIdsFromEdge(e).forall(p))
    )
  }

  lazy val consistent: GraphChanges = {
    val addNodesBuilder = Array.newBuilder[Node]
    val addEdgesBuilder = Array.newBuilder[Edge]
    val delEdgesBuilder = Array.newBuilder[Edge]
    val addNodesSet = new mutable.HashSet[NodeId]()
    val addEdgesSet = new mutable.HashSet[EdgeEquality.Unique]()
    val delEdgesSet = new mutable.HashSet[EdgeEquality.Unique]()
    addNodes.reverseIterator.foreach { addNode =>
      val seen = addNodesSet.contains(addNode.id)
      if (!seen) {
        addNodesBuilder += addNode
        addNodesSet += addNode.id
      }
    }
    delEdges.reverseIterator.foreach { delEdge =>
      val seen = EdgeEquality.Unique(delEdge).fold(false) { edgeEquality =>
        val r = delEdgesSet.contains(edgeEquality)
        if (!r) delEdgesSet += edgeEquality
        r
      }
      if (!seen) {
        delEdgesBuilder += delEdge
      }
    }
    addEdges.reverseIterator.foreach { addEdge =>
      val seen = EdgeEquality.Unique(addEdge).fold(false) { edgeEquality =>
        val r = addEdgesSet.contains(edgeEquality) || delEdgesSet.contains(edgeEquality)
        if (!r) addEdgesSet += edgeEquality
        r
      }
      if (!seen) {
        addEdgesBuilder += addEdge
      }
    }

    copy(
      addNodes = addNodesBuilder.result,
      addEdges = addEdgesBuilder.result(),
      delEdges = delEdgesBuilder.result()
    )
  }

  lazy val involvedNodeIds: collection.Set[NodeId] = {
    val involved = new mutable.HashSet[NodeId]()
    addNodes.foreach(node => involved += node.id)
    addEdges.foreach { edge =>
      involved += edge.sourceId
      involved += edge.targetId
    }
    delEdges.foreach { edge =>
      involved += edge.sourceId
      involved += edge.targetId
    }
    involved
  }

  lazy val size: Int = InlineList.foldLeft(addNodes, addEdges, delEdges)(0)(_ + _.length)
  lazy val isEmpty: Boolean = InlineList.forall(addNodes, addEdges, delEdges)(_.isEmpty)
  @inline def nonEmpty: Boolean = !isEmpty

  override def toString = toPrettyString()

  def toPrettyString(graph:Graph = Graph.empty) = {
    // the graph can provide additional information about the edges

    val addNodeLookup = addNodes.toSeq.by(_.id)

    def id(nid: NodeId): String = {
      val str = (
        for {
          node <- addNodeLookup.get(nid).orElse(graph.lookup.nodesById(nid))
        } yield node.str
      ).fold("")(str => s"${ "\"" }$str${ "\"" }")
      s"[${ nid.shortHumanReadable }]$str"
    }

    val sb = new StringBuilder
    sb ++= s"GraphChanges(\n"
    if(addNodes.nonEmpty) sb ++= s"  addNodes: ${ addNodes.map(n => s"${ id(n.id) }:${ n.tpe }/${n.role}/${n.schema}  ${n.id.toBase58}  ${n.id.toUuid}").mkString("\n            ") }\n"
    if(addEdges.nonEmpty) sb ++= s"  addEdges: ${ addEdges.map(e => s"${ id(e.sourceId) } -${ e.data }-> ${ id(e.targetId) }").mkString("\n            ") }\n"
    if(delEdges.nonEmpty) sb ++= s"  delEdges: ${ delEdges.map(e => s"${ id(e.sourceId) } -${ e.data }-> ${ id(e.targetId) }").mkString("\n            ") }\n"
    sb ++= ")"

    sb.result()
  }
}
object GraphChanges {

  final case class Import(changes: GraphChanges, topLevelNodeIds: Seq[NodeId], focusNodeId: Option[NodeId]) {
    def resolve(graph: Graph, parentId: NodeId): GraphChanges = {
      val addToParentChanges =
        if(topLevelNodeIds.isEmpty) GraphChanges.empty
        else if(topLevelNodeIds.size == 1) GraphChanges.addToParent(topLevelNodeIds.map(ChildId(_)), ParentId(parentId))
        else {
          //TODO: fix ordering...
          val children = graph.idToIdxFold[flatland.ArraySliceInt](parentId)(flatland.ArraySliceInt.empty) { focusedIdx => graph.childEdgeIdx(focusedIdx) }
          val minOrderingNum: BigDecimal = if(children.isEmpty) BigDecimal(EpochMilli.now) else children.minBy[BigDecimal](edgeIdx => graph.edges(edgeIdx).as[Edge.Child].data.ordering) - 1
          GraphChanges(
            addEdges = topLevelNodeIds.mapWithIndex { (idx, nodeId) =>
              Edge.Child(ParentId(parentId), EdgeData.Child(ordering = minOrderingNum - idx), ChildId(nodeId))
            }(breakOut)
          )
        }

      changes merge addToParentChanges
    }
  }

  val empty = new GraphChanges()

  def from(
    addNodes: Iterable[Node] = Seq.empty,
    addEdges: Iterable[Edge] = Seq.empty,
    delEdges: Iterable[Edge] = Seq.empty
  ) = GraphChanges(addNodes.toArray, addEdges.toArray, delEdges.toArray)

  def addMarkdownMessage(string: String): GraphChanges = addNode(Node.MarkdownMessage(string))
  def addMarkdownTask(string: String): GraphChanges = addNode(Node.MarkdownTask(string))

  def addNode(content: NodeData.Content, role: NodeRole) = GraphChanges(addNodes = Array(Node.Content(content, role)))
  def addNode(node: Node) = GraphChanges(addNodes = Array(node))
  def addNodeWithParent(node: Node, parentId: ParentId) =
    GraphChanges(addNodes = Array(node), addEdges = Array(Edge.Child(parentId, ChildId(node.id))))
  def addNodeWithParent(node: Node, parentIds: Iterable[ParentId]) =
    GraphChanges(addNodes = Array(node), addEdges = parentIds.map(parentId => Edge.Child(parentId, ChildId(node.id)))(breakOut))
  def addNodesWithParents(nodes: Iterable[Node], parentIds: Iterable[ParentId]) =
    GraphChanges(addNodes = nodes.toArray, addEdges = nodes.flatMap(node => parentIds.map(parentId => Edge.Child(parentId, ChildId(node.id))))(breakOut))
  def addNodeWithDeletedParent(node: Node, parentIds: Iterable[ParentId], deletedAt: EpochMilli) =
    GraphChanges(addNodes = Array(node), addEdges = parentIds.map(parentId => Edge.Child.delete(parentId, deletedAt, ChildId(node.id)))(breakOut))

  def addToParent(nodeId: ChildId, parentId: ParentId): GraphChanges = addToParent(List(nodeId), parentId)
  def addToParent(nodeIds: Iterable[ChildId], parentId: ParentId): GraphChanges= GraphChanges(
    addEdges = nodeIds.map { channelId =>
      Edge.Child(parentId, channelId)
    }(breakOut)
  )

  def addToParents(nodeId: ChildId, parentIds: Iterable[ParentId]): GraphChanges = GraphChanges(
    addEdges = parentIds.map { parentId =>
      Edge.Child(parentId, nodeId)
    }(breakOut)
  )

  val newProjectName = "Untitled Project"
  def newProject(nodeId: NodeId, userId: UserId, title: String = newProjectName, schema: NodeSchema = NodeSchema.empty): GraphChanges = {
    val post = new Node.Content(
      nodeId,
      NodeData.Markdown(title),
      NodeRole.Project,
      NodeMeta(accessLevel = NodeAccess.Inherited),
      schema = schema
    )
    GraphChanges(
      addNodes = Array(post),
      addEdges = Array(
        Edge.Pinned(nodeId, userId),
        Edge.Notify(nodeId, userId),
        Edge.Member(nodeId, EdgeData.Member(AccessLevel.ReadWrite), userId)
      )
    )
  }

  def pin(nodeId:NodeId, userId: UserId) = GraphChanges.connect(Edge.Pinned)(nodeId, userId)
  def unpin(nodeId:NodeId, userId: UserId) = GraphChanges.disconnect(Edge.Pinned)(nodeId, userId)

  def undelete(childIds: Iterable[ChildId], parentIds: Iterable[ParentId]): GraphChanges = connect(Edge.Child)(parentIds, childIds)
  def undelete(childId: ChildId, parentIds: Iterable[ParentId]): GraphChanges = undelete(childId :: Nil, parentIds)
  def undelete(childId: ChildId, parentId: ParentId): GraphChanges = undelete(childId :: Nil, parentId :: Nil)

  def delete(childIds: Iterable[ChildId], parentIds: Iterable[ParentId]): GraphChanges =
    childIds.foldLeft(empty)((acc, nextNode) => acc merge delete(nextNode, parentIds))
  def delete(childId: ChildId, parentIds: Iterable[ParentId], deletedAt: EpochMilli = EpochMilli.now): GraphChanges = GraphChanges(
    addEdges = parentIds.map(
      parentId => Edge.Child.delete(parentId, deletedAt, childId)
    )(breakOut)
  )
  def delete(childId: ChildId, parentId: ParentId): GraphChanges = GraphChanges(
    addEdges = Array(Edge.Child.delete(parentId, childId))
  )

  def deleteFromGraph(childId: ChildId, graph:Graph, timestamp: EpochMilli = EpochMilli.now) = graph.idToIdxFold(childId)(GraphChanges.empty) { childIdx =>
    GraphChanges(
      addEdges = graph.parentEdgeIdx(childIdx).flatMap { edgeIdx =>
        val edge = graph.edges(edgeIdx).asInstanceOf[Edge.Child]
        if (edge.data.deletedAt.isDefined) None
        else Some(edge.copy(data = edge.data.copy(deletedAt = Some(timestamp))))
      }(breakOut)
    )
  }

  class ConnectFactory[SOURCEID, TARGETID, EDGE <: Edge : ClassTag](edge: (SOURCEID, TARGETID) => EDGE, toGraphChanges: Array[EDGE] => GraphChanges) {
    def apply(sourceId: SOURCEID, targetId: TARGETID): GraphChanges =
      if(sourceId != targetId) toGraphChanges(Array(edge(sourceId, targetId))) else empty
    def apply(sourceId: SOURCEID, targetIds: Iterable[TARGETID]): GraphChanges =
      toGraphChanges(targetIds.collect { case targetId if targetId != sourceId => edge(sourceId, targetId) }(breakOut))
    def apply(sourceIds: Iterable[SOURCEID], targetId: TARGETID): GraphChanges
    =
      toGraphChanges(sourceIds.collect { case sourceId if sourceId != targetId => edge(sourceId, targetId) }(breakOut))
    def apply(sourceIds: Iterable[SOURCEID], targetIds: Iterable[TARGETID]): GraphChanges
    =
      toGraphChanges(sourceIds.flatMap(sourceId => targetIds.collect { case targetId if targetId != sourceId => edge(sourceId, targetId) })(breakOut))
  }

  class ConnectFactoryWithData[SOURCE, TARGET, DATA, EDGE <: Edge : ClassTag](edge: (SOURCE, DATA, TARGET) => EDGE, toGraphChanges: Array[EDGE] => GraphChanges) {
    def apply(sourceId: SOURCE, data: DATA, targetId: TARGET): GraphChanges =
      if(sourceId != targetId) toGraphChanges(Array(edge(sourceId, data, targetId))) else empty
    def apply(sourceId: SOURCE, data: DATA, targetIds: Iterable[TARGET]): GraphChanges =
      toGraphChanges(targetIds.collect { case targetId if targetId != sourceId => edge(sourceId, data, targetId) }(breakOut))
    def apply(sourceIds: Iterable[SOURCE], data: DATA, targetId: TARGET): GraphChanges
    =
      toGraphChanges(sourceIds.collect { case sourceId if sourceId != targetId => edge(sourceId, data, targetId) }(breakOut))
    def apply(sourceIds: Iterable[SOURCE], data: DATA, targetIds: Iterable[TARGET]): GraphChanges
    =
      toGraphChanges(sourceIds.flatMap(sourceId => targetIds.collect { case targetId if targetId != sourceId => edge(sourceId, data, targetId) })(breakOut))
  }

  def connect[SOURCE <: NodeId, TARGET <: NodeId, EDGE <: Edge](edge: (SOURCE, TARGET) => EDGE) = new ConnectFactory(edge, (edges: Array[Edge]) => GraphChanges(addEdges = edges))
  def connect[SOURCE <: NodeId, TARGET <: NodeId, DATA, EDGE <: Edge](edge: (SOURCE, DATA, TARGET) => EDGE) = new ConnectFactoryWithData(edge, (edges: Array[Edge]) => GraphChanges(addEdges = edges))
  def disconnect[SOURCE <: NodeId, TARGET <: NodeId, EDGE <: Edge](edge: (SOURCE, TARGET) => EDGE) = new ConnectFactory(edge, (edges: Array[Edge]) => GraphChanges(delEdges = edges))
  def disconnect[SOURCE <: NodeId, TARGET <: NodeId, DATA, EDGE <: Edge](edge: (SOURCE, DATA, TARGET) => EDGE) = new ConnectFactoryWithData(edge, (edges: Array[Edge]) => GraphChanges(delEdges = edges))

  def changeSource[SOURCE <: NodeId, TARGET <: NodeId, EDGE <: Edge](edge: (SOURCE, TARGET) => EDGE)(targetIds: Iterable[TARGET], oldSourceIds: Iterable[SOURCE], newSourceIds: Iterable[SOURCE]): GraphChanges = {
    val disconnect: GraphChanges = GraphChanges.disconnect(edge)(oldSourceIds, targetIds)
    val connect: GraphChanges = GraphChanges.connect(edge)(newSourceIds, targetIds)
    disconnect merge connect
  }
  def changeTarget[SOURCE <: NodeId, TARGET <: NodeId, EDGE <: Edge](edge: (SOURCE, TARGET) => EDGE)(sourceIds: Iterable[SOURCE], oldTargetIds: Iterable[TARGET], newTargetIds: Iterable[TARGET]): GraphChanges = {
    val disconnect: GraphChanges = GraphChanges.disconnect(edge)(sourceIds, oldTargetIds)
    val connect: GraphChanges = GraphChanges.connect(edge)(sourceIds, newTargetIds)
    disconnect merge connect
  }

  //TODO: The GrahpChange.moveInto function needs tests!
  //TODO: unify with moveInto used for drag&drop?
  //TODO: write with int
  def moveInto(graph: Graph, subjectIds: Iterable[ChildId], newParentIds: Iterable[ParentId]): GraphChanges =
    newParentIds.foldLeft(GraphChanges.empty) { (changes, targetId) => changes merge GraphChanges.moveInto(graph, subjectIds, targetId) }
  def moveInto(graph: Graph, subjectId: ChildId, newParentId: ParentId): GraphChanges = moveInto(graph, subjectId :: Nil, newParentId)
  def moveInto(graph: Graph, subjectIds: Iterable[ChildId], newParentId: ParentId): GraphChanges = {
    // TODO: only keep deepest parent in transitive chain
    val newParentships: Array[Edge] = subjectIds
      .filterNot(_ == newParentId) // avoid creating self-loops
      .map { subjectId =>
        // if subject was not deleted in one of its parents => keep it
        // if it was deleted, take the latest deletion date
        val deletedAt = graph.idToIdxFold(subjectId)(Option.empty[EpochMilli])(graph.latestDeletedAt)

        Edge.Child(newParentId, deletedAt, subjectId)
      }(breakOut)

    val cycleFreeSubjects: Iterable[NodeId] = subjectIds.filterNot(subject => subject == newParentId || (graph.ancestors(newParentId) contains subject)) // avoid self loops and cycles
    val removeParentships = ((
      for {
        subject <- cycleFreeSubjects
        parent <- graph.parents(subject)
        subjectRole <- graph.nodesById(subject).map(_.role)
        parentRole <- graph.nodesById(parent).map(_.role)
        // moving nodes should keep its tags. Except for tags itself.
        if subjectRole == NodeRole.Tag || (subjectRole != NodeRole.Tag && parentRole != NodeRole.Tag)
      } yield Edge.Child(ParentId(parent), ChildId(subject))
    )(breakOut): Array[Edge]) diff newParentships

    GraphChanges(addEdges = newParentships, delEdges = removeParentships)
  }


  // these moveInto methods are only used for drag&drop right now. Can we unify them with the above moveInto?
  @inline def moveInto(nodeId: ChildId, newParentId: ParentId, graph:Graph): GraphChanges = moveInto(nodeId :: Nil, newParentId :: Nil, graph)
  @inline def moveInto(nodeId: Iterable[ChildId], newParentId: ParentId, graph:Graph): GraphChanges = moveInto(nodeId, newParentId :: Nil, graph)
  @inline def moveInto(nodeId: ChildId, newParentIds: Iterable[ParentId], graph:Graph): GraphChanges = moveInto(nodeId :: Nil, newParentIds, graph)
  @inline def moveInto(nodeIds: Iterable[ChildId], newParentIds: Iterable[ParentId], graph:Graph): GraphChanges = {
    GraphChanges.moveInto(graph, nodeIds, newParentIds)
  }

  def movePinnedChannel(channelId: ChildId, visibleSourceChannelId: Option[ParentId], targetChannelId: Option[ParentId], graph: Graph, userId: UserId): GraphChanges = graph.idToIdxFold(channelId)(GraphChanges.empty) { channelIdx =>
    // target == None means that channel becomes top-level

    // the visibleSourceChannelId is the visual parent of the channelId. The visualize parent might have some nodes in between
    // itself and the channelId. Therefore we search all parents of channelId that lie between channelId and visibleSourceChannelId.
    val disconnect: GraphChanges = visibleSourceChannelId.fold(GraphChanges.empty) { visibleSourceChannelId =>
      graph.idToIdxFold(visibleSourceChannelId)(GraphChanges.empty) { visibleSourceChannelIdx =>
        val delEdges = Array.newBuilder[Edge]
        graph.parentsIdx.foreachElement(channelIdx) { parentIdx =>
          val canReachVisibleSourceChannel = dfs.exists(_(parentIdx), dfs.withStart, graph.parentsIdx, isFound = { parentIdx =>
            parentIdx == visibleSourceChannelIdx
          })

          if (canReachVisibleSourceChannel) delEdges += Edge.Child(ParentId(graph.nodeIds(parentIdx)), channelId)
        }

        GraphChanges(delEdges = delEdges.result)
      }
    }

    val connect: GraphChanges = targetChannelId.fold(GraphChanges.empty) { targetChannelId =>
      GraphChanges.connect(Edge.Child)(targetChannelId, channelId)
    }
    disconnect merge connect
  }

  def linkOrCopyInto(edge: Edge.LabeledProperty, nodeId: NodeId, graph:Graph): GraphChanges = if (edge.nodeId != nodeId) {
    graph.nodesById(edge.propertyId) match {
      case Some(node: Node.Content) if node.role == NodeRole.Neutral =>
        val copyNode = node.copy(id = NodeId.fresh)
        GraphChanges(
          addNodes = Array(copyNode),
          addEdges = Array(edge.copy(nodeId = nodeId, propertyId = PropertyId(copyNode.id)))
        )
      case _ =>
        GraphChanges(
          addEdges = Array(edge.copy(nodeId = nodeId))
        )
    }
  } else GraphChanges.empty

  @inline def linkInto(nodeId: ChildId, tagId: ParentId, graph:Graph): GraphChanges = linkInto(nodeId :: Nil, tagId, graph)
  @inline def linkInto(nodeId: ChildId, tagIds: Iterable[ParentId], graph:Graph): GraphChanges = linkInto(nodeId :: Nil, tagIds, graph)
  @inline def linkInto(nodeIds: Iterable[ChildId], tagId: ParentId, graph:Graph): GraphChanges = linkInto(nodeIds, tagId :: Nil, graph)
  def linkInto(nodeIds: Iterable[ChildId], tagIds: Iterable[ParentId], graph:Graph): GraphChanges = {
    // tags will be added with the same (latest) deletedAt date, which the node already has for other parents
    nodeIds.foldLeft(GraphChanges.empty) { (currentChange, nodeId) =>
      val deletedAt = graph.idToIdxFold(nodeId)(Option.empty[EpochMilli])(graph.latestDeletedAt)
      currentChange merge GraphChanges.connect[ParentId, ChildId, Option[EpochMilli], Edge.Child](Edge.Child(_, _, _))(tagIds, deletedAt, nodeIds)
    }
  }


  @inline def linkOrMoveInto(nodeId: ChildId, newParentId: ParentId, graph:Graph, link:Boolean): GraphChanges = linkOrMoveInto(nodeId :: Nil, newParentId :: Nil, graph, link)
  @inline def linkOrMoveInto(nodeId: Iterable[ChildId], newParentId: ParentId, graph:Graph, link:Boolean): GraphChanges = linkOrMoveInto(nodeId, newParentId :: Nil, graph, link)
  @inline def linkOrMoveInto(nodeId: ChildId, newParentIds: Iterable[ParentId], graph:Graph, link:Boolean): GraphChanges = linkOrMoveInto(nodeId :: Nil, newParentIds, graph, link)
  @inline def linkOrMoveInto(nodeId: Iterable[ChildId], newParentId: Iterable[ParentId], graph:Graph, link:Boolean): GraphChanges = {
    if(link) linkInto(nodeId, newParentId, graph)
    else moveInto(nodeId, newParentId, graph)
  }


  @inline def assign(nodeId: NodeId, userId: UserId): GraphChanges = {
    GraphChanges.connect(Edge.Assigned)(nodeId, userId)
  }

  def connectWithProperty(sourceId:NodeId, propertyName:String, targetId:NodeId) = {
    connect(Edge.LabeledProperty)(sourceId, EdgeData.LabeledProperty(propertyName), PropertyId(targetId))
  }
}
