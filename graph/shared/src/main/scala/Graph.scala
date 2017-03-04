
package object graph {
  import collection.mutable
  import util.{algorithm, Pipe}
  import algorithm._
  //TODO: different types of ids to restrict Connects in/out
  //TODO: this also needs to be done as database contstraint
  type AtomId = Long

  final case class Graph(
    posts: Map[AtomId, Post] = Map.empty, // TODO: accept List and generate Map lazily
    connections: Map[AtomId, Connects] = Map.empty,
    containments: Map[AtomId, Contains] = Map.empty
  ) {
    override def toString = s"Graph(${posts.values.toList}.by(_.id),${connections.values.toList}.by(_.id), ${containments.values.toList}.by(_.id))"

    private val postDefaultNeighbourhood = posts.mapValues(_ => Set.empty[AtomId])
    lazy val successors = postDefaultNeighbourhood ++ directedAdjacencyList[AtomId, Connects](connections.values, _.sourceId, _.targetId)
    lazy val predecessors = postDefaultNeighbourhood ++ directedAdjacencyList[AtomId, Connects](connections.values, _.targetId, _.sourceId)
    lazy val neighbours = postDefaultNeighbourhood ++ adjacencyList[AtomId, Connects](connections.values, _.targetId, _.sourceId)

    lazy val children = postDefaultNeighbourhood ++ directedAdjacencyList[AtomId, Contains](containments.values, _.parentId, _.childId)
    lazy val parents = postDefaultNeighbourhood ++ directedAdjacencyList[AtomId, Contains](containments.values, _.childId, _.parentId)
    lazy val containmentNeighbours = postDefaultNeighbourhood ++ adjacencyList[AtomId, Contains](containments.values, _.parentId, _.childId)

    //TODO: remove .mapValues(_.map(_.id))
    // be aware that incomingConnections and incident connections can be queried with a hyperedge ( connection )
    // that's why the need default values from connectionDefaultNeighbourhood
    private val connectionDefaultNeighbourhood = connections.mapValues(_ => Set.empty[AtomId])
    lazy val incomingConnections = postDefaultNeighbourhood ++ connectionDefaultNeighbourhood ++
      directedIncidenceList[AtomId, Connects](connections.values, _.targetId).mapValues(_.map(_.id))
    lazy val outgoingConnections = postDefaultNeighbourhood ++ directedIncidenceList[AtomId, Connects](connections.values, _.sourceId).mapValues(_.map(_.id))
    lazy val incidentConnections = postDefaultNeighbourhood ++ connectionDefaultNeighbourhood ++
      incidenceList[AtomId, Connects](connections.values, _.sourceId, _.targetId).mapValues(_.map(_.id))

    lazy val incidentParentContains = postDefaultNeighbourhood ++ directedIncidenceList[AtomId, Contains](containments.values, _.childId).mapValues(_.map(_.id))
    lazy val incidentChildContains = postDefaultNeighbourhood ++ directedIncidenceList[AtomId, Contains](containments.values, _.parentId).mapValues(_.map(_.id))
    lazy val incidentContains = postDefaultNeighbourhood ++ incidenceList[AtomId, Contains](containments.values, _.parentId, _.childId).mapValues(_.map(_.id))

    def incidentConnectionsDeep(atomId: AtomId) = {
      // Currently connects.in must be a Post, so no cycles can occour
      // TODO: algorithm to build for all atomIds simultanously

      var next = incidentConnections.get(atomId).toList.flatten
      var result: List[AtomId] = Nil
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
    lazy val connectionDegree = postDefaultDegree ++ degreeSequence[AtomId, Connects](connections.values, _.targetId, _.sourceId)
    lazy val containmentDegree = postDefaultDegree ++ degreeSequence[AtomId, Contains](containments.values, _.parentId, _.childId)
    def fullDegree(post: AtomId) = {
      println(post)
      println(connectionDegree.keys.toSeq.sorted.toString)
      println(containmentDegree.keys.toSeq.sorted.toString)
      println(posts.keys.toSeq.sorted.toString)
      connectionDegree(post) + containmentDegree(post)
    }
    def fullDegree(connection: Connects) = 2

    def removePosts(atomIds: Iterable[AtomId]) = atomIds.foldLeft(this)((g, p) => g removePost p) //TODO: more efficient
    def removePost(atomId: AtomId) = {
      val removedPosts = posts.get(atomId).map(_.id)
      val removedConnections = incidentConnectionsDeep(atomId)
      val removedContains = incidentContains.get(atomId).toList.flatten
      copy(
        posts = posts -- removedPosts,
        connections = connections -- removedConnections,
        containments = containments -- removedContains
      )
    }

    def removeConnections(atomIds: Iterable[AtomId]) = atomIds.foldLeft(this)((g, e) => g removeConnection e)
    def removeConnection(atomId: AtomId) = {
      val removedConnections = incidentConnectionsDeep(atomId)
      copy(
        connections = connections -- removedConnections - atomId
      )
    }

    def removeContainment(atomId: AtomId) = {
      copy(
        containments = containments - atomId
      )
    }

    def involvedInCycle(atomId: AtomId): Boolean = {
      children(atomId).exists(child => depthFirstSearch(child, children).exists(_ == atomId))
    }

    def transitiveChildren(postId: AtomId) = depthFirstSearch(postId, children) |> { children => if (involvedInCycle(postId)) children else children.drop(1) } //TODO better?
    def transitiveParents(postId: AtomId) = depthFirstSearch(postId, parents) |> { parents => if (involvedInCycle(postId)) parents else parents.drop(1) } //TODO better?

    def +(p: Post) = copy(posts = posts + (p.id -> p))
    def +(c: Connects) = copy(connections = connections + (c.id -> c))
    def +(c: Contains) = copy(containments = containments + (c.id -> c))

    def ++(cs: Iterable[Connects]) = copy(connections = connections ++ cs.map(c => c.id -> c))

    def consistent = copy(
      connections = connections.filter { case (cid, c) => posts.get(c.sourceId).isDefined && posts.get(c.targetId).isDefined },
      containments = containments.filter { case (cid, c) => posts.get(c.childId).isDefined && posts.get(c.parentId).isDefined }
    )

    lazy val depth: collection.Map[AtomId, Int] = {
      val tmpDepths = mutable.HashMap[AtomId, Int]()
      val visited = mutable.HashSet[AtomId]() // to handle cycles
      def getDepth(id: AtomId): Int = {
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
  final case class Post(id: AtomId, title: String)
  object Post { def apply(title: String): Post = Post(0L, title) }
  final case class Connects(id: AtomId, sourceId: AtomId, targetId: AtomId)
  object Connects { def apply(in: AtomId, out: AtomId): Connects = Connects(0L, in, out) }
  //TODO: reverse direction of contains?
  final case class Contains(id: AtomId, parentId: AtomId, childId: AtomId)
  object Contains { def apply(parentId: AtomId, childId: AtomId): Contains = Contains(0L, parentId, childId) }
}
