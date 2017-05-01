package wust

import ids._

package object graph {
  import wust.util.Pipe
  import wust.util.algorithm._
  import wust.util.collection._

  import collection.{breakOut, mutable}

  case class Ownership(postId: PostId, groupId: GroupId)
  case class Membership(userId: UserId, groupId: GroupId)
  case class User(id: UserId, name: String, isImplicit: Boolean, revision: Int) //TODO: derive: identify only by id
  case class Group(id: GroupId)

  //TODO: rename Post -> ???
  sealed trait Atom
  final case class Post(id: PostId, title: String) extends Atom
  final case class Connection(
    id: ConnectionId,
    sourceId: PostId,
    targetId: ConnectableId
  ) extends Atom

  final case class Containment(id: ContainmentId, parentId: PostId, childId: PostId)
    extends Atom

  object Graph {
    def empty =
      new Graph(
        Map.empty,
        Map.empty,
        Map.empty,
        Map.empty,
        Set.empty,
        Map.empty,
        Set.empty
      )

    def apply(
      posts: Iterable[Post] = Nil,
      connections: Iterable[Connection] = Nil,
      containments: Iterable[Containment] = Nil,
      groups: Iterable[Group] = Nil,
      ownerships: Iterable[Ownership] = Nil,
      users: Iterable[User] = Nil,
      memberships: Iterable[Membership] = Nil
    ): Graph = {
      new Graph(
        posts.by(_.id),
        connections.by(_.id),
        containments.by(_.id),
        groups.by(_.id),
        ownerships.toSet,
        users.by(_.id),
        memberships.toSet
      )
    }
  }

  final case class Graph( //TODO: costom pickler over lists instead of maps to save traffic
    postsById: Map[PostId, Post],
    connectionsById: Map[ConnectionId, Connection],
    containmentsById: Map[ContainmentId, Containment],
    groupsById: Map[GroupId, Group],
    ownerships: Set[Ownership],
    usersById: Map[UserId, User],
    memberships: Set[Membership]
  ) {

    lazy val posts: Iterable[Post] = postsById.values
    lazy val groups: Iterable[Group] = groupsById.values
    lazy val users: Iterable[User] = usersById.values
    lazy val connections: Iterable[Connection] = connectionsById.values
    lazy val containments: Iterable[Containment] = containmentsById.values

    override def toString =
      s"Graph(${posts.map(_.id.id).mkString(" ")},${
        connections
          .map(c => s"${c.id.id}:${c.sourceId.id}->${c.targetId.id}")
          .mkString(", ")
      }, ${
        containments
          .map(c => s"${c.id.id}:${c.parentId.id}⊂${c.childId.id}")
          .mkString(", ")
      },groups:${groupsById.keys}, ownerships: ${ownerships.map(o => s"${o.postId} -> ${o.groupId}").mkString(", ")}, users: ${usersById.keys}, memberships: ${memberships.map(o => s"${o.userId} -> ${o.groupId}").mkString(", ")})"
    def toSummaryString = s"Graph(posts: ${posts.size}, connections: ${connections.size}, containments: ${containments.size}, groups: ${groups.size}, ownerships: ${ownerships.size}, users: ${users.size}, memberships: ${memberships.size})"

    private val postDefaultNeighbourhood =
      postsById.mapValues(_ => Set.empty[PostId])
    lazy val successors: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, (PostId, PostId), PostId](connections.collect {
      case Connection(_, in, out: PostId) => (in, out)
    }, _._1, _._2)
    lazy val predecessors: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, (PostId, PostId), PostId](connections.collect {
      case Connection(_, in, out: PostId) => (in, out)
    }, _._2, _._1)
    lazy val neighbours: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ adjacencyList[PostId, (PostId, PostId)](connections.collect {
      case Connection(_, in, out: PostId) => (in, out)
    }, _._2, _._1)
    // TODO: lazy val neighbours: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ adjacencyList[PostId, Connection](connections, _.targetId, _.sourceId)

    lazy val children: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Containment, PostId](containments, _.parentId, _.childId)
    lazy val parents: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Containment, PostId](containments, _.childId, _.parentId)
    lazy val containmentNeighbours: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ adjacencyList[PostId, Containment](containments, _.parentId, _.childId)

    //TODO: remove .mapValues(_.map(_.id))
    // be aware that incomingConnections and incident connections can be queried with a hyperedge ( connection )
    // that's why the need default values from connectionDefaultNeighbourhood
    private val connectionDefaultNeighbourhood =
      postsById.mapValues(_ => Set.empty[ConnectionId])
    private val hyperConnectionDefaultNeighbourhood: Map[ConnectableId, Set[ConnectionId]] = connectionDefaultNeighbourhood ++ connectionsById
      .mapValues(_ => Set.empty[ConnectionId])
    private val containmentsDefaultNeighbourhood =
      postsById.mapValues(_ => Set.empty[ContainmentId])
    lazy val incomingConnections: Map[PostId, Set[ConnectionId]] = connectionDefaultNeighbourhood ++
      directedIncidenceList[PostId, Connection](connections.collect {
        case c @ Connection(_, _, _: PostId) => c
      }, _.targetId.asInstanceOf[PostId]).mapValues(_.map(_.id))
    lazy val outgoingConnections: Map[PostId, Set[ConnectionId]] = connectionDefaultNeighbourhood ++
      directedIncidenceList[PostId, Connection](connections, _.sourceId)
      .mapValues(_.map(_.id))

    lazy val incidentConnections: Map[ConnectableId, Set[ConnectionId]] = hyperConnectionDefaultNeighbourhood ++
      incidenceList[ConnectableId, Connection](
        connections,
        _.sourceId,
        _.targetId
      ).mapValues(_.map(_.id))

    lazy val incidentParentContainments: Map[PostId, Set[ContainmentId]] = containmentsDefaultNeighbourhood ++ directedIncidenceList[PostId, Containment](containments, _.childId).mapValues(_.map(_.id))
    lazy val incidentChildContainments: Map[PostId, Set[ContainmentId]] = containmentsDefaultNeighbourhood ++ directedIncidenceList[PostId, Containment](containments, _.parentId).mapValues(_.map(_.id))
    lazy val incidentContainments: Map[PostId, Set[ContainmentId]] = containmentsDefaultNeighbourhood ++ incidenceList[PostId, Containment](containments, _.parentId, _.childId).mapValues(_.map(_.id))

    def incidentConnectionsDeep(id: ConnectableId): Iterable[ConnectionId] = {
      // Currently Connection.sourceId must be a Post, so no cycles can occour
      // TODO: algorithm to build for all ids simultanously

      var next: List[ConnectionId] = incidentConnections.get(id).toList.flatten
      var result: List[ConnectionId] = Nil
      var i = 0
      while (next.nonEmpty && i < 10) {
        result ::= next.head
        val candidates = incidentConnections(next.head).toList
        next = next.tail ::: candidates
        i += 1
      }
      result
    }

    private val groupDefaultPosts: Map[GroupId, Set[PostId]] = groupsById.mapValues(_ => Set.empty[PostId])
    private val postDefaultGroups = postsById.mapValues(_ => Set.empty[GroupId])
    lazy val postsByGroupId: Map[GroupId, Set[PostId]] = groupDefaultPosts ++ directedAdjacencyList[GroupId, Ownership, PostId](ownerships, _.groupId, _.postId)
    lazy val groupsByPostId: Map[PostId, Set[GroupId]] = postDefaultGroups ++ directedAdjacencyList[PostId, Ownership, GroupId](ownerships, _.postId, _.groupId)

    private val groupDefaultUsers: Map[GroupId, Set[UserId]] = groupsById.mapValues(_ => Set.empty[UserId])
    private val userDefaultGroups = usersById.mapValues(_ => Set.empty[GroupId])
    lazy val usersByGroupId: Map[GroupId, Set[UserId]] = groupDefaultUsers ++ directedAdjacencyList[GroupId, Membership, UserId](memberships, _.groupId, _.userId)
    lazy val groupsByUserId: Map[UserId, Set[GroupId]] = userDefaultGroups ++ directedAdjacencyList[UserId, Membership, GroupId](memberships, _.userId, _.groupId)

    private val postDefaultDegree = postsById.mapValues(_ => 0)
    lazy val connectionDegree = postDefaultDegree ++ degreeSequence[ConnectableId, Connection](connections, _.targetId, _.sourceId)
    lazy val containmentDegree = postDefaultDegree ++ degreeSequence[PostId, Containment](
      containments,
      _.parentId,
      _.childId
    )

    val fullDegree: ConnectableId => Int = {
      case p: PostId => connectionDegree(p) + containmentDegree(p)
      case _: ConnectionId => 2
      case _ => ???
    }

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

    def transitiveChildren(postId: PostId) = postsById.keySet.contains(postId) match {
      case true =>
        depthFirstSearch(postId, children) |> { children =>
          if (involvedInContainmentCycle(postId)) children else children.drop(1)
        } //TODO better?
      case false => Seq.empty
    }
    //TODO: rename to transitiveParentsIds:Iterable[PostId]
    // Also provide transitiveParents:Iterable[Post]?
    def transitiveParents(postId: PostId): Iterable[PostId] = postsById.keySet.contains(postId) match {
      case true =>
        depthFirstSearch(postId, parents) |> { parents =>
          if (involvedInContainmentCycle(postId)) parents else parents.drop(1)
        } //TODO better?
      case false => Seq.empty
    }

    val `-`: AtomId => Graph = {
      case id: PostId =>
        val removedPosts = postsById.get(id).map(_.id)
        val removedConnections = incidentConnectionsDeep(id)
        val removedContainments = incidentContainments.get(id).toList.flatten
        copy(
          postsById = postsById -- removedPosts,
          connectionsById = connectionsById -- removedConnections,
          containmentsById = containmentsById -- removedContainments
        )
      case id: ConnectionId =>
        val removedConnections = incidentConnectionsDeep(id)
        copy(
          connectionsById = connectionsById -- removedConnections - id
        )
      case id: ContainmentId =>
        copy(
          containmentsById = containmentsById - id
        )
      case _ => ???
    }

    def --(ids: Iterable[AtomId]) =
      ids.foldLeft(this)((g, p) => g - p) //TODO: more efficient

    //TODO: also accept Ownerships,Groups,Users,Memberships -> should ownerships and groups have atomids?
    val `+`: Atom => Graph = {
      case p: Post => copy(postsById = postsById + (p.id -> p))
      case c: Connection => copy(connectionsById = connectionsById + (c.id -> c))
      case c: Containment =>
        copy(containmentsById = containmentsById + (c.id -> c))
    }

    def ++(atoms: Iterable[Atom]) = atoms.foldLeft(this)((g, a) => g + a)

    def consistent = {
      val invalidConnections = connections
        .filter { c =>
          !postsById.isDefinedAt(c.sourceId) || !(c.targetId match {
            case t: PostId => postsById.isDefinedAt(t)
            case c: ConnectionId => connectionsById.isDefinedAt(c)
            case u: UnknownConnectableId =>
              postsById.isDefinedAt(PostId(u.id)) || connectionsById
                .isDefinedAt(ConnectionId(u.id))
          })
        }
        .map(_.id)
        .flatMap(c => incidentConnectionsDeep(c) ++ List(c))

      val invalidContainments = containments
        .filter { c =>
          !postsById.isDefinedAt(c.childId) || !postsById.isDefinedAt(
            c.parentId
          )
        }
        .map(_.id)

      val validOwnerships = ownerships.filter { o =>
        postsById.isDefinedAt(o.postId) && groupsById.isDefinedAt(o.groupId)
      }

      val validMemberships = memberships.filter { m =>
        usersById.isDefinedAt(m.userId) && groupsById.isDefinedAt(m.groupId)
      }
      println("validMemberships: " + validMemberships)

      val g = this -- invalidConnections -- invalidContainments
      g.copy(
        connectionsById = g.connectionsById.mapValues {
          case c @ Connection(_, _, u: UnknownConnectableId) =>
            c.copy(
              targetId = g.postsById
                .get(PostId(u.id))
                .map(_.id)
                .getOrElse(g.connectionsById(ConnectionId(u.id)).id)
            )
          case valid => valid
        },
        ownerships = validOwnerships,
        memberships = validMemberships
      )
    }

    lazy val depth: collection.Map[PostId, Int] = {
      val tmpDepths = mutable.HashMap[PostId, Int]()
      val visited = mutable.HashSet[PostId]() // to handle cycles
      def getDepth(id: PostId): Int = {
        tmpDepths.getOrElse(id, {
          if (!visited(id)) {
            visited += id

            val c = children(id)
            val d = if (c.isEmpty) 0 else c.map(getDepth).max + 1
            tmpDepths(id) = d
            d
          } else 0 // cycle
        })
      }

      for (id <- postsById.keys if !tmpDepths.isDefinedAt(id)) {
        getDepth(id)
      }
      tmpDepths
    }
  }
}
