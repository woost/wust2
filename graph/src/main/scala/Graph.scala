package wust.graph

import wust.ids._
import wust.util.Memo
import wust.util.algorithm._
import wust.util.collection._
import cuid.Cuid

import collection.mutable
import collection.breakOut

case class Membership(userId: UserId, postId: PostId)
sealed trait User {
  def id: UserId
  def name: String
  def channelPostId:PostId
}
object User {
  sealed trait Persisted extends User
  case class Real(id: UserId, name: String, revision: Int, channelPostId: PostId) extends Persisted {
    def canEqual(other: Any): Boolean = other.isInstanceOf[Real]

    override def equals(other: Any): Boolean = other match {
      case that: Real =>
        (that canEqual this) &&
          id == that.id &&
          revision == that.revision
      case _ => false
    }

    override def hashCode(): Int = (id, revision).hashCode
  }

  case class Implicit(id: UserId, name: String, revision: Int, channelPostId: PostId) extends Persisted {
    def canEqual(other: Any): Boolean = other.isInstanceOf[Real]

    override def equals(other: Any): Boolean = other match {
      case that: Real =>
        (that canEqual this) &&
          id == that.id &&
          revision == that.revision
      case _ => false
    }

    override def hashCode(): Int = (id, revision).hashCode

  }

  case class Assumed(id: UserId, channelPostId: PostId) extends User {
    def name = s"anon-$id"
  }
}

sealed trait PostContent {
  def externalString: String
}
object PostContent {
  //TODO with auto detection of type?: def fromString(content: String)

  case class Markdown(content: String) extends PostContent {
    def externalString = content
  }
  case class Text(content: String) extends PostContent {
    def externalString = content
  }
  case object Channels extends PostContent {
    def externalString = "Channels"
  }
}


//TODO: rename Post -> Item?
final case class Post(id: PostId, content: PostContent, author: UserId, created: EpochMilli, modified: EpochMilli, joinDate: JoinDate, joinLevel: AccessLevel)
//TODO: get rid of timestamp created/modified and author. should be relations.
object Post {
  def apply(id: PostId, content: PostContent, author: UserId, created: EpochMilli, modified: EpochMilli): Post = {
    new Post(id, content, author, created, modified, JoinDate.Never, AccessLevel.ReadWrite)
  }
  def apply(id: PostId, content: PostContent, author: UserId, time: EpochMilli = EpochMilli.now): Post = {
    new Post(id, content, author, time, time, JoinDate.Never, AccessLevel.ReadWrite)
  }
  def apply(content: PostContent, author: UserId, time: EpochMilli): Post = {
    new Post(PostId.fresh, content, author, time, time, JoinDate.Never, AccessLevel.ReadWrite)
  }
  def apply(content: PostContent, author: UserId): Post = {
    val time = EpochMilli.now
    new Post(PostId.fresh, content, author, time, time, JoinDate.Never, AccessLevel.ReadWrite)
  }
}

final case class Connection(sourceId: PostId, label:Label, targetId: PostId)

object Graph {
  def empty = new Graph(Map.empty, Map.empty, Map.empty, Set.empty)

  def apply(
    posts:        Iterable[Post]        = Nil,
    connections:  Iterable[Connection]  = Nil,
    users:        Iterable[User]        = Nil,
    memberships:  Iterable[Membership]  = Nil
  ): Graph = {
    new Graph(
      posts.by(_.id),
      connections.groupBy(_.label).map{case (label,conns) => label -> conns.toSet}, //TODO: abc
      users.by(_.id),
      memberships.toSet
    )
  }
}

