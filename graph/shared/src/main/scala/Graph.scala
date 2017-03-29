package wust

package object graph {
  import collection.mutable
  import wust.util.Pipe
  import wust.util.collection._
  import wust.util.algorithm._

  //TODO: this also needs to be done as database contstraint
  type IdType = Long
  //TODO anyval
  sealed trait AtomId {
    def id: IdType
  }
  object AtomId {
    implicit def ordering[A <: AtomId]: Ordering[A] = Ordering.by(_.id)
  }
  sealed trait ConnectableId extends AtomId
  case class PostId(id: IdType) extends ConnectableId
  object PostId { implicit def fromIdType(id: IdType) = PostId(id) }
  case class ConnectsId(id: IdType) extends ConnectableId
  object ConnectsId { implicit def fromIdType(id: IdType) = ConnectsId(id) }
  case class ContainsId(id: IdType) extends AtomId
  object ContainsId { implicit def fromIdType(id: IdType) = ContainsId(id) }
  case class UnknownConnectableId(id: IdType) extends ConnectableId

  object Graph {
    def empty = new Graph(Map.empty, Map.empty, Map.empty)

    def apply(
      posts: Iterable[Post] = Nil,
      connections: Iterable[Connects] = Nil,
      containments: Iterable[Contains] = Nil
    ): Graph = {
      new Graph(
        posts.by(_.id),
        connections.by(_.id),
        containments.by(_.id)
      )
    }
  }

  final case class Graph( //TODO: private constructor and costom picklers over lists instead of maps
    postsById: Map[PostId, Post],
    connectionsById: Map[ConnectsId, Connects], //TODO: rename to connectionIdsById and also provide connectionsById?
    containmentsById: Map[ContainsId, Contains]
  ) {

    lazy val posts: Iterable[Post] = postsById.values
    lazy val connections: Iterable[Connects] = connectionsById.values
    lazy val containments: Iterable[Contains] = containmentsById.values

    override def toString = s"Graph(${posts.map(_.id.id).mkString(" ")},${connections.map(c => s"[${c.id.id}]${c.sourceId.id} -> ${c.targetId.id}").mkString(", ")}, ${containments.map(c => s"[${c.id.id}]${c.parentId.id} ⊂ ${c.childId.id}").mkString(", ")})"

    private val postDefaultNeighbourhood = postsById.mapValues(_ => Set.empty[PostId])
    lazy val successors: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, (PostId, PostId)](connections.collect { case Connects(_, in, out: PostId) => (in, out) }, _._1, _._2)
    lazy val predecessors: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, (PostId, PostId)](connections.collect { case Connects(_, in, out: PostId) => (in, out) }, _._2, _._1)
    lazy val neighbours: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ adjacencyList[PostId, (PostId, PostId)](connections.collect { case Connects(_, in, out: PostId) => (in, out) }, _._2, _._1)
    // TODO: lazy val neighbours: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ adjacencyList[PostId, Connects](connections, _.targetId, _.sourceId)

    lazy val children: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Contains](containments, _.parentId, _.childId)
    lazy val parents: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Contains](containments, _.childId, _.parentId)
    lazy val containmentNeighbours: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ adjacencyList[PostId, Contains](containments, _.parentId, _.childId)

    //TODO: remove .mapValues(_.map(_.id))
    // be aware that incomingConnections and incident connections can be queried with a hyperedge ( connection )
    // that's why the need default values from connectionDefaultNeighbourhood
    private val connectionDefaultNeighbourhood = postsById.mapValues(_ => Set.empty[ConnectsId])
    private val hyperConnectionDefaultNeighbourhood: Map[ConnectableId, Set[ConnectsId]] = connectionDefaultNeighbourhood ++ connectionsById.mapValues(_ => Set.empty[ConnectsId])
    private val containsDefaultNeighbourhood = postsById.mapValues(_ => Set.empty[ContainsId])
    lazy val incomingConnections: Map[PostId, Set[ConnectsId]] = connectionDefaultNeighbourhood ++
      directedIncidenceList[PostId, Connects](connections.collect { case c @ Connects(_, _, _: PostId) => c }, _.targetId.asInstanceOf[PostId]).mapValues(_.map(_.id))
    lazy val outgoingConnections: Map[PostId, Set[ConnectsId]] = connectionDefaultNeighbourhood ++
      directedIncidenceList[PostId, Connects](connections, _.sourceId).mapValues(_.map(_.id))

    lazy val incidentConnections: Map[ConnectableId, Set[ConnectsId]] = hyperConnectionDefaultNeighbourhood ++
      incidenceList[ConnectableId, Connects](connections, _.sourceId, _.targetId).mapValues(_.map(_.id))

    lazy val incidentParentContains: Map[PostId, Set[ContainsId]] = containsDefaultNeighbourhood ++ directedIncidenceList[PostId, Contains](containments, _.childId).mapValues(_.map(_.id))
    lazy val incidentChildContains: Map[PostId, Set[ContainsId]] = containsDefaultNeighbourhood ++ directedIncidenceList[PostId, Contains](containments, _.parentId).mapValues(_.map(_.id))
    lazy val incidentContains: Map[PostId, Set[ContainsId]] = containsDefaultNeighbourhood ++ incidenceList[PostId, Contains](containments, _.parentId, _.childId).mapValues(_.map(_.id))

    def incidentConnectionsDeep(id: ConnectableId): Iterable[ConnectsId] = {
      // Currently connects.in must be a Post, so no cycles can occour
      // TODO: algorithm to build for all ids simultanously

      var next: List[ConnectsId] = incidentConnections.get(id).toList.flatten
      var result: List[ConnectsId] = Nil
      var i = 0
      while (next.nonEmpty && i < 10) {
        result ::= next.head
        val candidates = incidentConnections(next.head).toList
        next = next.tail ::: candidates
        i += 1
      }
      result
    }

    private val postDefaultDegree = postsById.mapValues(_ => 0)
    lazy val connectionDegree = postDefaultDegree ++ degreeSequence[ConnectableId, Connects](connections, _.targetId, _.sourceId)
    lazy val containmentDegree = postDefaultDegree ++ degreeSequence[PostId, Contains](containments, _.parentId, _.childId)

    val fullDegree: ConnectableId => Int = {
      case p: PostId => connectionDegree(p) + containmentDegree(p)
      case c: ConnectsId => 2
      case _ => ???
    }

    def involvedInContainmentCycle(id: PostId): Boolean = {
      children(id).exists(child => depthFirstSearch(child, children).exists(_ == id))
    }
    // TODO: maybe fast involved-in-cycle-algorithm?
    // breadth-first-search starting at successors and another one starting at predecessors in different direction.
    // When both queues contain the same elements, we can stop, because we found a cycle

    def transitiveChildren(postId: PostId) = depthFirstSearch(postId, children) |> { children => if (involvedInContainmentCycle(postId)) children else children.drop(1) } //TODO better?
    //TODO: rename to transitiveParentsIds:Iterable[PostId]
    // Also provide transitiveParents:Iterable[Post]?
    def transitiveParents(postId: PostId): Iterable[PostId] = depthFirstSearch(postId, parents) |> { parents => if (involvedInContainmentCycle(postId)) parents else parents.drop(1) } //TODO better?

    val `-`: AtomId => Graph = {
      case id: PostId =>
        val removedPosts = postsById.get(id).map(_.id)
        val removedConnections = incidentConnectionsDeep(id)
        val removedContains = incidentContains.get(id).toList.flatten
        copy(
          postsById = postsById -- removedPosts,
          connectionsById = connectionsById -- removedConnections,
          containmentsById = containmentsById -- removedContains
        )
      case id: ConnectsId =>
        val removedConnections = incidentConnectionsDeep(id)
        copy(
          connectionsById = connectionsById -- removedConnections - id
        )
      case id: ContainsId =>
        copy(
          containmentsById = containmentsById - id
        )
      case _ => ???
    }

    def --(ids: Iterable[AtomId]) = ids.foldLeft(this)((g, p) => g - p) //TODO: more efficient

    val `+`: Atom => Graph = {
      case p: Post => copy(postsById = postsById + (p.id -> p))
      case c: Connects => copy(connectionsById = connectionsById + (c.id -> c))
      case c: Contains => copy(containmentsById = containmentsById + (c.id -> c))
    }

    def ++(atoms: Iterable[Atom]) = atoms.foldLeft(this)((g, a) => g + a)

    def consistent = {
      val invalidConnects = connections.filter { c =>
        !postsById.isDefinedAt(c.sourceId) || !(c.targetId match {
          case t: PostId => postsById.isDefinedAt(t)
          case c: ConnectsId => connectionsById.isDefinedAt(c)
          case u: UnknownConnectableId => (postsById.isDefinedAt(PostId(u.id)) || connectionsById.isDefinedAt(ConnectsId(u.id)))
        })
      }.map(_.id).flatMap(c => incidentConnectionsDeep(c) ++ List(c))

      val invalidContainments = containments.filter {
        c => !postsById.isDefinedAt(c.childId) || !postsById.isDefinedAt(c.parentId)
      }.map(_.id)

      val g = this -- invalidConnects -- invalidContainments
      g.copy(connectionsById = g.connectionsById.mapValues {
        case c @ Connects(_, _, u: UnknownConnectableId) => c.copy(targetId = g.postsById.get(PostId(u.id)).map(_.id).getOrElse(g.connectionsById(ConnectsId(u.id)).id))
        case valid => valid
      })
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

  //TODO: rename Post -> ???
  sealed trait Atom

  final case class Post(id: PostId, title: String) extends Atom
  object Post { def apply(title: String): Post = Post(0L, title) }

  //TODO: rename to Connection
  final case class Connects(id: ConnectsId, sourceId: PostId, targetId: ConnectableId) extends Atom
  object Connects { def apply(in: PostId, out: ConnectableId): Connects = Connects(0L, in, out) }

  //TODO: rename to Containment
  //TODO: reverse direction of contains?
  final case class Contains(id: ContainsId, parentId: PostId, childId: PostId) extends Atom
  object Contains { def apply(parentId: PostId, childId: PostId): Contains = Contains(0L, parentId, childId) }
}
