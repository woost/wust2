package wust.graph

import derive.derive
import wust.ids._
import wust.util.Pipe
import wust.util.algorithm._
import wust.util.collection._
import cuid.Cuid
import scalaz._

import java.time.LocalDateTime
import collection.mutable
import collection.breakOut

case class Ownership(postId: PostId, groupId: GroupId)
case class Membership(userId: UserId, groupId: GroupId)
case class Group(id: GroupId)
sealed trait User {
  def id: UserId
  def name: String
}
object User {
  sealed trait Persisted extends User
  @derive((id, revision) => Equality)
  case class Real(id: UserId, name: String, revision: Int) extends Persisted
  @derive((id, revision) => Equality)
  case class Implicit(id: UserId, name: String, revision: Int) extends Persisted
  case class Assumed(id: UserId) extends User {
    def name = s"anon-$id"
  }
}

//TODO: rename Post -> Item?
final case class Post(id: PostId, content: String, author: UserId, created: LocalDateTime, modified: LocalDateTime)
object Post {
  def apply(id: PostId, content: String, author: UserId, time: LocalDateTime = LocalDateTime.now()): Post = {
    Post(id, content, author, time, time)
  }
}

final case class Connection(sourceId: PostId, label:Label, targetId: PostId)

object Graph {
  def empty = new Graph(Map.empty, Map.empty, Map.empty, Set.empty, Map.empty, Set.empty)

  def apply(
    posts:        Iterable[Post]        = Nil,
    connections:  Iterable[Connection]  = Nil,
    groups:       Iterable[Group]       = Nil,
    ownerships:   Iterable[Ownership]   = Nil,
    users:        Iterable[User]        = Nil,
    memberships:  Iterable[Membership]  = Nil
  ): Graph = {
    new Graph(
      posts.by(_.id),
      connections.groupBy(_.label).map{case (label,conns) => label -> conns.toSet}, //TODO: abc
      groups.by(_.id),
      ownerships.toSet,
      users.by(_.id),
      memberships.toSet
    )
  }
}

