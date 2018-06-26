package wust.graph

import wust.ids._
import wust.util.Memo
import wust.util.algorithm._
import wust.util.collection._

import collection.mutable
import collection.breakOut

case class NodeMeta(deleted: DeletedDate, joinDate: JoinDate, joinLevel: AccessLevel)
object NodeMeta {
  def User = NodeMeta(DeletedDate.NotDeleted, JoinDate.Never, AccessLevel.Read)
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
    def apply(id: NodeId, data: NodeData.Content): Content = {
      new Content(id, data, NodeMeta(DeletedDate.NotDeleted, JoinDate.Never, AccessLevel.ReadWrite))
    }
    def apply(data: NodeData.Content): Content = {
      new Content(NodeId.fresh, data, NodeMeta(DeletedDate.NotDeleted, JoinDate.Never, AccessLevel.ReadWrite))
    }
  }

  implicit def AsUserInfo(user: User): UserInfo = UserInfo(user.id, user.data.name, user.data.channelNodeId)
}

sealed trait Edge {
  def sourceId: NodeId
  def targetId: NodeId
  def data: EdgeData
}

object Edge {
  sealed trait Content extends Edge

  //TODO should have constructor: level: AccessLevel
  case class Member(userId: UserId, data: EdgeData.Member, groupId: NodeId) extends Edge {
    def sourceId = userId
    def targetId = groupId
  }
  //TODO should have constructor: timestamp: Timestamp
  case class Author(userId: UserId, data: EdgeData.Author, nodeId: NodeId) extends Edge {
    def sourceId = userId
    def targetId = nodeId
  }

  case class Parent(childId: NodeId, parentId: NodeId) extends Content {
    def sourceId = childId
    def targetId = parentId
    def data = EdgeData.Parent
  }

  //TODO should have constructor: label: String
  case class Label(sourceId: NodeId, data: EdgeData.Label, targetId: NodeId) extends Content
}

object Graph {
  def empty = new Graph(Set.empty, Set.empty)

  def apply( nodes: Iterable[Node]  = Nil, edges: Iterable[Edge]  = Nil ): Graph = {
    new Graph( nodes.toSet, edges.toSet )
  }
}