final case class Graph( //TODO: costom pickler over lists instead of maps to save traffic
  postsById:    Map[PostId, Post],
  connectionsByLabel:  Map[Label, Set[Connection]],
  usersById:    Map[UserId, User],
  memberships:  Set[Membership]
) {
  lazy val isEmpty: Boolean = postsById.isEmpty // && groups.isEmpty && users.isEmpty
  lazy val nonEmpty: Boolean = !isEmpty
  lazy val size: Int = postsById.keys.size
  lazy val length: Int = size

  private val connectionsByLabelF: (Label) => Set[Connection] = connectionsByLabel.withDefaultValue(Set.empty)

  lazy val chronologicalPostsAscending: IndexedSeq[Post] = posts.toIndexedSeq.sortBy(p => p.created : EpochMilli.Raw)

  lazy val connections:Set[Connection] = connectionsByLabel.values.flatMap(identity)(breakOut)
  lazy val connectionsWithoutParent: Set[Connection] = (connectionsByLabel - Label.parent).values.flatMap(identity)(breakOut)
  lazy val containments: Set[Connection] = connectionsByLabelF(Label.parent)
  lazy val posts: Iterable[Post] = postsById.values
  lazy val postIds: Iterable[PostId] = postsById.keys
  lazy val users: Iterable[User] = usersById.values
  lazy val userIds: Iterable[UserId] = usersById.keys
  lazy val postIdsTopologicalSortedByChildren:Iterable[PostId] = postIds.topologicalSortBy(children)
  lazy val postIdsTopologicalSortedByParents:Iterable[PostId] = postIds.topologicalSortBy(parents)
  lazy val allParentIds: Set[PostId] = containments.map(_.targetId)
  lazy val allSourceIds: Set[PostId] = containments.map(_.sourceId)
  lazy val allParents: Set[Post] = allParentIds.map(postsById)
  // lazy val containmentIsolatedPostIds = postIds.toSet -- containments.map(_.targetId) -- containments.map(_.sourceId)
  lazy val toplevelPostIds: Set[PostId] = postIds.toSet -- allSourceIds
  lazy val allParentIdsTopologicallySortedByChildren:Iterable[PostId] = allParentIds.topologicalSortBy(children)
  lazy val allParentIdsTopologicallySortedByParents:Iterable[PostId] = allParentIds.topologicalSortBy(parents) //TODO: ..ByChildren.reverse?

  override def toString: String =
    s"Graph(${posts.map(_.id.takeRight(4)).mkString(" ")}, " +
    s"${connectionsByLabel.values.flatten.map(c => s"${c.sourceId.takeRight(4)}-[${c.label}]->${c.targetId.takeRight(4)}").mkString(", ")}, " +
    s"users: ${userIds.map(_.takeRight(4))}, " +
    s"memberships: ${memberships.map(o => s"${o.userId.takeRight(4)} -> ${o.postId.takeRight(4)}").mkString(", ")})"

  def toSummaryString = s"Graph(posts: ${posts.size}, containments; ${containments.size}, connections: ${connectionsWithoutParent.size}, users: ${users.size}, memberships: ${memberships.size})"

  private lazy val postDefaultNeighbourhood = postsById.mapValues(_ => Set.empty[PostId]).withDefaultValue(Set.empty[PostId])
  lazy val successorsWithoutParent: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Connection, PostId](connectionsWithoutParent, _.sourceId, _.targetId)
  lazy val predecessorsWithoutParent: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Connection, PostId](connectionsWithoutParent, _.targetId, _.sourceId)
  lazy val neighboursWithoutParent: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ adjacencyList[PostId, Connection](connectionsWithoutParent, _.targetId, _.sourceId)

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

  private lazy val connectionDefaultNeighbourhood = postsById.mapValues(_ => Set.empty[Connection]).withDefaultValue(Set.empty[Connection])
  lazy val incomingConnections: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++
    directedIncidenceList[PostId, Connection](connectionsWithoutParent, _.targetId)
  lazy val outgoingConnections: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++
    directedIncidenceList[PostId, Connection](connectionsWithoutParent, _.sourceId)
  lazy val incidentConnections: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++ incidenceList[PostId, Connection](connectionsWithoutParent, _.sourceId, _.targetId)

  lazy val incidentParentContainments: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++ directedIncidenceList[PostId, Connection](containments, _.sourceId)
  lazy val incidentChildContainments: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++ directedIncidenceList[PostId, Connection](containments, _.targetId)
  lazy val incidentContainments: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++ incidenceList[PostId, Connection](containments, _.targetId, _.sourceId)

  private lazy val postDefaultMembers: Map[PostId, Set[UserId]] = postsById.mapValues(_ => Set.empty[UserId]).withDefaultValue(Set.empty[UserId])
  private lazy val userDefaultPosts = usersById.mapValues(_ => Set.empty[PostId]).withDefaultValue(Set.empty[PostId])
  lazy val usersByPostId: Map[PostId, Set[UserId]] = postDefaultMembers ++ directedAdjacencyList[PostId, Membership, UserId](memberships, _.postId, _.userId)
  lazy val postsByUserId: Map[UserId, Set[PostId]] = userDefaultPosts ++ directedAdjacencyList[UserId, Membership, PostId](memberships, _.userId, _.postId)
  lazy val publicPostIds: Set[PostId] = postsById.keySet -- postsByUserId.values.flatten

  private lazy val postDefaultDegree = postsById.mapValues(_ => 0).withDefaultValue(0)
  lazy val connectionDegree: Map[PostId, Int] = postDefaultDegree ++
    degreeSequence[PostId, Connection](connectionsWithoutParent, _.targetId, _.sourceId)
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
        val cs = depthFirstSearch(postId, children)
        if (cs.startInvolvedInCycle) cs else cs.drop(1)
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

  def filter(p: PostId => Boolean): Graph = {
    val newPostsById = postsById.filterKeys(p)
    val exists = newPostsById.isDefinedAt _

    copy(
      postsById = newPostsById,
      connectionsByLabel = connectionsByLabel.mapValues(_.filter(c => exists(c.sourceId) && exists(c.targetId))).filter(_._2.nonEmpty),
      memberships = memberships.filter { m => exists(m.postId) }
    )
  }

  def removePosts(ps: Iterable[PostId]): Graph = {
    val postIds = ps.toSet

    copy(
      postsById = postsById -- ps,
      connectionsByLabel = connectionsByLabel.mapValues( _.filterNot( c => postIds(c.sourceId) || postIds(c.targetId)) ).filter(_._2.nonEmpty),
      memberships = memberships.filterNot { m => postIds(m.postId) }
    )
  }

  def removeConnections(cs: Iterable[Connection]): Graph = copy(connectionsByLabel = connectionsByLabel.mapValues(_ -- cs).filter(_._2.nonEmpty))
  def removeMemberships(cs: Iterable[Membership]): Graph = copy(memberships = memberships -- cs)
  def addPosts(cs: Iterable[Post]): Graph = copy(postsById = postsById ++ cs.map(p => p.id -> p))
  def addConnections(cs: Iterable[Connection]): Graph = cs.foldLeft(this)((g,c) => g + c) // todo: more efficient
  def addMemberships(newMemberships: Iterable[Membership]): Graph = copy(memberships = memberships ++ newMemberships)

  def applyChanges(c: GraphChanges): Graph = {
    copy(
      postsById = postsById ++ c.addPosts.by(_.id) ++ c.updatePosts.by(_.id) -- c.delPosts,
      connectionsByLabel =
        connectionsByLabel.map { case (k, v) => k -> v.filterNot(c.delConnections) } ++
          c.addConnections.groupBy(_.label).collect { case (k, v) => k -> (v ++ connectionsByLabelF(k)).filterNot(c.delConnections) },
      memberships = memberships.filterNot(m => c.delPosts.contains(m.postId))
    )
  }

  def +(post: Post): Graph = copy(postsById = postsById + (post.id -> post))
  def +(connection: Connection): Graph = copy(
    connectionsByLabel = connectionsByLabel.updated(
      connection.label,
      connectionsByLabelF(connection.label) + connection
    )
  )

  def +(user: User): Graph = copy(usersById = usersById + (user.id -> user))
  def +(membership: Membership): Graph = copy(memberships = memberships + membership)

  def +(other: Graph): Graph = copy (
    postsById ++ other.postsById,
    connectionsByLabel ++ other.connectionsByLabel,
    usersById ++ other.usersById,
    memberships ++ other.memberships
  )

  lazy val consistent: Graph = {
    val filteredConnections = connectionsByLabel.mapValues(_.filter(c => postsById.isDefinedAt(c.sourceId) && postsById.isDefinedAt(c.targetId) && c.sourceId != c.targetId)).filter(_._2.nonEmpty)
    val filteredMemberships = memberships.filter { m => usersById.isDefinedAt(m.userId) && postsById.isDefinedAt(m.postId) }

    if (connectionsByLabel.values.flatten.size != filteredConnections.values.flatten.size ||
      memberships.size != filteredMemberships.size)
      copy(
        connectionsByLabel = filteredConnections,
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
