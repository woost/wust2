package wust.graph

import wust.ids._
import wust.util.Memo
import wust.util.algorithm._
import wust.util.collection._
import cuid.Cuid
import wust.graph.Edge.Author
import wust.ids.EdgeData.{Member, Type}

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
  lazy val nodeIds: scala.collection.Set[NodeId] = {
    val ids = mutable.HashSet[NodeId]()
    ids.sizeHint(nodes.size)
    nodes.foreach { ids += _.id }
    ids
  }
  require(nodeIds.size == nodes.size)

  lazy val nodesById: collection.Map[NodeId, Node] = nodes.by(_.id)
  lazy val connectionsByType: Map[EdgeData.Type, Set[Edge]] = edges.groupBy(_.data.tpe).map{case (tpe,conns) => tpe -> conns}
  lazy val isEmpty: Boolean = nodes.isEmpty
  lazy val nonEmpty: Boolean = !isEmpty
  lazy val size: Int = nodes.size
  lazy val length: Int = size

  private lazy val connectionsByTypeF: EdgeData.Type => Set[Edge] = connectionsByType.withDefaultValue(Set.empty)

  def authors(node: Node): collection.Set[UserId] = authors(node.id)
  private lazy val nodeDefaultNeighbourhood: collection.Map[NodeId, Set[NodeId]] = defaultNeighbourhood(nodeIds, Set.empty[NodeId])
  private lazy val nodeDefaultAuthors: collection.Map[NodeId, collection.Set[UserId]] = defaultNeighbourhood(nodeIds, Set.empty[UserId])
  lazy val authors: collection.Map[NodeId, collection.Set[UserId]] = nodeDefaultAuthors ++ directedAdjacencyList[NodeId, Edge.Author, UserId](authorships, _.nodeId, _.userId)
  lazy val allAuthorIds:collection.Set[UserId] = authors.values.flatten.toSet
  lazy val allUserIds:collection.Set[UserId] = nodesById.values.collect{case Node.User(id, _, _) => id}.toSet
  private lazy val connectionDefaultAuthorNeighbourhood: collection.Map[NodeId, collection.Set[Edge.Author]] = defaultNeighbourhood(nodeIds, Set.empty[Edge.Author])
  lazy val authorEdges: collection.Map[NodeId, collection.Set[Edge.Author]] = connectionDefaultAuthorNeighbourhood ++ directedIncidenceList[NodeId, Edge.Author](authorships, _.nodeId)

  def nodeAge(node: Node): EpochMilli = nodeAge(node.id)
  lazy val nodeAge: collection.Map[NodeId, EpochMilli] = authorEdges.mapValues(n => if(n.nonEmpty) n.maxBy(_.data.timestamp).data.timestamp else EpochMilli.min)

  lazy val channelNode:Option[Node] = nodesById.values.collectFirst{case channel@Node.Content(_, NodeData.Channels, _) => channel}
  lazy val channelNodeId:Option[NodeId] = channelNode.map(_.id)
  lazy val channelIds:collection.Set[NodeId] = channelNode.fold(collection.Set.empty[NodeId])(n => children(n.id))
  println("chano" + channelNodeId)

  lazy val withoutChannels:Graph = this.filterNot(channelIds ++ channelNodeId)
  lazy val onlyAuthors:Graph = this.filterNot((allUserIds -- allAuthorIds).map(id => UserId.raw(id)))

  lazy val chronologicalNodesAscending: IndexedSeq[Node] = nodes.toIndexedSeq.sortBy(n => nodeAge(n))

  lazy val connections:Set[Edge] = connectionsByType.values.flatMap(identity)(breakOut)
  lazy val connectionsWithoutParent: Set[Edge] = (connectionsByType - EdgeData.Parent.tpe).values.flatMap(identity)(breakOut)
  lazy val containments: Set[Edge] = connectionsByTypeF(EdgeData.Parent.tpe)
  lazy val authorships: Set[Edge.Author] = connectionsByTypeF(EdgeData.Author.tpe).collect{case a: Edge.Author => a}
  lazy val contentNodes: Iterable[Node.Content] = nodes.collect{case p: Node.Content => p}
  lazy val nodeIdsTopologicalSortedByChildren:Iterable[NodeId] = nodeIds.topologicalSortBy(children)
  lazy val nodeIdsTopologicalSortedByParents:Iterable[NodeId] = nodeIds.topologicalSortBy(parents)
  lazy val allParentIds: Set[NodeId] = containments.map(_.targetId)
  lazy val allSourceIds: Set[NodeId] = containments.map(_.sourceId)
  lazy val allParents: Set[Node] = allParentIds.map(nodesById)
  lazy val toplevelNodeIds: Set[NodeId] = nodeIds.toSet -- allSourceIds
  lazy val allParentIdsTopologicallySortedByChildren:Iterable[NodeId] = allParentIds.topologicalSortBy(children)

  override def toString: String = {
    def nodeStr(node:Node) = s"${node.data.tpe}(${node.data.str}:${node.id.takeRight(4)})"
    s"Graph(${nodes.map(nodeStr).mkString(" ")}, " +
    s"${connectionsByType.values.flatten.map(c => s"${c.sourceId.takeRight(4)}-${c.data}->${c.targetId.takeRight(4)}").mkString(", ")})"
  }

  def toSummaryString = s"Graph(nodes: ${nodes.size}, containments; ${containments.size}, connections: ${connectionsWithoutParent.size})"

  lazy val successorsWithoutParent: collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ directedAdjacencyList[NodeId, Edge, NodeId](connectionsWithoutParent, _.sourceId, _.targetId)
  lazy val predecessorsWithoutParent: collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ directedAdjacencyList[NodeId, Edge, NodeId](connectionsWithoutParent, _.targetId, _.sourceId)
  lazy val neighboursWithoutParent: collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ adjacencyList[NodeId, Edge](connectionsWithoutParent, _.targetId, _.sourceId)

  def children(node:Node):collection.Set[NodeId] = children(node.id)
  lazy val children: collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ directedAdjacencyList[NodeId, Edge, NodeId](containments, _.targetId, _.sourceId)
  lazy val parents: collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ directedAdjacencyList[NodeId, Edge, NodeId](containments, _.sourceId, _.targetId)
  lazy val containmentNeighbours: collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ adjacencyList[NodeId, Edge](containments, _.targetId, _.sourceId)

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
    directedIncidenceList[NodeId, Edge](connectionsWithoutParent, _.targetId)
  lazy val outgoingConnections: collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++
    directedIncidenceList[NodeId, Edge](connectionsWithoutParent, _.sourceId)
  lazy val incidentConnections: collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++ incidenceList[NodeId, Edge](connectionsWithoutParent, _.sourceId, _.targetId)

  lazy val incidentParentContainments: collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++ directedIncidenceList[NodeId, Edge](containments, _.sourceId)
  lazy val incidentChildContainments: collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++ directedIncidenceList[NodeId, Edge](containments, _.targetId)
  lazy val incidentContainments: collection.Map[NodeId, collection.Set[Edge]] = connectionDefaultNeighbourhood ++ incidenceList[NodeId, Edge](containments, _.targetId, _.sourceId)

  private lazy val nodeDefaultDegree = defaultNeighbourhood(nodeIds,0)
  lazy val connectionDegree: collection.Map[NodeId, Int] = nodeDefaultDegree ++
    degreeSequence[NodeId, Edge](connectionsWithoutParent, _.targetId, _.sourceId)
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
