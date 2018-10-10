package wust.graph

import wust.ids._
import wust.util.{Memo, NestedArrayInt, SliceInt, algorithm}
import wust.util.algorithm._
import wust.util.collection._
import wust.util.time.time

import collection.{breakOut, mutable}
import scala.collection.immutable

object Graph {
  val empty = new Graph(Set.empty, Set.empty)

  def apply(nodes: Iterable[Node] = Nil, edges: Iterable[Edge] = Nil): Graph = {
    new Graph(nodes.toSet, edges.toSet)
  }
}

final case class Graph(nodes: Set[Node], edges: Set[Edge]) {
  lazy val lookup = time(s"graph lookup [${nodes.size}, ${edges.size}]") { GraphLookup(this, nodes.toArray, edges.toArray) }

  def isEmpty: Boolean = nodes.isEmpty
  def nonEmpty: Boolean = !isEmpty
  def size: Int = nodes.size
  def length: Int = size

  override def toString: String = {
    def nodeStr(node: Node) =
      s"${node.data.tpe}(${node.data.str}:${node.id.toBase58.takeRight(3)})"
    s"Graph(${nodes.map(nodeStr).mkString(" ")}, " +
      s"${edges
        .map(c => s"${c.sourceId.toBase58.takeRight(3)}-${c.data}->${c.targetId.toBase58.takeRight(3)}")
        .mkString(", ")})"
  }

  def toSummaryString = {
    s"Graph(nodes: ${nodes.size}, ${edges.size})"
  }

  def pageContentWithAuthors(page: Page): Graph = {
    val pageChildren = page.parentIds.flatMap(lookup.descendantsWithDeleted)
    this.filterIds(pageChildren.toSet ++ pageChildren.flatMap(lookup.authorIds) ++ page.parentIds)
  }

  def pageContent(page: Page): Graph = {
    val pageChildren = page.parentIds.flatMap(lookup.descendants _)
    this.filterIds(pageChildren.toSet)
  }

  def -(nodeId: NodeId): Graph = removeNodes(nodeId :: Nil)
  def -(edge: Edge): Graph = copy(edges = edges - edge)

  def filterIds(p: NodeId => Boolean): Graph = filter(node => p(node.id))
  def filter(p: Node => Boolean): Graph = {
    // we only want to call p once for each node
    // and not trigger the pre-caching machinery of nodeIds
    val filteredNodes = nodes.filter(p)

    @inline def nothingFiltered = filteredNodes.size == nodes.size

    if(nothingFiltered) this
    else {
      val filteredNodeIds: Set[NodeId] = filteredNodes.map(_.id)
      copy(
        nodes = filteredNodes,
        edges = edges.filter(e => filteredNodeIds(e.sourceId) && filteredNodeIds(e.targetId))
      )
    }
  }

  def filterNotIds(p: NodeId => Boolean): Graph = filterIds(id => !p(id))
  def filterNot(p: Node => Boolean): Graph = filter(node => !p(node))

  def removeNodes(nids: Iterable[NodeId]): Graph = filterNotIds(nids.toSet)
  def removeConnections(es: Iterable[Edge]): Graph = copy(edges = edges -- es)
  def addNodes(ns: Iterable[Node]): Graph = changeGraphInternal(addNodes = ns.toSet, addEdges = Set.empty)
  def addConnections(es: Iterable[Edge]): Graph = changeGraphInternal(addNodes = Set.empty, addEdges = es.toSet)

  def applyChanges(c: GraphChanges): Graph = changeGraphInternal(addNodes = c.addNodes.toSet, addEdges = c.addEdges.toSet, deleteEdges = c.delEdges.toSet)
  def +(node: Node): Graph = changeGraphInternal(addNodes = Set(node), addEdges = Set.empty)
  def +(edge: Edge): Graph = changeGraphInternal(addNodes = Set.empty, addEdges = Set(edge))
  def +(that: Graph): Graph = changeGraphInternal(addNodes = that.nodes, addEdges = that.edges)

  private def changeGraphInternal(addNodes: Set[Node], addEdges: Set[Edge], deleteEdges: Set[Edge] = Set.empty): Graph = {
    val addNodeIds: Set[NodeId] = addNodes.map(_.id)(breakOut)
    val addEdgeIds: Set[(NodeId, String, NodeId)] = addEdges.map(e => (e.sourceId, e.data.tpe, e.targetId))(breakOut)
    copy(
      nodes = nodes.filterNot(n => addNodeIds(n.id)) ++ addNodes,
      edges = edges.filterNot(e => addEdgeIds((e.sourceId, e.data.tpe, e.targetId))) ++ addEdges -- deleteEdges
    )
  }

  @deprecated("", "") @inline def nodeIds = lookup.nodeIds
  @deprecated("", "") @inline def nodesById(nodeId:NodeId) = lookup.nodesById(nodeId)
  @deprecated("", "") @inline def userIdByName = lookup.userIdByName
  @deprecated("", "") @inline def nodeModified(nodeId:NodeId) = lookup.nodeModified(lookup.idToIdx(nodeId))
  @deprecated("", "") @inline def nodeCreated(nodeId:NodeId) = lookup.nodeCreated(lookup.idToIdx(nodeId))
  @deprecated("", "") @inline def expandedNodes = lookup.expandedNodes
  @deprecated("", "")
  @inline def authorshipsByNodeId(nodeId: NodeId): Seq[Edge.Author] = lookup.authorshipEdgeIdx(lookup.idToIdx(nodeId)).map(idx => lookup.edges(idx).asInstanceOf[Edge.Author])
  @deprecated("", "")
  @inline def isChildOfAny(n: NodeId, parentIds: Iterable[NodeId]) = lookup.isChildOfAny(n, parentIds)
  @deprecated("", "")
  @inline def isDeletedChildOfAny(n: NodeId, parentIds: Iterable[NodeId]) = lookup.isDeletedChildOfAny(n, parentIds)
  @deprecated("", "") @inline def authorIds = lookup.authorIds
  @deprecated("", "") @inline def incidentChildContainments = lookup.incidentChildContainments
  @deprecated("", "") @inline def incidentParentContainments = lookup.incidentParentContainments
  @deprecated("", "") @inline def successorsWithoutParent = lookup.successorsWithoutParent
  @deprecated("", "") @inline def predecessorsWithoutParent = lookup.predecessorsWithoutParent
  @deprecated("", "") @inline def neighboursWithoutParent = lookup.neighboursWithoutParent
  @deprecated("", "")
  @deprecated("", "") @inline def children(n: NodeId) = lookup.children(n)
  @deprecated("", "") @inline def deletedChildren(n: NodeId) = lookup.deletedChildren(n)
  @deprecated("", "") @inline def members(n: NodeId) = lookup.members(n)
  @deprecated("", "") @inline def authors(n: NodeId) = lookup.authors(n)
  @deprecated("", "") @inline def authorsIn(n: NodeId) = lookup.authorsIn(n)
  @deprecated("", "") @inline def hasChildren(n: NodeId) = lookup.hasChildren(n)
  @deprecated("", "") @inline def hasDeletedChildren(n: NodeId) = lookup.hasDeletedChildren(n)
  @deprecated("", "") @inline def parents(n: NodeId) = lookup.parents(n)
  @deprecated("", "") @inline def parentDepths(n: NodeId) = lookup.parentDepths(n)
  @deprecated("", "") @inline def childDepth(n: NodeId) = lookup.childDepth(n)
  @deprecated("", "") @inline def deletedParents(n: NodeId) = lookup.deletedParents(n)
  @deprecated("", "") @inline def hasParents(n: NodeId) = lookup.hasParents(n)
  @deprecated("", "") @inline def labeledEdges = lookup.labeledEdges
  @deprecated("", "") @inline def containments = lookup.containments
  @deprecated("", "") @inline def containmentNeighbours = lookup.containmentNeighbours
  @deprecated("", "") @inline def rootNodes = lookup.rootNodes
  @deprecated("", "") @inline def contentNodes = lookup.contentNodes
  @deprecated("", "")
  @inline def redundantTree(root: Node, excludeCycleLeafs: Boolean) = lookup.redundantTree(lookup.idToIdx(root.id), excludeCycleLeafs)
  @deprecated("", "") @inline def channelTree(user: UserId): Seq[Tree] = lookup.channelTree(user)
  @deprecated("", "")
  @inline def can_access_node(userId: NodeId, nodeId: NodeId): Boolean = lookup.can_access_node(userId, nodeId)
  @deprecated("", "") @inline def descendants(nodeId: NodeId) = lookup.descendants(nodeId)
  @deprecated("", "") @inline def ancestors(nodeId: NodeId) = lookup.ancestors(nodeId)
  @deprecated("", "") @inline def involvedInContainmentCycle(id: NodeId) = lookup.involvedInContainmentCycle(id)
  @deprecated("", "")
  @inline def inChildParentRelation(child: NodeId, possibleParent: NodeId): Boolean = lookup.inChildParentRelation(child, possibleParent)
  @deprecated("", "")
  @inline def isStaticParentIn(id: NodeId, parentIds: Iterable[NodeId]): Boolean = lookup.isStaticParentIn(id, parentIds)

}