final case class Graph( //TODO: costom pickler over lists instead of maps to save traffic
  postsById:    Map[PostId, Post],
  connectionsByLabel:  Map[Label, Set[Connection]],
  groupsById:   Map[GroupId, Group],
  ownerships:   Set[Ownership],
  usersById:    Map[UserId, User],
  memberships:  Set[Membership]
) {
  lazy val isEmpty: Boolean = postsById.isEmpty // && groups.isEmpty && users.isEmpty
  lazy val nonEmpty: Boolean = !isEmpty
  lazy val size: Int = postsById.keys.size
  lazy val length: Int = size

  private val connectionsByLabelF: (Label) => Set[Connection] = connectionsByLabel.withDefaultValue(Set.empty)

  lazy val chronologicalPostsAscending: List[Post] = posts.toList.sortBy(p => Tag.unwrap(p.id))

  lazy val connections: Set[Connection] = (connectionsByLabel - Label.parent).values.flatMap(identity)(breakOut)
  lazy val containments: Set[Connection] = connectionsByLabelF(Label.parent)
  lazy val posts: Iterable[Post] = postsById.values
  lazy val postIds: Iterable[PostId] = postsById.keys
  lazy val groups: Iterable[Group] = groupsById.values
  lazy val groupIds: Iterable[GroupId] = groupsById.keys
  lazy val users: Iterable[User] = usersById.values
  lazy val userIds: Iterable[UserId] = usersById.keys
  lazy val postIdsTopologicalSortedByChildren:Iterable[PostId] = postIds.topologicalSortBy(children)
  lazy val postIdsTopologicalSortedByParents:Iterable[PostId] = postIds.topologicalSortBy(parents)
  lazy val allParentIds: Set[PostId] = containments.map(_.targetId)
  lazy val allsourceIds: Set[PostId] = containments.map(_.sourceId)
  lazy val allParents: Set[Post] = allParentIds.map(postsById)
  // lazy val containmentIsolatedPostIds = postIds.toSet -- containments.map(_.targetId) -- containments.map(_.sourceId)
  lazy val toplevelPostIds: Set[PostId] = postIds.toSet -- allsourceIds
  lazy val allParentIdsTopologicallySortedByChildren:Iterable[PostId] = allParentIds.topologicalSortBy(children)
  lazy val allParentIdsTopologicallySortedByParents:Iterable[PostId] = allParentIds.topologicalSortBy(parents) //TODO: ..ByChildren.reverse?

  override def toString: String =
    s"Graph(${posts.map(_.id).mkString(" ")}, " +
    s"${connectionsByLabel.values.flatten.map(c => s"${c.sourceId}-[${c.label}]->${c.targetId}").mkString(", ")}, " +
    s"groups:$groupIds, " +
    s"ownerships: ${ownerships.map(o => s"${o.postId} -> ${o.groupId}").mkString(", ")}, " +
    s"users: $userIds, " +
    s"memberships: ${memberships.map(o => s"${o.userId} -> ${o.groupId}").mkString(", ")})"
  def toSummaryString = s"Graph(posts: ${posts.size}, containments; ${containments.size}, connections: ${connections.size}, groups: ${groups.size}, ownerships: ${ownerships.size}, users: ${users.size}, memberships: ${memberships.size})"

  private lazy val postDefaultNeighbourhood = postsById.mapValues(_ => Set.empty[PostId])
  lazy val successors: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Connection, PostId](connections, _.sourceId, _.targetId)
  lazy val predecessors: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Connection, PostId](connections, _.targetId, _.sourceId)
  lazy val neighbours: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ adjacencyList[PostId, Connection](connections, _.targetId, _.sourceId)

  lazy val children: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Connection, PostId](containments, _.targetId, _.sourceId)
  lazy val parents: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Connection, PostId](containments, _.sourceId, _.targetId)
  lazy val containmentNeighbours: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ adjacencyList[PostId, Connection](containments, _.targetId, _.sourceId)

  def inChildParentRelation(child: PostId, possibleParent: PostId): Boolean = getParents(child).contains(possibleParent)
  def inDescendantAncestorRelation(descendent: PostId, possibleAncestor: PostId): Boolean = ancestors(descendent).exists(_ == possibleAncestor)

  // There are cases where the key is not present and cases where the set is empty
  def hasChildren(post: PostId): Boolean = children.contains(post) && children(post).nonEmpty
  def hasParents(post: PostId): Boolean = parents.contains(post) && parents(post).nonEmpty
  def getChildren(postId: PostId): Set[PostId] = if(children.contains(postId)) children(postId) else Set.empty[PostId]
  def getParents(postId: PostId): Set[PostId] = if(parents.contains(postId)) parents(postId) else Set.empty[PostId]
  def getChildrenOpt(postId: PostId): Option[Set[PostId]] = if(hasChildren(postId)) Some(children(postId)) else None
  def getParentsOpt(postId: PostId): Option[Set[PostId]] = if(hasParents(postId)) Some(parents(postId)) else None

  // be aware that incomingConnections and incident connections can be queried with a hyperedge ( connection )
  // that's why the need default values from connectionDefaultNeighbourhood
  private lazy val connectionDefaultNeighbourhood = postsById.mapValues(_ => Set.empty[Connection])
  lazy val incomingConnections: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++
    directedIncidenceList[PostId, Connection](connections, _.targetId)
  lazy val outgoingConnections: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++
    directedIncidenceList[PostId, Connection](connections, _.sourceId)
  lazy val incidentConnections: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++ incidenceList[PostId, Connection](connections, _.sourceId, _.targetId)

  lazy val incidentParentContainments: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++ directedIncidenceList[PostId, Connection](containments, _.sourceId)
  lazy val incidentChildContainments: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++ directedIncidenceList[PostId, Connection](containments, _.targetId)
  lazy val incidentContainments: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++ incidenceList[PostId, Connection](containments, _.targetId, _.sourceId)

  private lazy val groupDefaultPosts: Map[GroupId, Set[PostId]] = groupsById.mapValues(_ => Set.empty[PostId])
  private lazy val postDefaultGroups = postsById.mapValues(_ => Set.empty[GroupId])
  lazy val postsByGroupId: Map[GroupId, Set[PostId]] = groupDefaultPosts ++ directedAdjacencyList[GroupId, Ownership, PostId](ownerships, _.groupId, _.postId)
  lazy val groupsByPostId: Map[PostId, Set[GroupId]] = postDefaultGroups ++ directedAdjacencyList[PostId, Ownership, GroupId](ownerships, _.postId, _.groupId)
  lazy val publicPostIds: Set[PostId] = postsById.keySet -- postsByGroupId.values.flatten

  private lazy val groupDefaultUsers: Map[GroupId, Set[UserId]] = groupsById.mapValues(_ => Set.empty[UserId])
  private lazy val userDefaultGroups = usersById.mapValues(_ => Set.empty[GroupId])
  lazy val usersByGroupId: Map[GroupId, Set[UserId]] = groupDefaultUsers ++ directedAdjacencyList[GroupId, Membership, UserId](memberships, _.groupId, _.userId)
  lazy val groupsByUserId: Map[UserId, Set[GroupId]] = userDefaultGroups ++ directedAdjacencyList[UserId, Membership, GroupId](memberships, _.userId, _.groupId)

  private lazy val postDefaultDegree = postsById.mapValues(_ => 0)
  lazy val connectionDegree: Map[PostId, Int] = postDefaultDegree ++
    degreeSequence[PostId, Connection](connections, _.targetId, _.sourceId)
  lazy val containmentDegree: Map[PostId, Int] = postDefaultDegree ++
    degreeSequence[PostId, Connection](containments, _.targetId, _.sourceId)

  def fullDegree(postId: PostId): Int = connectionDegree(postId) + containmentDegree(postId)

  def involvedInContainmentCycle(id: PostId): Boolean = {
    children.get(id)
      .map(_.exists(child => depthFirstSearch(child, children).exists(_ == id)))
      .getOrElse(false)
  }
  // TODO: maybe fast involved-in-cycle-algorithm?
  // breadth-first-search starting at successors and another one starting at predecessors in different direction.
  // When both queues contain the same elements, we can stop, because we found a cycle
  // Even better:
  // lazy val involvedInContainmentCycle:Set[PostId] = all posts involved in a cycle

  def descendants(postId: PostId) = _descendants(postId)
  private val _descendants: (PostId) => Iterable[PostId] = Memo.mutableHashMapMemo { postId =>
    postsById.isDefinedAt(postId) match {
      case true =>
        depthFirstSearch(postId, children) |> { children =>
          if (children.startInvolvedInCycle) children else children.drop(1)
        } //TODO better?
      case false => Seq.empty
    }
  }
  //TODO: rename to transitiveParentIds:Iterable[PostId]
  // Also provide ancestors:Iterable[Post]?
  def ancestors(postId: PostId) = _ancestors(postId)
  private val _ancestors: (PostId) => Iterable[PostId] = Memo.mutableHashMapMemo { postId =>
    if (postsById.keySet.contains(postId)) {
      val p = depthFirstSearch(postId, parents)
      if (p.startInvolvedInCycle) p else p.drop(1)
    } else {
      Seq.empty
    }
  }

  // Get connected components by only considering containment edges
  lazy val connectedContainmentComponents: List[Set[PostId]] = {
    connectedComponents(postIds, containmentNeighbours)
  }

  def -(postId: PostId): Graph = removePosts(postId :: Nil)
  def -(connection: Connection): Graph = copy(
    connectionsByLabel = connectionsByLabel.updated(
      connection.label,
      connectionsByLabelF(connection.label) - connection
    )
  )
  def -(ownership: Ownership): Graph = copy(ownerships = ownerships - ownership)

  def filter(p: PostId => Boolean): Graph = {
    val newPostsById = postsById.filterKeys(p)
    val exists = newPostsById.isDefinedAt _

    copy(
      postsById = newPostsById,
      connectionsByLabel = connectionsByLabel.mapValues(_.filter(c => exists(c.sourceId) && exists(c.targetId))).filter(_._2.nonEmpty),
      ownerships = ownerships.filter { o => exists(o.postId) }
    )
  }

  def removePosts(ps: Iterable[PostId]): Graph = {
    val postIds = ps.toSet

    copy(
      postsById = postsById -- ps,
      connectionsByLabel = connectionsByLabel.mapValues( _.filterNot( c => postIds(c.sourceId) || postIds(c.targetId)) ).filter(_._2.nonEmpty),
      ownerships = ownerships.filterNot { o => postIds(o.postId) }
    )
  }
  def removeConnections(cs: Iterable[Connection]): Graph = copy(connectionsByLabel = connectionsByLabel.mapValues(_ -- cs).filter(_._2.nonEmpty))
  def removeOwnerships(cs: Iterable[Ownership]): Graph = copy(ownerships = ownerships -- cs)
  def addPosts(cs: Iterable[Post]): Graph = copy(postsById = postsById ++ cs.map(p => p.id -> p))
  def addConnections(cs: Iterable[Connection]): Graph = cs.foldLeft(this)((g,c) => g + c) // todo: more efficient
  def addOwnerships(cs: Iterable[Ownership]): Graph = copy(ownerships = ownerships ++ cs)

  def applyChanges(c: GraphChanges): Graph = {
    copy(
      postsById = postsById ++ c.addPosts.by(_.id) ++ c.updatePosts.by(_.id) -- c.delPosts,
      connectionsByLabel = connectionsByLabel ++ (c.addConnections -- c.delConnections).groupBy(_.label).map { case (k,v) => k -> ( v ++ connectionsByLabelF(k)) },
      ownerships = ownerships ++ c.addOwnerships -- c.delOwnerships
    )
  }

  def +(post: Post): Graph = copy(postsById = postsById + (post.id -> post))
  def +(connection: Connection): Graph = copy(
    connectionsByLabel = connectionsByLabel.updated(
      connection.label,
      connectionsByLabelF(connection.label) + connection
    )
  )

  def +(group: Group): Graph = copy(groupsById = groupsById + (group.id -> group))
  def addGroups(newGroups: Iterable[Group]): Graph = copy(groupsById = groupsById ++ newGroups.by(_.id))
  def +(user: User): Graph = copy(usersById = usersById + (user.id -> user))
  def +(ownership: Ownership): Graph = copy(ownerships = ownerships + ownership)
  def +(membership: Membership): Graph = copy(memberships = memberships + membership)
  def addMemberships(newMemberships: Iterable[Membership]): Graph = copy(memberships = memberships ++ newMemberships)

  def +(other: Graph): Graph = copy (
    postsById ++ other.postsById,
    connectionsByLabel ++ other.connectionsByLabel,
    groupsById ++ other.groupsById,
    ownerships ++ other.ownerships,
    usersById ++ other.usersById,
    memberships ++ other.memberships
  )

  def withoutGroup(groupId: GroupId): Graph = copy(
    groupsById = groupsById - groupId,
    ownerships = ownerships.filter(_.groupId != groupId),
    memberships = memberships.filter(_.groupId != groupId)
  )

  lazy val consistent: Graph = {
    val filteredConnections = connectionsByLabel.mapValues(_.filter(c => postsById.isDefinedAt(c.sourceId) && postsById.isDefinedAt(c.targetId) && c.sourceId != c.targetId)).filter(_._2.nonEmpty)
    val filteredOwnerships = ownerships.filter { o => postsById.isDefinedAt(o.postId) && groupsById.isDefinedAt(o.groupId) }
    val filteredMemberships = memberships.filter { m => usersById.isDefinedAt(m.userId) && groupsById.isDefinedAt(m.groupId) }

    if (connectionsByLabel.values.flatten.size != filteredConnections.values.flatten.size ||
      ownerships.size != filteredOwnerships.size ||
      memberships.size != filteredMemberships.size)
      copy(
        connectionsByLabel = filteredConnections,
        ownerships = filteredOwnerships,
        memberships = filteredMemberships
      )
    else
      this
  }

  lazy val childDepth: collection.Map[PostId, Int] = depth(children)
  lazy val parentDepth: collection.Map[PostId, Int] = depth(parents)

  def depth(next: PostId => Iterable[PostId]): collection.Map[PostId, Int] = {
    val tmpDepths = mutable.HashMap[PostId, Int]()
    val visited = mutable.HashSet[PostId]() // to handle cycles
    def getDepth(id: PostId): Int = {
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

    for (id <- postIds if !tmpDepths.isDefinedAt(id)) {
      getDepth(id)
    }
    tmpDepths
    }
  }
