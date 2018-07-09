package wust.graph

import wust.ids._
import wust.util.Memo
import wust.util.algorithm._
import wust.util.collection._

import collection.mutable
import collection.breakOut

case class NodeMeta(accessLevel: NodeAccess)
object NodeMeta {
  //TODO a user should NOT have NodeMeta. We cannot delete users like normal
  //posts and what does join and accesslevel actually mean in the context of a
  //user?
  def User = NodeMeta(NodeAccess.Level(AccessLevel.Restricted))
}

sealed trait Node {
  def id: NodeId
  def data: NodeData
  def meta: NodeMeta
}

object Node {
  // TODO: we cannot set the nodemeta here, but there is changeable data in the db class
  case class User(id: UserId, data: NodeData.User, meta: NodeMeta) extends Node
  case class Content(id: NodeId, data: NodeData.Content, meta: NodeMeta) extends Node
  object Content {
    private val defaultMeta = NodeMeta(NodeAccess.Inherited)

    def apply(id: NodeId, data: NodeData.Content): Content = {
      new Content(id, data, defaultMeta)
    }
    def apply(data: NodeData.Content): Content = {
      new Content(NodeId.fresh, data, defaultMeta)
    }
  }

  implicit def AsUserInfo(user: User): UserInfo =
    UserInfo(user.id, user.data.name, user.data.channelNodeId)
}

sealed trait Edge {
  def sourceId: NodeId
  def targetId: NodeId
  def data: EdgeData
}

object Edge {
  //TODO: should Edge have a equals and hashcode depending only on sourceid, targetid and data.str?
  sealed trait Content extends Edge

  //TODO should have constructor: level: AccessLevel // or not: this makes it less extensible if you add fields to EdgeData
  case class Member(userId: UserId, data: EdgeData.Member, groupId: NodeId) extends Edge {
    def sourceId = userId
    def targetId = groupId
  }
  //TODO should have constructor: timestamp: Timestamp // or not: this makes it less extensible if you add fields to EdgeData
  case class Author(userId: UserId, data: EdgeData.Author, nodeId: NodeId) extends Edge {
    def sourceId = userId
    def targetId = nodeId
  }

  case class Parent(childId: NodeId, parentId: NodeId) extends Content {
    def sourceId = childId
    def targetId = parentId
    def data = EdgeData.Parent
  }

  case class DeletedParent(childId: NodeId, data: EdgeData.DeletedParent, parentId: NodeId) extends Content {
    def sourceId = childId
    def targetId = parentId
  }

  //TODO should have constructor: label: String
  case class Label(sourceId: NodeId, data: EdgeData.Label, targetId: NodeId) extends Content
}

object Graph {
  def empty = new Graph(Set.empty, Set.empty)

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
      s"${node.data.tpe}(${node.data.str}:${node.id.toCuidString.takeRight(4)})"
    s"Graph(${nodes.map(nodeStr).mkString(" ")}, " +
      s"${connectionsByType.values.flatten
        .map(c => s"${c.sourceId.toCuidString.takeRight(4)}-${c.data}->${c.targetId.toCuidString.takeRight(4)}")
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

    val authorshipsByNodeId = mutable.HashMap[NodeId, List[Edge.Author]]().withDefaultValue(Nil)
    val membershipsByNodeId = mutable.HashMap[NodeId, List[Edge.Member]]().withDefaultValue(Nil)
    val allAuthorIds = mutable.HashSet[UserId]()
    val allUserIds = mutable.HashSet[UserId]()

    val channelNodeIds = mutable.HashSet[NodeId]()
    val channelIds = mutable.HashSet[NodeId]()

    // loop over edges once
    edges.foreach {
      case e @ Edge.Author(authorId, _, nodeId) =>
        authorshipsByNodeId(nodeId) ::= e
        allAuthorIds += authorId
      case e @ Edge.Member(authorId, _, nodeId) =>
        membershipsByNodeId(nodeId) ::= e
      case e @ Edge.Parent(childId, parentId) =>
        children(parentId) += childId
        parents(childId) += parentId
        containments += e
      case e @ Edge.DeletedParent(childId,_ , parentId) =>
        deletedParents(childId) += parentId
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

  def isDeletedNow(node: Node, parentIds: Set[NodeId]):Boolean = isDeletedNow(node.id, parentIds)
  def isDeletedNow(nodeId: NodeId, parentIds: Set[NodeId]):Boolean = {
    parentIds subsetOf deletedParents(nodeId)
  }


  lazy val channels: collection.Set[Node] =
    channelIds.map(nodesById)
  lazy val withoutChannels: Graph = this.filterNot(channelIds ++ channelNodeIds)
  lazy val onlyAuthors: Graph =
    this.filterNot((allUserIds -- allAuthorIds).map(id => UserId.raw(id)))
  def content(page: Page): Graph = {
    val pageParents = page.parentIds.flatMap(ancestors)
    this.filterNot(
      channelIds ++ channelNodeIds ++ page.parentIds ++ pageParents ++ (allUserIds -- allAuthorIds)
        .map(id => UserId.raw(id))
    )
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
    ancestors(descendent).exists(_ == possibleAncestor)

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
  def can_access_node(userId:NodeId, nodeId:NodeId):Boolean = {
    def can_access_node_recursive(userId:NodeId, nodeId:NodeId, visited:Set[NodeId] = Set.empty):Boolean = {
      if(visited(nodeId)) return false // prevent inheritance cycles

      // is there a membership?
      val levelFromMembership = membershipsByNodeId(nodeId).collectFirst{ case Edge.Member(`userId`, EdgeData.Member(level), _) => level }
      levelFromMembership match {
        case None => // if no member edge exists
          // read access level directly from node
          nodesById(nodeId).meta.accessLevel match {
            case NodeAccess.Level(level) => level == AccessLevel.ReadWrite
            case NodeAccess.Inherited =>
              // recursively inherit permissions from parents. minimum one parent needs to allow access.
              parents(nodeId).exists(parentId => can_access_node_recursive(userId, parentId, visited + nodeId))
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

  def -(nodeId: NodeId): Graph = removeNodes(nodeId :: Nil)
  def -(edge: Edge): Graph = copy(
    edges = edges - edge
  )

  def filter(p: NodeId => Boolean): Graph = {
    copy(
      nodes = nodes.filter(n => p(n.id)),
      edges = edges.filter(e => p(e.sourceId) && p(e.targetId))
    )
  }

  def filterNot(p: NodeId => Boolean): Graph = filter(id => !p(id))

  def removeNodes(nids: Iterable[NodeId]): Graph = filterNot(nids.toSet)

  def removeConnections(es: Iterable[Edge]): Graph = copy(edges = edges -- es)
  def addNodes(ns: Iterable[Node]): Graph = copy(nodes = nodes ++ ns)
  def addConnections(es: Iterable[Edge]): Graph =
    copy(edges = edges ++ es.filter(e => nodeIds(e.sourceId) && nodeIds(e.targetId)))

  def applyChanges(c: GraphChanges): Graph =     copy(
      nodes = nodes ++ c.addNodes,
      edges = edges ++ c.addEdges -- c.delEdges
    )


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
}
