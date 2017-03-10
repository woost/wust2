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
  case class UnknownConnectableId(id: IdType) extends ConnectableId
  object UnknownConnectableId { implicit def fromIdType(id: IdType) = UnknownConnectableId(id) }
  case class ContainsId(id: IdType) extends AtomId
  object ContainsId { implicit def fromIdType(id: IdType) = ContainsId(id) }

  final case class Graph(
    posts: Map[PostId, Post] = Map.empty, // TODO: accept List and generate Map lazily
    connections: Map[ConnectsId, Connects] = Map.empty,
    containments: Map[ContainsId, Contains] = Map.empty
  ) {
    override def toString = s"Graph(${posts.values.toList}.by(_.id),${connections.values.toList}.by(_.id), ${containments.values.toList}.by(_.id))"

    private val postDefaultNeighbourhood = posts.mapValues(_ => Set.empty[PostId])
    lazy val successors: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, (PostId, PostId)](connections.values.collect { case Connects(_, in, out: PostId) => (in, out) }, _._1, _._2)
    lazy val predecessors: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, (PostId, PostId)](connections.values.collect { case Connects(_, in, out: PostId) => (in, out) }, _._2, _._1)
    lazy val neighbours: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ adjacencyList[PostId, (PostId, PostId)](connections.values.collect { case Connects(_, in, out: PostId) => (in, out) }, _._2, _._1)
    // TODO: lazy val neighbours: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ adjacencyList[PostId, Connects](connections.values, _.targetId, _.sourceId)

    lazy val children: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Contains](containments.values, _.parentId, _.childId)
    lazy val parents: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ directedAdjacencyList[PostId, Contains](containments.values, _.childId, _.parentId)
    lazy val containmentNeighbours: Map[PostId, Set[PostId]] = postDefaultNeighbourhood ++ adjacencyList[PostId, Contains](containments.values, _.parentId, _.childId)

    //TODO: remove .mapValues(_.map(_.id))
    // be aware that incomingConnections and incident connections can be queried with a hyperedge ( connection )
    // that's why the need default values from connectionDefaultNeighbourhood
    private val connectionDefaultNeighbourhood = posts.mapValues(_ => Set.empty[ConnectsId])
    private val hyperConnectionDefaultNeighbourhood: Map[ConnectableId, Set[ConnectsId]] = connectionDefaultNeighbourhood ++ connections.mapValues(_ => Set.empty[ConnectsId])
    private val containsDefaultNeighbourhood = posts.mapValues(_ => Set.empty[ContainsId])
    lazy val incomingConnections: Map[PostId, Set[ConnectsId]] = connectionDefaultNeighbourhood ++
      directedIncidenceList[PostId, Connects](connections.values.collect { case c @ Connects(_, _, _: PostId) => c }, _.targetId.asInstanceOf[PostId]).mapValues(_.map(_.id))
    lazy val outgoingConnections: Map[PostId, Set[ConnectsId]] = connectionDefaultNeighbourhood ++
      directedIncidenceList[PostId, Connects](connections.values, _.sourceId).mapValues(_.map(_.id))

    lazy val incidentConnections: Map[ConnectableId, Set[ConnectsId]] = hyperConnectionDefaultNeighbourhood ++
      incidenceList[ConnectableId, Connects](connections.values, _.sourceId, _.targetId).mapValues(_.map(_.id))

    lazy val incidentParentContains: Map[PostId, Set[ContainsId]] = containsDefaultNeighbourhood ++ directedIncidenceList[PostId, Contains](containments.values, _.childId).mapValues(_.map(_.id))
    lazy val incidentChildContains: Map[PostId, Set[ContainsId]] = containsDefaultNeighbourhood ++ directedIncidenceList[PostId, Contains](containments.values, _.parentId).mapValues(_.map(_.id))
    lazy val incidentContains: Map[PostId, Set[ContainsId]] = containsDefaultNeighbourhood ++ incidenceList[PostId, Contains](containments.values, _.parentId, _.childId).mapValues(_.map(_.id))

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

    private val postDefaultDegree = posts.mapValues(_ => 0)
    lazy val connectionDegree = postDefaultDegree ++ degreeSequence[ConnectableId, Connects](connections.values, _.targetId, _.sourceId)
    lazy val containmentDegree = postDefaultDegree ++ degreeSequence[PostId, Contains](containments.values, _.parentId, _.childId)

    val fullDegree: ConnectableId => Int = {
      case p: PostId => connectionDegree(p) + containmentDegree(p)
      case c: ConnectsId => 2
    }

    def involvedInCycle(id: PostId): Boolean = {
      children(id).exists(child => depthFirstSearch(child, children).exists(_ == id))
    }

    def transitiveChildren(postId: PostId) = depthFirstSearch(postId, children) |> { children => if (involvedInCycle(postId)) children else children.drop(1) } //TODO better?
    def transitiveParents(postId: PostId) = depthFirstSearch(postId, parents) |> { parents => if (involvedInCycle(postId)) parents else parents.drop(1) } //TODO better?

    val `-`: AtomId => Graph = {
      case id: PostId =>
        val removedPosts = posts.get(id).map(_.id)
        val removedConnections = incidentConnectionsDeep(id)
        val removedContains = incidentContains.get(id).toList.flatten
        copy(
          posts = posts -- removedPosts,
          connections = connections -- removedConnections,
          containments = containments -- removedContains
        )
      case id: ConnectsId =>
        val removedConnections = incidentConnectionsDeep(id)
        copy(
          connections = connections -- removedConnections - id
        )
      case id: ContainsId =>
        copy(
          containments = containments - id
        )
    }

    def --(ids: Iterable[AtomId]) = ids.foldLeft(this)((g, p) => g - p) //TODO: more efficient

    val `+`: Atom => Graph = {
      case p: Post => copy(posts = posts + (p.id -> p))
      case c: Connects => copy(connections = connections + (c.id -> c))
      case c: Contains=> copy(containments = containments + (c.id -> c))
    }

    def ++(atoms: Iterable[Atom]) = atoms.foldLeft(this)((g, a) => g + a)

    def consistent = copy(
      connections = connections.flatMap {
        case (cid, c) if posts.get(c.sourceId).isDefined =>
          val newTarget = (c.targetId match {
            case t: PostId => posts.get(t).map(_.id)
            case c: ConnectsId => connections.get(c).map(_.id)
            case u: UnknownConnectableId => posts.get(PostId(u.id)).map(_.id) orElse connections.get(ConnectsId(u.id)).map(_.id)
          })
          newTarget.map(target => cid -> c.copy(targetId = target))
        case _ => None
      },
      containments = containments.filter {
        case (cid, c) => posts.get(c.childId).isDefined && posts.get(c.parentId).isDefined
      }
    )

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

      for (id <- posts.keys if !tmpDepths.isDefinedAt(id)) {
        getDepth(id)
      }
      tmpDepths
    }
  }

  object Graph {
    def empty = Graph()
  }

  //TODO: rename Post -> ???
  sealed trait Atom
  final case class Post(id: PostId, title: String) extends Atom
  object Post { def apply(title: String): Post = Post(0L, title) }
  final case class Connects(id: ConnectsId, sourceId: PostId, targetId: ConnectableId) extends Atom
  object Connects { def apply(in: PostId, out: ConnectableId): Connects = Connects(0L, in, out) }
  //TODO: reverse direction of contains?
  final case class Contains(id: ContainsId, parentId: PostId, childId: PostId) extends Atom
  object Contains { def apply(parentId: PostId, childId: PostId): Contains = Contains(0L, parentId, childId) }
}
