package wust

import derive.derive
import wust.ids._
import wust.util.Pipe
import wust.util.algorithm._
import wust.util.collection._
import scalaz._

import collection.mutable

package object graph {

  case class Ownership(postId: PostId, groupId: GroupId)
  case class Membership(userId: UserId, groupId: GroupId)
  @derive((id, isImplicit) => Equality)
  case class User(id: UserId, name: String, isImplicit: Boolean, revision: Int)
  case class Group(id: GroupId)

  //TODO: rename Post -> ???
  final case class Post(id: PostId, title: String)
  final case class Connection(sourceId: PostId, targetId: PostId)
  final case class Containment(parentId: PostId, childId: PostId)
  object Graph {
    def empty =
      new Graph(
        Map.empty,
        Set.empty,
        Set.empty,
        Map.empty,
        Set.empty,
        Map.empty,
        Set.empty
      )

    def apply(
      posts:        Iterable[Post]        = Nil,
      connections:  Iterable[Connection]  = Nil,
      containments: Iterable[Containment] = Nil,
      groups:       Iterable[Group]       = Nil,
      ownerships:   Iterable[Ownership]   = Nil,
      users:        Iterable[User]        = Nil,
      memberships:  Iterable[Membership]  = Nil
    ): Graph = {
      new Graph(
        posts.by(_.id),
        connections.toSet,
        containments.toSet,
        groups.by(_.id),
        ownerships.toSet,
        users.by(_.id),
        memberships.toSet
      )
    }
  }

  // @wust.util.callLog(println)
  final case class Graph( //TODO: costom pickler over lists instead of maps to save traffic
    postsById:    Map[PostId, Post],
    connections:  Set[Connection],
    containments: Set[Containment],
    groupsById:   Map[GroupId, Group],
    ownerships:   Set[Ownership],
    usersById:    Map[UserId, User],
    memberships:  Set[Membership]
  ) {

    lazy val posts: Iterable[Post] = postsById.values
    lazy val postIds: Iterable[PostId] = postsById.keys
    lazy val groups: Iterable[Group] = groupsById.values
    lazy val groupIds: Iterable[GroupId] = groupsById.keys
    lazy val users: Iterable[User] = usersById.values
    lazy val userIds: Iterable[UserId] = usersById.keys

    override def toString =
      s"Graph(${posts.map(_.id).mkString(" ")},${connections.map(c => s"${c.sourceId}->${c.targetId}").mkString(", ")}, ${containments.map(c => s"${c.parentId}⊂${c.childId}").mkString(", ")},groups:${groupIds}, ownerships: ${ownerships.map(o => s"${o.postId} -> ${o.groupId}").mkString(", ")}, users: ${userIds}, memberships: ${memberships.map(o => s"${o.userId} -> ${o.groupId}").mkString(", ")})"
    def toSummaryString = s"Graph(posts: ${posts.size}, connections: ${connections.size}, containments: ${containments.size}, groups: ${groups.size}, ownerships: ${ownerships.size}, users: ${users.size}, memberships: ${memberships.size})"

    private lazy val postDefaultNeighbourhood = postsById.mapValues(_ => Set.empty[PostId])
    lazy val successors: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Connection, PostId](connections, _.sourceId, _.targetId)
    lazy val predecessors: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Connection, PostId](connections, _.targetId, _.sourceId)
    lazy val neighbours: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ adjacencyList[PostId, Connection](connections, _.targetId, _.sourceId)

    lazy val children: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Containment, PostId](containments, _.parentId, _.childId)
    lazy val parents: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Containment, PostId](containments, _.childId, _.parentId)
    lazy val containmentNeighbours: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ adjacencyList[PostId, Containment](containments, _.parentId, _.childId)

    def hasChildren(post: PostId) = children(post).nonEmpty
    def hasParents(post: PostId) = parents(post).nonEmpty

    // be aware that incomingConnections and incident connections can be queried with a hyperedge ( connection )
    // that's why the need default values from connectionDefaultNeighbourhood
    private lazy val connectionDefaultNeighbourhood = postsById.mapValues(_ => Set.empty[Connection])
    lazy val incomingConnections: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++
      directedIncidenceList[PostId, Connection](connections, _.targetId)
    lazy val outgoingConnections: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++
      directedIncidenceList[PostId, Connection](connections, _.sourceId)
    lazy val incidentConnections: Map[PostId, Set[Connection]] = connectionDefaultNeighbourhood ++ incidenceList[PostId, Connection](connections, _.sourceId, _.targetId)

    private lazy val containmentDefaultNeighbourhood = postsById.mapValues(_ => Set.empty[Containment])
    lazy val incidentParentContainments: Map[PostId, Set[Containment]] = containmentDefaultNeighbourhood ++ directedIncidenceList[PostId, Containment](containments, _.childId)
    lazy val incidentChildContainments: Map[PostId, Set[Containment]] = containmentDefaultNeighbourhood ++ directedIncidenceList[PostId, Containment](containments, _.parentId)
    lazy val incidentContainments: Map[PostId, Set[Containment]] = containmentDefaultNeighbourhood ++ incidenceList[PostId, Containment](containments, _.parentId, _.childId)

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
    lazy val connectionDegree = postDefaultDegree ++
      degreeSequence[PostId, Connection](connections, _.targetId, _.sourceId)
    lazy val containmentDegree = postDefaultDegree ++
      degreeSequence[PostId, Containment](containments, _.parentId, _.childId)

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

    def transitiveChildren(postId: PostId) = _transitiveChildren(postId)
    private val _transitiveChildren: (PostId) => Iterable[PostId] = Memo.mutableHashMapMemo { postId =>
      postsById.isDefinedAt(postId) match {
        case true =>
          depthFirstSearch(postId, children) |> { children =>
            if (children.startInvolvedInCycle) children else children.drop(1)
          } //TODO better?
        case false => Seq.empty
      }
    }
    //TODO: rename to transitiveParentIds:Iterable[PostId]
    // Also provide transitiveParents:Iterable[Post]?
    def transitiveParents(postId: PostId) = _transitiveParents(postId)
    private val _transitiveParents: (PostId) => Iterable[PostId] = Memo.mutableHashMapMemo { postId =>
      postsById.keySet.contains(postId) match {
        case true =>
          depthFirstSearch(postId, parents) |> { parents =>
            if (parents.startInvolvedInCycle) parents else parents.drop(1)
          } //TODO better?
        case false => Seq.empty
      }
    }

    lazy val connectedContainmentComponents: List[Set[PostId]] = {
      connectedComponents(postIds, containmentNeighbours)
    }

    def -(postId: PostId) = removePosts(postId :: Nil)
    def -(connection: Connection) = copy(connections = connections - connection)
    def -(containment: Containment) = copy(containments = containments - containment)
    def -(ownership: Ownership) = copy(ownerships = ownerships - ownership)

    def removePosts(ps: Iterable[PostId]) = {
      val postIds = ps.toSet

      copy(
        postsById = postsById -- ps,
        connections = connections.filterNot{ c => postIds(c.sourceId) || postIds(c.targetId) },
        containments = containments.filterNot{ c => postIds(c.parentId) || postIds(c.childId) },
        ownerships = ownerships.filterNot { o => postIds(o.postId) }
      )
    }
    def removeConnections(cs: Iterable[Connection]) = copy(connections = connections -- cs)
    def removeContainments(cs: Iterable[Containment]) = copy(containments = containments -- cs)
    def removeOwnerships(cs: Iterable[Ownership]) = copy(ownerships = ownerships -- cs)
    def addPosts(cs: Iterable[Post]) = copy(postsById = postsById ++ cs.map(p => p.id -> p))
    def addConnections(cs: Iterable[Connection]) = copy(connections = connections ++ cs)
    def addContainments(cs: Iterable[Containment]) = copy(containments = containments ++ cs)
    def addOwnerships(cs: Iterable[Ownership]) = copy(ownerships = ownerships ++ cs)

    def applyChanges(c: GraphChanges) = {
      copy(
        postsById = postsById ++ c.addPosts.by(_.id) ++ c.updatePosts.by(_.id) -- c.delPosts,
        connections = connections ++ c.addConnections -- c.delConnections,
        containments = containments ++ c.addContainments -- c.delContainments,
        ownerships = ownerships ++ c.addOwnerships -- c.delOwnerships
      )
    }

    def +(post: Post) = copy(postsById = postsById + (post.id -> post))
    def +(connection: Connection) = copy(connections = connections + connection)
    def +(containment: Containment) = copy(containments = containments + containment)

    def +(group: Group) = copy(groupsById = groupsById + (group.id -> group))
    def addGroups(newGroups: Iterable[Group]) = copy(groupsById = groupsById ++ newGroups.by(_.id))
    def +(user: User) = copy(usersById = usersById + (user.id -> user))
    def +(ownership: Ownership) = copy(ownerships = ownerships + ownership)
    def +(membership: Membership) = copy(memberships = memberships + membership)
    def addMemberships(newMemberships: Iterable[Membership]) = copy(memberships = memberships ++ newMemberships)

    def withoutGroup(groupId: GroupId) = copy(
      groupsById = groupsById - groupId,
      ownerships = ownerships.filter(_.groupId != groupId),
      memberships = memberships.filter(_.groupId != groupId)
    )

    lazy val consistent = {
      copy(
        connections = connections.filter{ c => postsById.isDefinedAt(c.sourceId) && postsById.isDefinedAt(c.targetId) && c.sourceId != c.targetId },
        containments = containments.filter{ c => postsById.isDefinedAt(c.parentId) && postsById.isDefinedAt(c.childId) && c.parentId != c.childId },
        ownerships = ownerships.filter { o => postsById.isDefinedAt(o.postId) && groupsById.isDefinedAt(o.groupId) },
        memberships = memberships.filter { m => usersById.isDefinedAt(m.userId) && groupsById.isDefinedAt(m.groupId) }
      )
    }

    lazy val childDepth = depth(children)
    lazy val parentDepth = depth(parents)

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
}
