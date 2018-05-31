package wust.graph

import wust.ids._
import wust.util.Memo
import wust.util.algorithm._
import wust.util.collection._
import cuid.Cuid
import wust.graph.Edge.Author
import wust.ids.EdgeData.Member

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
  // TODO: we cannot set the postmeta here, but there is changeable data in the db class
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
  def empty = new Graph(Map.empty, Map.empty)

  def apply(
             posts:        Iterable[Node]  = Nil,
             connections:  Iterable[Edge]  = Nil,
  ): Graph = {
    new Graph(
      posts.by(_.id),
      connections.groupBy(_.data.tpe).map{case (tpe,conns) => tpe -> conns.toSet},
    )
  }
}

final case class Graph( //TODO: store lists instead of maps for smaller encoding
                        postsById:    Map[NodeId, Node],
                        connectionsByType:  Map[EdgeData.Type, Set[Edge]],
) {
  lazy val isEmpty: Boolean = postsById.isEmpty
  lazy val nonEmpty: Boolean = !isEmpty
  lazy val size: Int = postsById.keys.size
  lazy val length: Int = size

  private val connectionsByTypeF: EdgeData.Type => Set[Edge] = connectionsByType.withDefaultValue(Set.empty)

  def authors(node: Node): Set[UserId] = authors(node.id)
  private lazy val postDefaultAuthors: Map[NodeId, Set[UserId]] = postDefaultNeighbourhood.asInstanceOf[Map[NodeId, Set[UserId]]]
  lazy val authors: Map[NodeId, Set[UserId]] = postDefaultAuthors ++ directedAdjacencyList[NodeId, Edge.Author, UserId](authorships, _.nodeId, _.userId)
  lazy val authorEdges: Map[NodeId, Set[Edge.Author]] = connectionDefaultNeighbourhood.asInstanceOf[Map[NodeId, Set[Edge.Author]]] ++ directedIncidenceList[NodeId, Edge.Author](authorships, _.nodeId)

  def nodeAge(node: Node): EpochMilli = nodeAge(node.id)
  lazy val nodeAge: Map[NodeId, EpochMilli] = authorEdges.mapValues(n => if(n.nonEmpty) n.maxBy(_.data.timestamp).data.timestamp else EpochMilli.min)

  def chronologicalPostsAscending: IndexedSeq[Node] = posts.toIndexedSeq.sortBy(n => nodeAge(n))

  lazy val connections:Set[Edge] = connectionsByType.values.flatMap(identity)(breakOut)
  lazy val connectionsWithoutParent: Set[Edge] = (connectionsByType - EdgeData.Parent.tpe).values.flatMap(identity)(breakOut)
  lazy val containments: Set[Edge] = connectionsByTypeF(EdgeData.Parent.tpe)
  lazy val authorships: Set[Edge.Author] = connectionsByTypeF(EdgeData.Author.tpe).collect{case a: Edge.Author => a}
  lazy val posts: Iterable[Node] = postsById.values
  lazy val contentPosts: Iterable[Node.Content] = posts.collect{case p: Node.Content => p}
  lazy val nodeIds: Iterable[NodeId] = postsById.keys
  lazy val nodeIdsTopologicalSortedByChildren:Iterable[NodeId] = nodeIds.topologicalSortBy(children)
  lazy val nodeIdsTopologicalSortedByParents:Iterable[NodeId] = nodeIds.topologicalSortBy(parents)
  lazy val allParentIds: Set[NodeId] = containments.map(_.targetId)
  lazy val allSourceIds: Set[NodeId] = containments.map(_.sourceId)
  lazy val allParents: Set[Node] = allParentIds.map(postsById)
  // lazy val containmentIsolatedNodeIds = nodeIds.toSet -- containments.map(_.targetId) -- containments.map(_.sourceId)
  lazy val toplevelNodeIds: Set[NodeId] = nodeIds.toSet -- allSourceIds
  lazy val allParentIdsTopologicallySortedByChildren:Iterable[NodeId] = allParentIds.topologicalSortBy(children)
  lazy val allParentIdsTopologicallySortedByParents:Iterable[NodeId] = allParentIds.topologicalSortBy(parents) //TODO: ..ByChildren.reverse?

  override def toString: String = {
    def nodeStr(node:Node) = s"${node.data.tpe}(${node.data.str}:${node.id.takeRight(4)})"
    s"Graph(${posts.map(nodeStr).mkString(" ")}, " +
    s"${connectionsByType.values.flatten.map(c => s"${c.sourceId.takeRight(4)}-${c.data}->${c.targetId.takeRight(4)}").mkString(", ")})"
  }

  def toSummaryString = s"Graph(posts: ${posts.size}, containments; ${containments.size}, connections: ${connectionsWithoutParent.size})"

  private lazy val postDefaultNeighbourhood: Map[NodeId, Set[NodeId]] = postsById.mapValues(_ => Set.empty[NodeId]).withDefaultValue(Set.empty[NodeId])
  lazy val successorsWithoutParent: Map[NodeId, Set[NodeId]] = postDefaultNeighbourhood ++ directedAdjacencyList[NodeId, Edge, NodeId](connectionsWithoutParent, _.sourceId, _.targetId)
  lazy val predecessorsWithoutParent: Map[NodeId, Set[NodeId]] = postDefaultNeighbourhood ++ directedAdjacencyList[NodeId, Edge, NodeId](connectionsWithoutParent, _.targetId, _.sourceId)
  lazy val neighboursWithoutParent: Map[NodeId, Set[NodeId]] = postDefaultNeighbourhood ++ adjacencyList[NodeId, Edge](connectionsWithoutParent, _.targetId, _.sourceId)

  def children(post:Node):Set[NodeId] = children(post.id)
  lazy val children: Map[NodeId, Set[NodeId]] = postDefaultNeighbourhood ++ directedAdjacencyList[NodeId, Edge, NodeId](containments, _.targetId, _.sourceId)
  lazy val parents: Map[NodeId, Set[NodeId]] = postDefaultNeighbourhood ++ directedAdjacencyList[NodeId, Edge, NodeId](containments, _.sourceId, _.targetId)
  lazy val containmentNeighbours: Map[NodeId, Set[NodeId]] = postDefaultNeighbourhood ++ adjacencyList[NodeId, Edge](containments, _.targetId, _.sourceId)

  def inChildParentRelation(child: NodeId, possibleParent: NodeId): Boolean = getParents(child).contains(possibleParent)
  def inDescendantAncestorRelation(descendent: NodeId, possibleAncestor: NodeId): Boolean = ancestors(descendent).exists(_ == possibleAncestor)

  // There are cases where the key is not present and cases where the set is empty
  def hasChildren(post: NodeId): Boolean = children.contains(post) && children(post).nonEmpty
  def hasParents(post: NodeId): Boolean = parents.contains(post) && parents(post).nonEmpty
  def getChildren(nodeId: NodeId): Set[NodeId] = if(children.contains(nodeId)) children(nodeId) else Set.empty[NodeId]
  def getParents(nodeId: NodeId): Set[NodeId] = if(parents.contains(nodeId)) parents(nodeId) else Set.empty[NodeId]
  def getChildrenOpt(nodeId: NodeId): Option[Set[NodeId]] = if(hasChildren(nodeId)) Some(children(nodeId)) else None
  def getParentsOpt(nodeId: NodeId): Option[Set[NodeId]] = if(hasParents(nodeId)) Some(parents(nodeId)) else None

  private lazy val connectionDefaultNeighbourhood: Map[NodeId, Set[Edge]] = postsById.mapValues(_ => Set.empty[Edge]).withDefaultValue(Set.empty[Edge])
  lazy val incomingConnections: Map[NodeId, Set[Edge]] = connectionDefaultNeighbourhood ++
    directedIncidenceList[NodeId, Edge](connectionsWithoutParent, _.targetId)
  lazy val outgoingConnections: Map[NodeId, Set[Edge]] = connectionDefaultNeighbourhood ++
    directedIncidenceList[NodeId, Edge](connectionsWithoutParent, _.sourceId)
  lazy val incidentConnections: Map[NodeId, Set[Edge]] = connectionDefaultNeighbourhood ++ incidenceList[NodeId, Edge](connectionsWithoutParent, _.sourceId, _.targetId)

  lazy val incidentParentContainments: Map[NodeId, Set[Edge]] = connectionDefaultNeighbourhood ++ directedIncidenceList[NodeId, Edge](containments, _.sourceId)
  lazy val incidentChildContainments: Map[NodeId, Set[Edge]] = connectionDefaultNeighbourhood ++ directedIncidenceList[NodeId, Edge](containments, _.targetId)
  lazy val incidentContainments: Map[NodeId, Set[Edge]] = connectionDefaultNeighbourhood ++ incidenceList[NodeId, Edge](containments, _.targetId, _.sourceId)

  private lazy val postDefaultMembers: Map[NodeId, Set[UserId]] = postsById.mapValues(_ => Set.empty[UserId]).withDefaultValue(Set.empty[UserId])
  // private lazy val userDefaultPosts = usersById.mapValues(_ => Set.empty[NodeId]).withDefaultValue(Set.empty[NodeId])
  // lazy val usersByNodeId: Map[NodeId, Set[UserId]] = postDefaultMembers ++ directedAdjacencyList[NodeId, Membership, UserId](memberships, _.nodeId, _.userId)
  // lazy val postsByUserId: Map[UserId, Set[NodeId]] = userDefaultPosts ++ directedAdjacencyList[UserId, Membership, NodeId](memberships, _.userId, _.nodeId)
  // lazy val publicNodeIds: Set[NodeId] = postsById.keySet -- postsByUserId.values.flatten

  private lazy val postDefaultDegree = postsById.mapValues(_ => 0).withDefaultValue(0)
  lazy val connectionDegree: Map[NodeId, Int] = postDefaultDegree ++
    degreeSequence[NodeId, Edge](connectionsWithoutParent, _.targetId, _.sourceId)
  lazy val containmentDegree: Map[NodeId, Int] = postDefaultDegree ++
    degreeSequence[NodeId, Edge](containments, _.targetId, _.sourceId)

  def fullDegree(nodeId: NodeId): Int = connectionDegree(nodeId) + containmentDegree(nodeId)

  def involvedInContainmentCycle(id: NodeId): Boolean = {
    children.get(id).exists(_.exists(child => depthFirstSearch(child, children).exists(_ == id)))
  }
  // TODO: maybe fast involved-in-cycle-algorithm?
  // breadth-first-search starting at successors and another one starting at predecessors in different direction.
  // When both queues contain the same elements, we can stop, because we found a cycle
  // Even better:
  // lazy val involvedInContainmentCycle:Set[NodeId] = all posts involved in a cycle

  def descendants(nodeId: NodeId) = _descendants(nodeId)
  private val _descendants: NodeId => Iterable[NodeId] = Memo.mutableHashMapMemo { nodeId =>
    postsById.isDefinedAt(nodeId) match {
      case true =>
        val cs = depthFirstSearch(nodeId, children)
        if (cs.startInvolvedInCycle) cs else cs.drop(1)
      case false => Seq.empty
    }
  }
  //TODO: rename to transitiveParentIds:Iterable[NodeId]
  // Also provide ancestors:Iterable[Post]?
  def ancestors(nodeId: NodeId) = _ancestors(nodeId)
  private val _ancestors: NodeId => Iterable[NodeId] = Memo.mutableHashMapMemo { nodeId =>
    if (postsById.keySet.contains(nodeId)) {
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

  def -(nodeId: NodeId): Graph = removePosts(nodeId :: Nil)
  def -(connection: Edge): Graph = copy(
    connectionsByType = connectionsByType.updated(
      connection.data.tpe,
      connectionsByTypeF(connection.data.tpe) - connection
    )
  )

  def filter(p: NodeId => Boolean): Graph = {
    val newPostsById = postsById.filterKeys(p)
    val exists = newPostsById.isDefinedAt _

    copy(
      postsById = newPostsById,
      connectionsByType = connectionsByType.mapValues(_.filter(c => exists(c.sourceId) && exists(c.targetId))).filter(_._2.nonEmpty)
    )
  }

  def filterNot(p: NodeId => Boolean): Graph = filter(id => !p(id))

  def removePosts(ps: Iterable[NodeId]): Graph = {
    val nodeIds = ps.toSet

    copy(
      postsById = postsById -- ps,
      connectionsByType = connectionsByType.mapValues( _.filterNot( c => nodeIds(c.sourceId) || nodeIds(c.targetId)) ).filter(_._2.nonEmpty)
    )
  }

  def removeConnections(cs: Iterable[Edge]): Graph = copy(connectionsByType = connectionsByType.mapValues(_ -- cs).filter(_._2.nonEmpty))
  // def removeMemberships(cs: Iterable[Membership]): Graph = copy(memberships = memberships -- cs)
  def addPosts(cs: Iterable[Node]): Graph = copy(postsById = postsById ++ cs.map(p => p.id -> p))
  def addConnections(cs: Iterable[Edge]): Graph = cs.foldLeft(this)((g, c) => g + c) // todo: more efficient
  // def addMemberships(newMemberships: Iterable[Membership]): Graph = copy(memberships = memberships ++ newMemberships)

  def applyChanges(c: GraphChanges): Graph = {
    copy(
      postsById = (postsById ++ c.addNodes.by(_.id) ++ c.updateNodes.by(_.id)).mapValues {
        case p:Node.Content if c.delNodes(p.id) =>
          p.copy(meta = p.meta.copy(deleted = DeletedDate.Deleted(EpochMilli.now)))
        case p => p
      },
      connectionsByType =
        connectionsByType.map { case (k, v) => k -> v.filterNot(c.delEdges) } ++
          c.addEdges.groupBy(_.data.tpe).collect { case (k, v) => k -> (v ++ connectionsByTypeF(k)).filterNot(c.delEdges) }
    )
  }

  def +(post: Node): Graph = copy(postsById = postsById + (post.id -> post))
  def +(connection: Edge): Graph = copy(
    connectionsByType = connectionsByType.updated(
      connection.data.tpe,
      connectionsByTypeF(connection.data.tpe) + connection
    )
  )

  // def +(user: User): Graph = copy(usersById = usersById + (user.id -> user))
  // def +(membership: Membership): Graph = copy(memberships = memberships + membership)

  def +(other: Graph): Graph = copy (
    postsById ++ other.postsById,
    connectionsByType ++ other.connectionsByType
  )

  lazy val consistent: Graph = {
    val filteredConnections = connectionsByType.mapValues(_.filter(c => postsById.isDefinedAt(c.sourceId) && postsById.isDefinedAt(c.targetId) && c.sourceId != c.targetId)).filter(_._2.nonEmpty)

    if (connectionsByType.values.flatten.size != filteredConnections.values.flatten.size)
      copy(
        connectionsByType = filteredConnections
      )
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