final case class GraphLookup(graph: Graph, nodes: Array[Node], edges: Array[Edge]) {

  @inline private def n = nodes.length
  @inline private def m = edges.length

  def createArraySet(ids:Set[NodeId]): ArraySet = {
    val marker = ArraySet.create(n)
    ids.foreach{id =>
      val idx = idToIdx.getOrElse(id,-1)
      if(idx != -1)
        marker.add(idx)
    }
    marker
  }

  def createBitSet(ids:Set[NodeId]): immutable.BitSet = {
    val builder = immutable.BitSet.newBuilder
    ids.foreach{id =>
      val idx = idToIdx.getOrElse(id,-1)
      if(idx != -1)
        builder += idx
    }
    builder.result()
  }

  @deprecated("","")
  private val _idToIdx = mutable.HashMap.empty[NodeId, Int]
  _idToIdx.sizeHint(n)
  val nodeIds = new Array[NodeId](n)

  lazy val userIdByName:Map[String,UserId] = nodes.collect{case u:Node.User => u.name -> u.id}(breakOut)

  time("graph lookup: node loop") {
    nodes.foreachIndexAndElement { (i, node) =>
      val nodeId = node.id
      _idToIdx(nodeId) = i
      nodeIds(i) = nodeId
    }
  }

  @deprecated("","")
  @inline def idToIdx: collection.Map[NodeId, Int] = _idToIdx
  @deprecated("","")
  @inline def nodesById(nodeId: NodeId): Node = nodes(idToIdx(nodeId))
  @inline def nodesByIdGet(nodeId: NodeId): Option[Node] = {
    val idx = idToIdx.getOrElse(nodeId,-1)
    if(idx == -1) None
    else Some(nodes(idx))
  }

  @deprecated("","")
  @inline def contains(nodeId: NodeId) = idToIdx.isDefinedAt(nodeId)

  assert(idToIdx.size == nodes.length, "nodes are not distinct by id")


  // we initialize alll builders with null to prevent many useless allocations
  private val parentLookupBuilder = Array.fill[mutable.ArrayBuilder.ofInt](n)(null)
  private val childLookupBuilder = Array.fill[mutable.ArrayBuilder.ofInt](n)(null)
  private val deletedParentLookupBuilder = Array.fill[mutable.ArrayBuilder.ofInt](n)(null)
  private val deletedChildLookupBuilder = Array.fill[mutable.ArrayBuilder.ofInt](n)(null)
  private val notifyByUserLookupBuilder = Array.fill[mutable.ArrayBuilder.ofInt](n)(null)
  private val authorshipEdgeLookupBuilder = Array.fill[mutable.ArrayBuilder.ofInt](n)(null)
  private val membershipEdgeLookupBuilder = Array.fill[mutable.ArrayBuilder.ofInt](n)(null)
  private val authorLookupBuilder = Array.fill[mutable.ArrayBuilder.ofInt](n)(null)
  private val pinnedNodeIdxLookupBuilder = Array.fill[mutable.ArrayBuilder.ofInt](n)(null)

  // since builders can be null, we create them lazily
  @inline private def addToNestedBuilder(builderArray: Array[mutable.ArrayBuilder.ofInt], idx:Int, value:Int):Unit = {
    if(builderArray(idx) == null)
      builderArray(idx) = new mutable.ArrayBuilder.ofInt
    builderArray(idx) += value
  }


  @deprecated("","")
  val labeledEdges = mutable.HashSet[Edge.Label]()
  val containmentsBuilder = mutable.ArrayBuilder.make[Edge.Parent]

  @deprecated("","")
  val staticParentIn = mutable.HashMap[NodeId, collection.Set[NodeId]]().withDefaultValue(mutable.HashSet.empty[NodeId])
  @deprecated("","")
  val expandedNodes = mutable.HashMap[UserId, collection.Set[NodeId]]().withDefaultValue(mutable.HashSet.empty[NodeId])

  private val remorseTimeForDeletedParents: EpochMilli = EpochMilli(EpochMilli.now - (24 * 3600 * 1000))

  time("graph lookup: edge loop") {
    edges.foreachIndexAndElement { (i, edge) =>
      val sourceIdx = idToIdx.getOrElse(edge.sourceId, -1)
      if(sourceIdx != -1) {
        val targetIdx = idToIdx.getOrElse(edge.targetId, -1)
        if(targetIdx != -1) {
          edge match {
            case e@Edge.Author(authorId, _, nodeId)     =>
              addToNestedBuilder(authorshipEdgeLookupBuilder, targetIdx, i)
              addToNestedBuilder(authorLookupBuilder, targetIdx, sourceIdx)
            case e@Edge.Member(authorId, _, nodeId)     =>
              addToNestedBuilder(membershipEdgeLookupBuilder, targetIdx, i)
            case e@Edge.Parent(childId, data, parentId) =>
              data.deletedAt match {
                case None            =>
                  addToNestedBuilder(parentLookupBuilder, sourceIdx, targetIdx)
                  addToNestedBuilder(childLookupBuilder, targetIdx, sourceIdx)
                  containmentsBuilder += e
                case Some(deletedAt) =>
                  //TODO should already be filtered in backend
                  if(deletedAt > remorseTimeForDeletedParents) {
                    addToNestedBuilder(deletedParentLookupBuilder, sourceIdx, targetIdx)
                    addToNestedBuilder(deletedChildLookupBuilder, targetIdx, sourceIdx)
                  }
              }
            case _: Edge.StaticParentIn                 =>
              staticParentIn(edge.sourceId) += edge.targetId
            case e: Edge.Label =>
              labeledEdges += e
            case _: Edge.Expanded =>
              expandedNodes(edge.sourceId.asInstanceOf[UserId]) += edge.targetId
            case _: Edge.Notify =>
              addToNestedBuilder(notifyByUserLookupBuilder, targetIdx, sourceIdx)
            case _: Edge.Pinned =>
              addToNestedBuilder(pinnedNodeIdxLookupBuilder, sourceIdx, targetIdx)
            case _              =>
          }
        }
      }
    }
  }

  val parentsIdx = NestedArrayInt(parentLookupBuilder)
  val childrenIdx = NestedArrayInt(childLookupBuilder)
  val deletedChildrenIdx = NestedArrayInt(deletedChildLookupBuilder)
  val deletedParentsIdx = NestedArrayInt(deletedParentLookupBuilder)
  val authorshipEdgeIdx = NestedArrayInt(authorshipEdgeLookupBuilder)
  val membershipEdgeIdx = NestedArrayInt(membershipEdgeLookupBuilder)
  val notifyByUserIdx = NestedArrayInt(notifyByUserLookupBuilder)
  val authorsIdx = NestedArrayInt(authorLookupBuilder)
  val pinnedNodeIdx = NestedArrayInt(pinnedNodeIdxLookupBuilder)

  val containments = containmentsBuilder.result()

  val parents: NodeId => collection.Set[NodeId] = Memo.mutableHashMapMemo { id =>
    val idx = idToIdx.getOrElse(id, -1)
    if(idx != -1) parentsIdx(idx).map(i => nodes(i).id)(breakOut) else Set.empty[NodeId]
  }
  val children: NodeId => collection.Set[NodeId] = Memo.mutableHashMapMemo { id =>
    val idx = idToIdx.getOrElse(id, -1)
    if(idx != -1) childrenIdx(idx).map(i => nodes(i).id)(breakOut) else Set.empty[NodeId]
  }
  val deletedParents: NodeId => collection.Set[NodeId] = Memo.mutableHashMapMemo { id =>
    val idx = idToIdx.getOrElse(id, -1)
    if(idx != -1) deletedParentsIdx(idx).map(i => nodes(i).id)(breakOut) else Set.empty[NodeId]
  }
  val deletedChildren: NodeId => collection.Set[NodeId] = Memo.mutableHashMapMemo { id =>
    val idx = idToIdx.getOrElse(id, -1)
    if(idx != -1) deletedChildrenIdx(idx).map(i => nodes(i).id)(breakOut) else Set.empty[NodeId]
  }

  lazy val (nodeCreated: Array[Long], nodeModified: Array[Long]) = {
    val nodeCreated = Array.fill(n)(EpochMilli.min: Long)
    val nodeModified = Array.fill(n)(EpochMilli.min: Long)
    var nodeIdx = 0
    while(nodeIdx < n) {
      val authorEdgeIndices: SliceInt = authorshipEdgeIdx(nodeIdx)
      if(authorEdgeIndices.nonEmpty) {
        val sortedAuthorEdges = authorEdgeIndices.sortBy(idx => edges(idx).asInstanceOf[Edge.Author].data.timestamp)
        nodeCreated(nodeIdx) = edges(sortedAuthorEdges(0)).asInstanceOf[Edge.Author].data.timestamp
        nodeModified(nodeIdx) = edges(sortedAuthorEdges(sortedAuthorEdges.length - 1)).asInstanceOf[Edge.Author].data.timestamp
      }
      nodeIdx += 1
    }
    (nodeCreated, nodeModified)
  }

  def filterIdx(p: Int => Boolean): Graph = {
    // we only want to call p once for each node
    // and not trigger the pre-caching machinery of nodeIds
    val filteredNodesIndices = nodes.indices.filter(p)

    @inline def nothingFiltered = filteredNodesIndices.length == nodes.length

    if(nothingFiltered) graph
    else {
      val filteredNodeIds: Set[NodeId] = filteredNodesIndices.map(nodeIds)(breakOut)
      val filteredNodes: Set[Node] = filteredNodesIndices.map(nodes)(breakOut)
      Graph(
        nodes = filteredNodes,
        edges = edges.filter(e => filteredNodeIds(e.sourceId) && filteredNodeIds(e.targetId))
      )
    }
  }

  val authorIds: NodeId => IndexedSeq[UserId] = Memo.mutableHashMapMemo { nodeId =>
    authorsIdx(idToIdx(nodeId)).map(idx => nodeIds(idx).asInstanceOf[UserId])
  }
  val authors: NodeId => IndexedSeq[Node.User] = Memo.mutableHashMapMemo { nodeId =>
    authorsIdx(idToIdx(nodeId)).map(idx => nodes(idx).asInstanceOf[Node.User])
  }

  val authorsIn: NodeId => List[Node.User] = Memo.mutableHashMapMemo { parentId =>
    (parentId :: descendantsWithDeleted(parentId).toList).flatMap(authors)
  }

  val members: NodeId => IndexedSeq[Node.User] = Memo.mutableHashMapMemo { nodeId =>
    val nodeIdx = idToIdx(nodeId)
    membershipEdgeIdx(nodeIdx).map(edgeIdx => nodesById(edges(edgeIdx).asInstanceOf[Edge.Member].userId).asInstanceOf[Node.User])
  }

  def isDeletedNow(nodeIdx: Int, parentIndices: immutable.BitSet): Boolean = {

    val deletedParentSet = ArraySet.create(n)
    deletedParentsIdx.foreachElement(nodeIdx)(deletedParentSet.add)

    deletedParentsIdx.sliceNonEmpty(nodeIdx) && (
      if(parentIndices.forall(deletedParentSet.contains)) true
      else parentsIdx.sliceIsEmpty(nodeIdx)
    )
  }

  def directNodeTags(nodeIdx: Int, parentIndices: immutable.BitSet): Array[Node] = {
    //      (parents(nodeId).toSet -- (parentIds - nodeId)).map(nodesById) // "- nodeId" reveals self-loops with page-parent

    val tagSet = new mutable.ArrayBuilder.ofRef[Node]

    parentsIdx.foreachElement(nodeIdx){ i =>
      if(!parentIndices.contains(i) || i == nodeIdx)
        tagSet += nodes(i)
    }

    tagSet.result()
  }

  def transitiveNodeTags(nodeIdx:Int, parentIndices: immutable.BitSet):Array[Node] = {
    //      val transitivePageParents = parentIds.flatMap(ancestors)
    //      (ancestors(nodeId).toSet -- parentIds -- transitivePageParents -- parents(nodeId))
    //        .map(nodesById)
    val tagSet = ArraySet.create(n)

    ancestorsIdx(nodeIdx).foreachElement(tagSet.add)
    parentIndices.foreach{ parentIdx =>
      tagSet.remove(parentIdx)
      ancestorsIdx(parentIdx).foreachElement(tagSet.remove)
    }
    parentsIdx.foreachElement(nodeIdx)(tagSet.remove)

    tagSet.map(nodes)
  }

  lazy val chronologicalNodesAscending: IndexedSeq[Node] = {
     nodes.indices.sortBy(nodeCreated).map(nodes)
  }

  lazy val contentNodes: Iterable[Node.Content] = nodes.collect { case p: Node.Content => p }

  lazy val allParentIds: IndexedSeq[NodeId] = containments.map(_.targetId)
  lazy val allParentIdsTopologicallySortedByChildren: Iterable[NodeId] =
    allParentIds.topologicalSortBy(children)

  private lazy val nodeDefaultNeighbourhood: collection.Map[NodeId, Set[NodeId]] =
    defaultNeighbourhood(nodeIds, Set.empty[NodeId])
  lazy val successorsWithoutParent
  : collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ directedAdjacencyList[
    NodeId,
    Edge,
    NodeId
    ](labeledEdges, _.sourceId, _.targetId)
  lazy val predecessorsWithoutParent
  : collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ directedAdjacencyList[
    NodeId,
    Edge,
    NodeId
    ](labeledEdges, _.targetId, _.sourceId)
  lazy val neighboursWithoutParent
  : collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ adjacencyList[
    NodeId,
    Edge
    ](labeledEdges, _.targetId, _.sourceId)

  def inChildParentRelation(child: NodeId, possibleParent: NodeId): Boolean =
    parents(child).contains(possibleParent)
  def inDescendantAncestorRelation(descendent: NodeId, possibleAncestor: NodeId): Boolean =
    ancestors(descendent).contains(possibleAncestor)

  def isStaticParentIn(id: NodeId, parentId: NodeId): Boolean = staticParentIn(id).contains(parentId)
  def isStaticParentIn(id: NodeId, parentIds: Iterable[NodeId]): Boolean = {
    val staticInParents = staticParentIn(id)
    parentIds.exists(parentId => staticInParents.contains(parentId))
  }


  @inline def hasChildrenIdx(nodeIdx: Int): Boolean = childrenIdx.sliceNonEmpty(nodeIdx)
  @inline def hasParentsIdx(nodeIdx: Int): Boolean = parentsIdx.sliceNonEmpty(nodeIdx)
  @inline def hasDeletedChildrenIdx(nodeIdx: Int): Boolean = deletedChildrenIdx.sliceNonEmpty(nodeIdx)
  @inline def hasDeletedParentsIdx(nodeIdx: Int): Boolean = deletedParentsIdx.sliceNonEmpty(nodeIdx)

  @inline private def hasSomethingById(nodeId:NodeId, lookup: Int => Boolean) = {
    val idx = idToIdx.getOrElse(nodeId, -1)
    if(idx == -1)
      false
    else
      lookup(idx)
  }
  def hasChildren(nodeId: NodeId): Boolean = hasSomethingById(nodeId, hasChildrenIdx)
  def hasParents(nodeId: NodeId): Boolean = hasSomethingById(nodeId, hasParentsIdx)
  def hasDeletedChildren(nodeId: NodeId): Boolean = hasSomethingById(nodeId, hasDeletedChildrenIdx)
  def hasDeletedParents(nodeId: NodeId): Boolean = hasSomethingById(nodeId, hasDeletedParentsIdx)

  def isChildOfAny(childId: NodeId, parentIds: Iterable[NodeId]): Boolean = {
    val p = parents(childId)
    parentIds.exists(p.contains)
  }
  def isDeletedChildOfAny(childId: NodeId, parentIds: Iterable[NodeId]): Boolean = {
    val p = deletedParents(childId)
    parentIds.exists(p.contains)
  }

  private lazy val connectionDefaultNeighbourhood: collection.Map[NodeId, collection.Set[Edge]] =
    defaultNeighbourhood(nodeIds, Set.empty[Edge])
  lazy val outgoingEdges
  : collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++
    directedIncidenceList[NodeId, Edge](edges, _.sourceId)

  lazy val incidentParentContainments
  : collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++ directedIncidenceList[
    NodeId,
    Edge
    ](containments, _.sourceId)
  lazy val incidentChildContainments
  : collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++ directedIncidenceList[
    NodeId,
    Edge
    ](containments, _.targetId)
  lazy val incidentContainments
  : collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++ incidenceList[
    NodeId,
    Edge
    ](containments, _.targetId, _.sourceId)

  def involvedInContainmentCycleIdx(idx: Int): Boolean = {
    // TODO: maybe fast involved-in-cycle-algorithm?
    // breadth-first-search starting at successors and another one starting at predecessors in different direction.
    // When both queues contain the same elements, we can stop, because we found a cycle
    // Even better:
    // lazy val involvedInContainmentCycle:Set[NodeId] = all nodes involved in a cycle
    childrenIdx(idx).exists(child => depthFirstSearchExists(child, childrenIdx, idx))
  }
  def involvedInContainmentCycle(id: NodeId): Boolean = {
    val idx = idToIdx.getOrElse(id, -1)
    if(idx == -1) return false
    else involvedInContainmentCycleIdx(idx)
  }

  def descendantsIdx(nodeIdx: Int) = _descendantsIdx(nodeIdx)
  private val _descendantsIdx: Int => Array[Int] = Memo.arrayMemo[Array[Int]](n).apply { nodeIdx: Int =>
    depthFirstSearchWithoutStart(nodeIdx, childrenIdx)
  }
  def descendants(nodeId: NodeId) = _descendants(nodeId)
  private val _descendants: NodeId => Seq[NodeId] = Memo.mutableHashMapMemo { nodeId =>
    val nodeIdx = idToIdx.getOrElse(nodeId, -1)
    if(nodeIdx == -1) {
      Seq.empty[NodeId]
    } else {
      descendantsIdx(nodeIdx).map(nodeIds) // instead of dfs, we use already memoized results
    }
  }

  def descendantsWithDeleted(nodeId: NodeId) = _descendantsWithDeleted(nodeId)
  private val _descendantsWithDeleted: NodeId => Iterable[NodeId] = Memo.mutableHashMapMemo { nodeId =>
    idToIdx.isDefinedAt(nodeId) match {
      case true  =>
        val cs = depthFirstSearchWithStartInCycleDetection(nodeId, (id: NodeId) => children(id) ++ deletedChildren(id))
        if(cs.startInvolvedInCycle) cs else cs.drop(1)
      case false => Seq.empty
    }
  }

  def ancestorsIdx(nodeIdx: Int) = _ancestorsIdx(nodeIdx)
  private val _ancestorsIdx: Int => Array[Int] = Memo.arrayMemo[Array[Int]](n).apply { nodeIdx =>
    depthFirstSearchWithoutStart(nodeIdx, parentsIdx)
  }
  def ancestors(nodeId: NodeId) = _ancestors(nodeId)
  private val _ancestors: NodeId => Seq[NodeId] = Memo.mutableHashMapMemo { nodeId =>
    val nodeIdx = idToIdx.getOrElse(nodeId, -1)
    if(nodeIdx == -1) {
      Seq.empty[NodeId]
    } else {
      ancestorsIdx(nodeIdx).map(nodeIds) // instead of dfs, we use already memoized results
    }
  }

  // IMPORTANT:
  // exactly the same as in the stored procedure
  // when changing things, make sure to change them for the stored procedure as well.
  def can_access_node(userId: NodeId, nodeId: NodeId): Boolean = {
    def can_access_node_recursive(
      userId: NodeId,
      nodeId: NodeId,
      visited: Set[NodeId] = Set.empty
    ): Boolean = {
      if(visited(nodeId)) return false // prevent inheritance cycles

      // is there a membership?
      val levelFromMembership = membershipEdgeIdx(idToIdx(nodeId)).map(edges).collectFirst {
        case Edge.Member(`userId`, EdgeData.Member(level), _) => level
      }
      levelFromMembership match {
        case None        => // if no member edge exists
          // read access level directly from node
          nodesById(nodeId).meta.accessLevel match {
            case NodeAccess.Level(level) => level == AccessLevel.ReadWrite
            case NodeAccess.Inherited    =>
              // recursively inherit permissions from parents. minimum one parent needs to allow access.
              parents(nodeId).exists(
                parentId => can_access_node_recursive(userId, parentId, visited + nodeId)
              )
          }
        case Some(level) =>
          level == AccessLevel.ReadWrite
      }
    }

    // everybody has full access to non-existent nodes
    if(!(nodeIds contains nodeId)) return true
    can_access_node_recursive(userId, nodeId)
  }

  lazy val containmentNeighbours
  : collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ adjacencyList[
    NodeId,
    Edge
    ](containments, _.targetId, _.sourceId)
  // Get connected components by only considering containment edges
  lazy val connectedContainmentComponents: List[Set[NodeId]] = {
    connectedComponents(nodeIds, containmentNeighbours)
  }

  lazy val childDepth: collection.Map[NodeId, Int] = depth(children)
  lazy val parentDepth: collection.Map[NodeId, Int] = depth(parents)

  def depth(next: NodeId => Iterable[NodeId]): collection.Map[NodeId, Int] = {
    val tmpDepths = mutable.HashMap[NodeId, Int]()
    val visited = mutable.HashSet[NodeId]() // to handle cycles
    def getDepth(id: NodeId): Int = {
      tmpDepths.getOrElse(id, {
        if(!visited(id)) {
          visited += id

          val c = next(id)
          val d = if(c.isEmpty) 0 else c.map(getDepth).max + 1
          tmpDepths(id) = d
          d
        } else 0 // cycle
      })
    }

    for(id <- nodeIds if !tmpDepths.isDefinedAt(id)) {
      getDepth(id)
    }
    tmpDepths
  }

  lazy val rootNodes: Array[Int] = {
    // val coveredChildrenx: Set[NodeId] = nodes.filter(n => !hasParents(n.id)).flatMap(n => descendants(n.id))(breakOut)
    val coveredChildren = {
      val a = new Array[Int](n)
      var i = 0
      var j = 0
      while(i < n) {
        if(!hasParentsIdx(i)) {
          val descendants = descendantsIdx(i)
          j = 0
          while(j < descendants.size) {
            a(descendants(j)) = 1
            j += 1
          }
        }
        i += 1
      }
      a
    }

    // val rootNodes = nodes.filter(n => coveredChildren(idToIdx(n.id)) == 0 && (!hasParents(n.id) || involvedInContainmentCycle(n.id))).toSet
    val rootNodesIdx = new mutable.ArrayBuilder.ofInt
    rootNodesIdx.sizeHint(n)
    var i = 0
    while(i < n) {
      assert(coveredChildren(i) == coveredChildren(idToIdx(nodes(i).id)))
      if(coveredChildren(i) == 0 && (!hasParentsIdx(i) || involvedInContainmentCycleIdx(i)))
        rootNodesIdx += i
      i += 1
    }
    rootNodesIdx.result()
  }

  def redundantTree(root: Int, excludeCycleLeafs: Boolean, visited: Array[Int] = new Array[Int](n)): Tree = {
    if(visited(root) == 0 && hasChildrenIdx(root)) {
      visited(root) = 1
      if(excludeCycleLeafs) {
        val nonCycleChildren = childrenIdx(root).filter(idx => visited(idx) == 0)
        if(nonCycleChildren.nonEmpty) {
          Tree.Parent(nodes(root), nonCycleChildren.map(n => redundantTree(n, excludeCycleLeafs, visited))(breakOut))
        }
        else
          Tree.Leaf(nodes(root))
      } else {
        Tree.Parent(nodes(root), childrenIdx(root).map(idx => redundantTree(idx, excludeCycleLeafs, visited))(breakOut))
      }
    }
    else
      Tree.Leaf(nodes(root))
  }

  lazy val redundantForestExcludingCycleLeafs: List[Tree] = {
    rootNodes.map(idx => redundantTree(idx, excludeCycleLeafs = true))(breakOut)
  }
  lazy val redundantForestIncludingCycleLeafs: List[Tree] = {
    rootNodes.map(idx => redundantTree(idx, excludeCycleLeafs = false))(breakOut)
  }

  def channelTree(user: UserId): Seq[Tree] = {
    val channelIndices = pinnedNodeIdx(idToIdx(user))
    val isChannel = new Array[Int](n)
    channelIndices.foreach { isChannel(_) = 1 }

    //TODO: more efficient algorithm? https://en.wikipedia.org/wiki/Reachability#Algorithms
    def reachable(childChannelIdx: Int, parentChannelIdx: Int): Boolean = {
      // child --> ...no other channel... --> parent
      // if child channel is trasitive child of parent channel,
      // without traversing over other channels
      val excludedChannels = new Array[Int](n)
      channelIndices.foreach { channelIdx =>
        excludedChannels(channelIdx) = 1
      }
      excludedChannels(parentChannelIdx) = 0
      depthFirstSearchExcludeExists(childChannelIdx, parentsIdx, exclude = excludedChannels, search = parentChannelIdx)
    }

    val topologicalParents = for {
      child <- channelIndices
      parent <- ancestorsIdx(child)
      if child != parent
      if isChannel(parent) == 1
      if reachable(child, parent)
    } yield Edge.Parent(nodes(child).id, nodes(parent).id)

    val topologicalMinor = Graph(channelIndices.map(nodes), topologicalParents)
    topologicalMinor.lookup.redundantForestExcludingCycleLeafs
  }

  def parentDepths(node: NodeId): Map[Int, Map[Int, Seq[NodeId]]]
  = {
    trait Grouping
    case class Cycle(node: NodeId) extends Grouping
    case class NoCycle(node: NodeId) extends Grouping

    import wust.util.algorithm.{dijkstra}
    type ResultMap = Map[Distance, Map[GroupIdx, Seq[NodeId]]]

    def ResultMap() = Map[Distance, Map[GroupIdx, Seq[NodeId]]]()

    // NodeId -> distance
    val (distanceMap: Map[NodeId, Int], predecessorMap) = dijkstra[NodeId](parents, node)
    val nodesInCycles = distanceMap.keys.filter(involvedInContainmentCycle(_))
    val groupedByCycle = nodesInCycles.groupBy { node => depthFirstSearchWithStartInCycleDetection[NodeId](node, parents).toSet }
    type GroupIdx = Int
    type Distance = Int
    val distanceMapForCycles: Map[NodeId, (GroupIdx, Distance)] =
      groupedByCycle.zipWithIndex.map { case ((group, cycledNodes), groupIdx) =>
        val smallestDistToGroup: Int = group.map(distanceMap).min
        cycledNodes.zip(Stream.continually { (groupIdx, smallestDistToGroup) })
      }.flatten.toMap

    // we want: distance -> (nocycle : Seq[NodeId], cycle1 : Seq[NodeId],...)
    (distanceMap.keys.toSet ++ distanceMapForCycles.keys.toSet).foldLeft(
      ResultMap()) { (result, nodeid) =>
      // in case that the nodeid is inside distanceMapForCycles, it is contained
      // inside a cycle, so we use the smallest distance of the cycle
      val (gId, dist) = if(distanceMapForCycles.contains(nodeid))
                          distanceMapForCycles(nodeid)
                        else
                          (-1, distanceMap(nodeid))

      import monocle.function.At._
      (monocle.Iso.id[ResultMap] composeLens at(dist)).modify { optInnerMap =>
        val innerMap = optInnerMap.getOrElse(Map.empty)
        Some(((monocle.Iso.id[Map[GroupIdx, Seq[NodeId]]] composeLens at(gId)) modify { optInnerSeq =>
          val innerSeq = optInnerSeq.getOrElse(Seq.empty)
          Some(innerSeq ++ Seq(nodeid))
        }) (innerMap))
      }(result)
    }
  }
}

sealed trait Tree {
  def node: Node
  def flatten: List[Node]
}
object Tree {
  case class Parent(node: Node, children: List[Tree]) extends Tree {
    override def flatten = node :: (children.flatMap(_.flatten)(breakOut): List[Node])
  }
  case class Leaf(node: Node) extends Tree {
    override def flatten = node :: Nil
  }
}
