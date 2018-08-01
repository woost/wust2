package wust.graph

import wust.ids._
import wust.util.Memo
import wust.util.algorithm._
import wust.util.collection._

import collection.mutable
import collection.breakOut

object Graph {
  val empty = new Graph(Set.empty, Set.empty)

  def apply(nodes: Iterable[Node] = Nil, edges: Iterable[Edge] = Nil): Graph = {
    new Graph(nodes.toSet, edges.toSet)
  }
}

final case class Graph(nodes: Set[Node], edges: Set[Edge]) {

  def isEmpty: Boolean = nodes.isEmpty
  def nonEmpty: Boolean = !isEmpty
  def size: Int = nodes.size
  def length: Int = size

  override def toString: String = {
    def nodeStr(node: Node) =
      s"${node.data.tpe}(${node.data.str}:${node.id.toBase58.takeRight(3)})"
    s"Graph(${nodes.map(nodeStr).mkString(" ")}, " +
      s"${connectionsByType.values.flatten
        .map(c => s"${c.sourceId.toBase58.takeRight(3)}-${c.data}->${c.targetId.toBase58.takeRight(3)}")
        .mkString(", ")})"
  }

  def toSummaryString = {
    s"Graph(nodes: ${nodes.size}, ${edges.size})"
  }

  lazy val (
    nodeIds: scala.collection.Set[NodeId],
    nodesById: collection.Map[NodeId, Node],
    labeledEdges: collection.Set[Edge.Label],
    containments: collection.Set[Edge.Parent],
    children: collection.Map[NodeId, collection.Set[NodeId]],
    parents: collection.Map[NodeId, collection.Set[NodeId]],
    deletedParents: collection.Map[NodeId, collection.Set[NodeId]],
    deletedChildren: collection.Map[NodeId, collection.Set[NodeId]],
    staticParentIn: collection.Map[NodeId, collection.Set[NodeId]],
    authorshipsByNodeId: collection.Map[NodeId, List[Edge.Author]],
    membershipsByNodeId: collection.Map[NodeId, List[Edge.Member]],
    allUserIds: collection.Set[UserId], //TODO: List?
    allAuthorIds: collection.Set[UserId], //TODO: List?
    channelNodeIds: collection.Set[NodeId], //TODO: List?
    channelIds: collection.Set[NodeId], //TODO: List?
    nodeCreated: collection.Map[NodeId, EpochMilli],
    nodeModified: collection.Map[NodeId, EpochMilli]
  ) = {
    // mutable sets/maps are ~5x faster than immutable ones
    // http://www.lihaoyi.com/post/BenchmarkingScalaCollections.html
    val nodeIds = mutable.HashSet[NodeId]()
    nodeIds.sizeHint(nodes.size)

    val nodesById = mutable.HashMap[NodeId, Node]()
    nodesById.sizeHint(nodes.size)

    val labeledEdges = mutable.HashSet[Edge.Label]()
    val containments = mutable.HashSet[Edge.Parent]()

    val children = mutable
      .HashMap[NodeId, collection.Set[NodeId]]()
      .withDefaultValue(mutable.HashSet.empty[NodeId])
    val parents = mutable
      .HashMap[NodeId, collection.Set[NodeId]]()
      .withDefaultValue(mutable.HashSet.empty[NodeId])
    val deletedParents = mutable
      .HashMap[NodeId, collection.Set[NodeId]]()
      .withDefaultValue(mutable.HashSet.empty[NodeId])
    val deletedChildren = mutable
      .HashMap[NodeId, collection.Set[NodeId]]()
      .withDefaultValue(mutable.HashSet.empty[NodeId])
    val staticParentIn = mutable
      .HashMap[NodeId, collection.Set[NodeId]]()
      .withDefaultValue(mutable.HashSet.empty[NodeId])

    val authorshipsByNodeId = mutable.HashMap[NodeId, List[Edge.Author]]().withDefaultValue(Nil)
    val membershipsByNodeId = mutable.HashMap[NodeId, List[Edge.Member]]().withDefaultValue(Nil)
    val allAuthorIds = mutable.HashSet[UserId]()
    val allUserIds = mutable.HashSet[UserId]()

    val channelNodeIds = mutable.HashSet[NodeId]()
    val channelIds = mutable.HashSet[NodeId]()

    val remorseTime = remorseTimeForDeletedParents
    // loop over edges once
    edges.foreach {
      case e @ Edge.Author(authorId, _, nodeId) =>
        authorshipsByNodeId(nodeId) ::= e
        allAuthorIds += authorId
      case e @ Edge.Member(authorId, _, nodeId) =>
        membershipsByNodeId(nodeId) ::= e
      case e @ Edge.Parent(childId, data, parentId) =>
        data.deletedAt match {
          case None =>
          children(parentId) += childId
          parents(childId) += parentId
          containments += e
          case Some(deletedAt) =>
            //TODO should already be filtered in backend
            if (deletedAt > remorseTime) {
            deletedParents(childId) += parentId
              deletedChildren(parentId) += childId
          }
        }
      case e @ Edge.StaticParentIn(childId, parentId) =>
        staticParentIn(childId) += parentId
      case e: Edge.Label =>
        labeledEdges += e
      case _ =>
    }

    // loop over nodes once
    nodes.foreach { node =>
      val nodeId = node.id
      nodeIds += nodeId
      nodesById(nodeId) = node

      node match {
        case Node.User(id, data, _) =>
          allUserIds += id
          channelNodeIds += data.channelNodeId
          channelIds ++= children(data.channelNodeId)
        case _ =>
      }
    }

    require(nodeIds.size == nodes.size, "nodes are not distinct by id")

    val nodeCreated = mutable.HashMap[NodeId, EpochMilli]().withDefaultValue(EpochMilli.min)
    val nodeModified = mutable.HashMap[NodeId, EpochMilli]().withDefaultValue(EpochMilli.min)
    authorshipsByNodeId.foreach {
      case (nodeId, authorEdges) =>
        val sortedAuthorEdges = authorEdges.sortBy(_.data.timestamp)
        authorshipsByNodeId(nodeId) = sortedAuthorEdges
        nodeCreated(nodeId) = sortedAuthorEdges.headOption.fold(EpochMilli.min)(_.data.timestamp)
        nodeModified(nodeId) = sortedAuthorEdges.lastOption.fold(EpochMilli.min)(_.data.timestamp) //TODO: lastOption O(n)
    }

    (
      nodeIds,
      nodesById,
      labeledEdges,
      containments,
      children,
      parents,
      deletedParents,
      deletedChildren,
      staticParentIn,
      authorshipsByNodeId,
      membershipsByNodeId,
      allAuthorIds,
      allUserIds,
      channelNodeIds,
      channelIds,
      nodeCreated,
      nodeModified
    )
  }

  private lazy val connectionsByType: Map[EdgeData.Type, Set[Edge]] =
    edges.groupBy(_.data.tpe).map { case (tpe, conns) => tpe -> conns }
  private lazy val connectionsByTypeF: EdgeData.Type => Set[Edge] =
    connectionsByType.withDefaultValue(Set.empty)

  def children(node: Node): collection.Set[NodeId] = children(node.id)
  def parents(node: Node): collection.Set[NodeId] = parents(node.id)

  val authorIds: NodeId => List[UserId] = Memo.mutableHashMapMemo { nodeId =>
    authorshipsByNodeId(nodeId).map(_.userId)
  }
  def authorIds(node: Node): List[UserId] = authorIds(node.id)
  val authors: NodeId => List[Node.User] = Memo.mutableHashMapMemo { nodeId =>
    authorshipsByNodeId(nodeId).map(a => nodesById(a.userId).asInstanceOf[Node.User])
  }
  def authors(node: Node): List[Node.User] = authors(node.id)
  def nodeCreated(node: Node): EpochMilli = nodeCreated(node.id)
  def nodeModified(node: Node): EpochMilli = nodeModified(node.id)

  val authorsIn: NodeId => List[Node.User] = Memo.mutableHashMapMemo { parentId =>
    (parentId :: descendants(parentId).toList).flatMap(authors)
  }

  def members(node: Node): List[Node.User] = members(node.id)
  val members: NodeId => List[Node.User] = Memo.mutableHashMapMemo { nodeId =>
    membershipsByNodeId(nodeId).map(a => nodesById(a.userId).asInstanceOf[Node.User])
  }

  def isDeletedNow(node: Node, parentIds: Set[NodeId]): Boolean = isDeletedNow(node.id, parentIds)
  def isDeletedNow(nodeId: NodeId, parentIds: Set[NodeId]): Boolean = {
    parentIds subsetOf deletedParents(nodeId)
  }

  lazy val channels: collection.Set[Node] = channelIds.map(nodesById)
  lazy val withoutChannels: Graph = this.filterNot(channelIds ++ channelNodeIds)
  lazy val onlyAuthors: Graph =
    this.filterNot((allUserIds -- allAuthorIds).map(id => UserId.raw(id)))

  private val remorseTimeForDeletedParents: EpochMilli = EpochMilli(EpochMilli.now - (24 * 3600 * 1000))
  def pageContentWithAuthors(page: Page): Graph = {
    val pageChildren = page.parentIds.flatMap(descendants)
    this.filter(pageChildren.toSet ++ pageChildren.flatMap(authorIds) ++ page.parentIds)
  }

  def pageContent(page: Page): Graph = {
    val pageChildren = page.parentIds.flatMap(descendants)
    this.filter(pageChildren.toSet)
  }

  val directNodeTags: ((NodeId, Set[NodeId])) => Set[Node] = Memo.mutableHashMapMemo {
    ( ( nodeId: NodeId, parentIds: Set[NodeId] ) =>
          (parents(nodeId).toSet -- (parentIds - nodeId)) // "- nodeId" reveals self-loops with page-parent
            .map(nodesById)
      ).tupled
  }

  val transitiveNodeTags: ((NodeId, Set[NodeId])) => Set[Node] = Memo.mutableHashMapMemo {
    // TODO: sort by depth
    { ( nodeId: NodeId, parentIds: Set[NodeId] ) =>
      val transitivePageParents = parentIds.flatMap(ancestors)
      (ancestors(nodeId).toSet -- parentIds -- transitivePageParents -- parents(nodeId))
        .map(nodesById)
    }.tupled
  }

  lazy val chronologicalNodesAscending: IndexedSeq[Node] =
    nodes.toIndexedSeq.sortBy(n => nodeCreated(n))

  lazy val contentNodes: Iterable[Node.Content] = nodes.collect { case p: Node.Content => p }
  lazy val nodeIdsTopologicalSortedByChildren: Iterable[NodeId] =
    nodeIds.topologicalSortBy(children)
  lazy val nodeIdsTopologicalSortedByParents: Iterable[NodeId] = nodeIds.topologicalSortBy(parents)
  lazy val allParentIds: collection.Set[NodeId] = containments.map(_.targetId)
  lazy val allSourceIds: collection.Set[NodeId] = containments.map(_.sourceId)
  lazy val toplevelNodeIds: Set[NodeId] = nodeIds.toSet -- allSourceIds
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
    getParents(child).contains(possibleParent)
  def inDescendantAncestorRelation(descendent: NodeId, possibleAncestor: NodeId): Boolean =
    ancestors(descendent).contains(possibleAncestor)

  def isStaticParentIn(id: NodeId, parentId: NodeId): Boolean = staticParentIn(id).contains(parentId)
  def isStaticParentIn(id: NodeId, parentIds: Iterable[NodeId]): Boolean = {
    val staticInParents = staticParentIn(id)
      parentIds.exists(parentId => staticInParents.contains(parentId))
  }

  // There are cases where the key is not present and cases where the set is empty
  def hasChildren(node: NodeId): Boolean = children.contains(node) && children(node).nonEmpty
  def hasParents(node: NodeId): Boolean = parents.contains(node) && parents(node).nonEmpty
  def getChildren(nodeId: NodeId): collection.Set[NodeId] =
    if (children.contains(nodeId)) children(nodeId) else Set.empty[NodeId]
  def getParents(nodeId: NodeId): collection.Set[NodeId] =
    if (parents.contains(nodeId)) parents(nodeId) else Set.empty[NodeId]
  def getChildrenOpt(nodeId: NodeId): Option[collection.Set[NodeId]] =
    if (hasChildren(nodeId)) Some(children(nodeId)) else None
  def getParentsOpt(nodeId: NodeId): Option[collection.Set[NodeId]] =
    if (hasParents(nodeId)) Some(parents(nodeId)) else None

  def isChildOf(childId:NodeId, parentId: NodeId): Boolean = parents(childId) contains parentId
  def isChildOfAny(childId:NodeId, parentIds: Iterable[NodeId]): Boolean = {
    val p = parents(childId)
    parentIds.exists(p.contains)
  }

  private lazy val connectionDefaultNeighbourhood: collection.Map[NodeId, collection.Set[Edge]] =
    defaultNeighbourhood(nodeIds, Set.empty[Edge])
  lazy val incomingEdges
    : collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++
    directedIncidenceList[NodeId, Edge](edges, _.targetId)
  lazy val outgoingEdges
    : collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++
    directedIncidenceList[NodeId, Edge](edges, _.sourceId)
  lazy val incidentEdges
    : collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++ incidenceList[
    NodeId,
    Edge
  ](edges, _.sourceId, _.targetId)

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

  private lazy val nodeDefaultDegree = defaultNeighbourhood(nodeIds, 0)
  lazy val connectionDegree: collection.Map[NodeId, Int] = nodeDefaultDegree ++
    degreeSequence[NodeId, Edge](labeledEdges, _.targetId, _.sourceId)
  lazy val containmentDegree: collection.Map[NodeId, Int] = nodeDefaultDegree ++
    degreeSequence[NodeId, Edge](containments, _.targetId, _.sourceId)

  def fullDegree(nodeId: NodeId): Int = connectionDegree(nodeId) + containmentDegree(nodeId)

  def involvedInContainmentCycle(id: NodeId): Boolean = {
    children.get(id).exists(_.exists(child => depthFirstSearch(child, children).exists(_ == id)))
  }
  // TODO: maybe fast involved-in-cycle-algorithm?
  // breadth-first-search starting at successors and another one starting at predecessors in different direction.
  // When both queues contain the same elements, we can stop, because we found a cycle
  // Even better:
  // lazy val involvedInContainmentCycle:Set[NodeId] = all nodes involved in a cycle

  def descendants(nodeId: NodeId) = _descendants(nodeId)
  private val _descendants: NodeId => Iterable[NodeId] = Memo.mutableHashMapMemo { nodeId =>
    nodesById.isDefinedAt(nodeId) match {
      case true =>
        val cs = depthFirstSearch(nodeId, children)
        if (cs.startInvolvedInCycle) cs else cs.drop(1)
      case false => Seq.empty
    }
  }
  //TODO: rename to transitiveParentIds:Iterable[NodeId]
  def ancestors(nodeId: NodeId) = _ancestors(nodeId)
  private val _ancestors: NodeId => Seq[NodeId] = Memo.mutableHashMapMemo { nodeId =>
    if (nodesById.keySet.contains(nodeId)) {
      val p = depthFirstSearch(nodeId, parents)
      // ancestors in a DAG are usually without the start-element --> DFS.drop(1)
      // if the start element is involved in a cycle, we keep it, since it is an ancestor of itself
      if (p.startInvolvedInCycle) p.toSeq else p.drop(1).toSeq
    } else {
      Seq.empty
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
      if (visited(nodeId)) return false // prevent inheritance cycles

      // is there a membership?
      val levelFromMembership = membershipsByNodeId(nodeId).collectFirst {
        case Edge.Member(`userId`, EdgeData.Member(level), _) => level
      }
      levelFromMembership match {
        case None => // if no member edge exists
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
    if (!(nodeIds contains nodeId)) return true
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

  def -(nodeId: NodeId): Graph = removeNodes(nodeId :: Nil)
  def -(edge: Edge): Graph = copy(
    edges = edges - edge
  )

  def filter(p: NodeId => Boolean): Graph = {
    // we only want to call p once for each node
    // and not trigger the pre-caching machinery of nodeIds
    val filteredNodes = nodes.filter(n => p(n.id))
    if(filteredNodes.size == nodes.size) this
    else {
      val filteredNodeIds = filteredNodes.map(_.id)
      copy(
        nodes = filteredNodes,
        edges = edges.filter(e => filteredNodeIds(e.sourceId) && filteredNodeIds(e.targetId))
      )
    }
  }

  def filterNot(p: NodeId => Boolean): Graph = filter(id => !p(id))

  def removeNodes(nids: Iterable[NodeId]): Graph = filterNot(nids.toSet)

  def removeConnections(es: Iterable[Edge]): Graph = copy(edges = edges -- es)
  def addNodes(ns: Iterable[Node]): Graph = copy(nodes = nodes ++ ns)
  def addConnections(es: Iterable[Edge]): Graph =
    copy(edges = edges ++ es.filter(e => nodeIds(e.sourceId) && nodeIds(e.targetId)))

  def applyChanges(c: GraphChanges): Graph = {
    val addNodeIds = c.addNodes.map(_.id)
    copy(
      nodes = nodes.filterNot(n => addNodeIds(n.id)) ++ c.addNodes,
      edges = edges ++ c.addEdges -- c.delEdges
    )
  }

  def +(node: Node): Graph = copy(nodes = nodes + node)
  def +(edge: Edge): Graph = copy(
    edges = edges + edge
  )

  def +(that: Graph): Graph = copy(
    nodes = this.nodes ++ that.nodes,
    edges = this.edges ++ that.edges
  )

  lazy val consistent: Graph = {
    val filteredEdges = edges.filter(e => nodeIds(e.sourceId) && nodeIds(e.targetId))

    if (edges.size != filteredEdges.size)
      copy(edges = filteredEdges)
    else
      this
  }

  lazy val childDepth: collection.Map[NodeId, Int] = depth(children)
  lazy val parentDepth: collection.Map[NodeId, Int] = depth(parents)
  lazy val parentDepthMinCycles: collection.Map[NodeId, Int] = depthMinCycles(parents)

  def depth(next: NodeId => Iterable[NodeId]): collection.Map[NodeId, Int] = {
    val tmpDepths = mutable.HashMap[NodeId, Int]()
    val visited = mutable.HashSet[NodeId]() // to handle cycles
    def getDepth(id: NodeId): Int = {
      tmpDepths.getOrElse(id, {
        if (!visited(id)) {
          visited += id

          val c = next(id)
          val d = if (c.isEmpty) 0 else c.map(getDepth).max + 1
          tmpDepths(id) = d
          d
        } else 0 // cycle
      })
    }

    for (id <- nodeIds if !tmpDepths.isDefinedAt(id)) {
      getDepth(id)
    }
    tmpDepths
  }

  def redundantTree(root:Node, visited:Set[Node] = Set.empty):Tree = {
    if(!visited(root) && hasChildren(root.id))
      Tree.Parent(root, children(root).map(n => redundantTree(nodesById(n), visited + root))(breakOut))
    else
      Tree.Leaf(root)
  }
def redundantForest:List[Tree] = {
    val roots = nodes.filterNot(n => hasParents(n.id))
    roots.map(n => redundantTree(n))(breakOut)
}
  def depthMinCycles(next: NodeId => Iterable[NodeId]): collection.Map[NodeId, Int] = {
    // inside sbt:
    // project graphJVM
    // test
    val tmpDepths = mutable.HashMap[NodeId, Int]()
    val visited = mutable.HashSet[NodeId]() // to handle cycles
    def getDepth(id: NodeId): Option[Int] = {
      tmpDepths.get(id) match {
        case Some(d) => Some(d)
        case None => {
          if (!visited(id)) {
            visited += id
            val nodeParents = next(id)
            val d : Option[Int] = if (nodeParents.isEmpty)
                      Some(0)
                    else {
                      val depths = nodeParents.map(getDepth).zip(nodeParents)
                      // e.g. [(Some(D), NodeId), (None, NodeId)]
                      val depthsWithNoneForCircles = depths.map(_ match {
                                   case (Some(d), _) => Some(d + 1)
                                   case (None, node) => None /* return none */
                                 }  )
                      if(depths.size == 0)
                        Some(0)
                      else if(depthsWithNoneForCircles.exists(_ == None)) {
                        val idsWithCycles = depths.filter(_._1 == None).map(_._2)
                        val idsWithCyclesParents = idsWithCycles.flatMap(k => parents.get(k).map((k, _))).toMap
                        println("=========================")
                        println(idsWithCyclesParents)
                        val depthMap = depth(idsWithCyclesParents)
                        // depthsWithNoneForCircles.max
                          // ++ cycledDepths
                        Some(10) // depthsWithNoneForCircles.min
                      }
                      else
                        depthsWithNoneForCircles.max
                    }
            d match {
              case Some(d) => tmpDepths(id) = d
              case None => tmpDepths(id) = 0
            }
            d
          }
          else
            None // cycle
        }
      }
    }
      for (id <- nodeIds if !tmpDepths.isDefinedAt(id)) {
          getDepth(id)
      }
      tmpDepths
  }

  trait Grouping
  case class Cycle(node : NodeId) extends Grouping
  case class NoCycle(node : NodeId) extends Grouping
  def parentDepths(node : NodeId) // : Map[NodeId, Int] 
    = {
    import wust.util.algorithm.{dijkstra}
    type ResultMap = Map[ Distance, Map[ GroupIdx, Seq [ NodeId ] ] ]
    def ResultMap() = Map[ Distance, Map[ GroupIdx, Seq [ NodeId ] ] ]()
    // NodeId -> distance
    val (distanceMap : Map[NodeId, Int], predecessorMap) = dijkstra(parents, node)
    val nodesInCycles = distanceMap.keys.filter(involvedInContainmentCycle(_))
    val groupedByCycle = nodesInCycles.groupBy{ node => depthFirstSearch(node, parents).toSet }
    type GroupIdx = Int
    type Distance = Int
    val distanceMapForCycles : Map[NodeId, (GroupIdx, Distance)] =
      groupedByCycle.zipWithIndex.map { case ((group, cycledNodes), groupIdx) =>
      val smallestDistToGroup : Int = group.map(distanceMap).min
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
      (monocle.Iso.id[ ResultMap ] composeLens at(dist)).modify { optInnerMap =>
        val innerMap = optInnerMap.getOrElse(Map.empty)
        Some(((monocle.Iso.id [ Map [ GroupIdx, Seq [ NodeId ]] ] composeLens at(gId)) modify { optInnerSeq =>
                val innerSeq = optInnerSeq.getOrElse(Seq.empty)
                Some(innerSeq ++ Seq(nodeid))
        })(innerMap))
      }(result)
    }
  }
}
sealed trait Tree {def node:Node}
object Tree {
  case class Parent(node:Node, children:List[Tree]) extends Tree
  case class Leaf(node:Node) extends Tree
}