final case class Graph(nodes: Set[Node], edges: Set[Edge]) {

  def isEmpty: Boolean = nodes.isEmpty
  def nonEmpty: Boolean = !isEmpty
  def size: Int = nodes.size
  def length: Int = size

  override def toString: String = {
    def nodeStr(node:Node) = s"${node.data.tpe}(${node.data.str}:${node.id.toCuidString.takeRight(4)})"
    s"Graph(${nodes.map(nodeStr).mkString(" ")}, " +
      s"${connectionsByType.values.flatten.map(c => s"${c.sourceId.toCuidString.takeRight(4)}-${c.data}->${c.targetId.toCuidString.takeRight(4)}").mkString(", ")})"
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
      authors: collection.Map[NodeId, List[Edge.Author]],
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

      val children = mutable.HashMap[NodeId, collection.Set[NodeId]]().withDefaultValue(mutable.HashSet.empty[NodeId])
      val parents = mutable.HashMap[NodeId, collection.Set[NodeId]]().withDefaultValue(mutable.HashSet.empty[NodeId])

      val authors = mutable.HashMap[NodeId, List[Edge.Author]]().withDefaultValue(Nil)
      val allAuthorIds = mutable.HashSet[UserId]()
      val allUserIds = mutable.HashSet[UserId]()

      val channelNodeIds = mutable.HashSet[NodeId]()
      val channelIds = mutable.HashSet[NodeId]()

      // loop over edges once
      edges.foreach {
        case e@Edge.Author(authorId, _, nodeId) =>
          authors(nodeId) ::= e
          allAuthorIds += authorId
        case e@Edge.Parent(childId, parentId) =>
          children(parentId) += childId
          parents(childId) += parentId
          containments += e
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
          case Node.User(id, _, _) =>
            allUserIds += id
          case Node.Content(id, data, _) =>
            data match {
              case NodeData.Channels =>
                channelNodeIds += id
                channelIds ++= children(id)
              case _ =>
            }
          case _ =>
        }
      }

      require(nodeIds.size == nodes.size, "nodes are not distinct by id")

      val nodeCreated = mutable.HashMap[NodeId, EpochMilli]().withDefaultValue(EpochMilli.min)
      val nodeModified = mutable.HashMap[NodeId, EpochMilli]().withDefaultValue(EpochMilli.min)
      authors.foreach {
        case (nodeId, authorEdges) =>
          val sortedAuthorEdges = authorEdges.sortBy(_.data.timestamp)
          authors(nodeId) = sortedAuthorEdges
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
        authors,
        allAuthorIds,
        allUserIds,
        channelNodeIds,
        channelIds,
        nodeCreated,
        nodeModified
      )
  }


  private lazy val connectionsByType: Map[EdgeData.Type, Set[Edge]] = edges.groupBy(_.data.tpe).map{case (tpe,conns) => tpe -> conns}
  private lazy val connectionsByTypeF: EdgeData.Type => Set[Edge] = connectionsByType.withDefaultValue(Set.empty)

  def children(node:Node):collection.Set[NodeId] = children(node.id)
  def parents(node:Node):collection.Set[NodeId] = parents(node.id)

  def authors(node: Node): List[Edge.Author] = authors(node.id)
  def nodeCreated(node: Node): EpochMilli = nodeCreated(node.id)
  def nodeModified(node: Node): EpochMilli = nodeModified(node.id)


  lazy val channels:collection.Set[Node] = channelIds.map(nodesById).filterNot(_.meta.deleted.isNowDeleted)
  lazy val withoutChannels:Graph = this.filterNot(channelIds ++ channelNodeIds)
  lazy val onlyAuthors:Graph = this.filterNot((allUserIds -- allAuthorIds).map(id => UserId.raw(id)))
  lazy val content:Graph = this.filterNot(channelIds ++ channelNodeIds ++ (allUserIds -- allAuthorIds).map(id => UserId.raw(id)))

  lazy val chronologicalNodesAscending: IndexedSeq[Node] = nodes.toIndexedSeq.sortBy(n => nodeCreated(n))

  lazy val contentNodes: Iterable[Node.Content] = nodes.collect{case p: Node.Content => p}
  lazy val nodeIdsTopologicalSortedByChildren:Iterable[NodeId] = nodeIds.topologicalSortBy(children)
  lazy val nodeIdsTopologicalSortedByParents:Iterable[NodeId] = nodeIds.topologicalSortBy(parents)
  lazy val allParentIds: collection.Set[NodeId] = containments.map(_.targetId)
  lazy val allSourceIds: collection.Set[NodeId] = containments.map(_.sourceId)
  lazy val toplevelNodeIds: Set[NodeId] = nodeIds.toSet -- allSourceIds
  lazy val allParentIdsTopologicallySortedByChildren:Iterable[NodeId] = allParentIds.topologicalSortBy(children)

  private lazy val nodeDefaultNeighbourhood: collection.Map[NodeId, Set[NodeId]] = defaultNeighbourhood(nodeIds, Set.empty[NodeId])
  lazy val successorsWithoutParent: collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ directedAdjacencyList[NodeId, Edge, NodeId](labeledEdges, _.sourceId, _.targetId)
  lazy val predecessorsWithoutParent: collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ directedAdjacencyList[NodeId, Edge, NodeId](labeledEdges, _.targetId, _.sourceId)
  lazy val neighboursWithoutParent: collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ adjacencyList[NodeId, Edge](labeledEdges, _.targetId, _.sourceId)


  def inChildParentRelation(child: NodeId, possibleParent: NodeId): Boolean = getParents(child).contains(possibleParent)
  def inDescendantAncestorRelation(descendent: NodeId, possibleAncestor: NodeId): Boolean = ancestors(descendent).exists(_ == possibleAncestor)

  // There are cases where the key is not present and cases where the set is empty
  def hasChildren(node: NodeId): Boolean = children.contains(node) && children(node).nonEmpty
  def hasParents(node: NodeId): Boolean = parents.contains(node) && parents(node).nonEmpty
  def getChildren(nodeId: NodeId): collection.Set[NodeId] = if(children.contains(nodeId)) children(nodeId) else Set.empty[NodeId]
  def getParents(nodeId: NodeId): collection.Set[NodeId] = if(parents.contains(nodeId)) parents(nodeId) else Set.empty[NodeId]
  def getChildrenOpt(nodeId: NodeId): Option[collection.Set[NodeId]] = if(hasChildren(nodeId)) Some(children(nodeId)) else None
  def getParentsOpt(nodeId: NodeId): Option[collection.Set[NodeId]] = if(hasParents(nodeId)) Some(parents(nodeId)) else None

  private lazy val connectionDefaultNeighbourhood: collection.Map[NodeId, collection.Set[Edge]] = defaultNeighbourhood(nodeIds, Set.empty[Edge])
  lazy val incomingConnections: collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++
    directedIncidenceList[NodeId, Edge](labeledEdges, _.targetId)
  lazy val outgoingConnections: collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++
    directedIncidenceList[NodeId, Edge](labeledEdges, _.sourceId)
  lazy val incidentConnections: collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++ incidenceList[NodeId, Edge](labeledEdges, _.sourceId, _.targetId)

  lazy val incidentParentContainments: collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++ directedIncidenceList[NodeId, Edge](containments, _.sourceId)
  lazy val incidentChildContainments: collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++ directedIncidenceList[NodeId, Edge](containments, _.targetId)
  lazy val incidentContainments: collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++ incidenceList[NodeId, Edge](containments, _.targetId, _.sourceId)

  private lazy val nodeDefaultDegree = defaultNeighbourhood(nodeIds,0)
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
  // Also provide ancestors:Iterable[Node]?
  def ancestors(nodeId: NodeId) = _ancestors(nodeId)
  private val _ancestors: NodeId => Iterable[NodeId] = Memo.mutableHashMapMemo { nodeId =>
    if (nodesById.keySet.contains(nodeId)) {
      val p = depthFirstSearch(nodeId, parents)
      if (p.startInvolvedInCycle) p else p.drop(1)
    } else {
      Seq.empty
    }
  }

  lazy val containmentNeighbours: collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ adjacencyList[NodeId, Edge](containments, _.targetId, _.sourceId)
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
  def addConnections(es: Iterable[Edge]): Graph = copy(edges = edges ++ es.filter(e => nodeIds(e.sourceId) && nodeIds(e.targetId)))

  def applyChanges(c: GraphChanges): Graph = {
    val updatedIds = c.updateNodes.map(_.id)
    copy(
      nodes = (nodes.filterNot(n => updatedIds(n.id)) ++ c.addNodes ++ c.updateNodes).map {
        case p:Node.Content if c.delNodes(p.id) =>
          p.copy(meta = p.meta.copy(deleted = DeletedDate.Deleted.now))
        case p => p
      },
      edges = edges ++ c.addEdges -- c.delEdges
    )
  }

  def +(node: Node): Graph = copy(nodes = nodes + node)
  def +(edge: Edge): Graph = copy(
    edges = edges + edge
  )

  def +(that: Graph): Graph = copy (
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
