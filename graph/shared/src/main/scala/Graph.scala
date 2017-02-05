package object graph {
  //TODO: different types of ids to restrict Connects in/out
  //TODO: this also needs to be done as database contstraint
  type AtomId = Long

  // Database layout:
  final case class Graph(
    posts: Map[AtomId, Post] = Map.empty,
    connections: Map[AtomId, Connects] = Map.empty, //TODO: rename: responding, responses?
    containments: Map[AtomId, Contains] = Map.empty
  ) {
    //TODO: acceleration Datastructures from pharg
    def connectionDegree(post: Post) = connections.values.count(r => r.sourceId == post.id || r.targetId == post.id)
    def containmentDegree(post: Post) = containments.values.count(c => c.parentId == post.id || c.childId == post.id)
    def fullDegree(post: Post) = connectionDegree(post) + containmentDegree(post)
    def fullDegree(connection: Connects) = 2

    def incidentConnections(atomId: AtomId) = connections.values.collect { case r if r.sourceId == atomId || r.targetId == atomId => r.id }
    def incidentContains(atomId: AtomId) = containments.values.collect { case c if c.parentId == atomId || c.childId == atomId => c.id }
    def incidentParentContains(atomId: AtomId) = containments.values.collect { case c if c.childId == atomId => c.id }
    def incidentChildContains(atomId: AtomId) = containments.values.collect { case c if c.parentId == atomId => c.id }

    def incidentConnectionsDeep(atomId: AtomId) = {
      // Connects.in must be a Post, so no cycles can occour

      var next = incidentConnections(atomId).toList
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

    def removePost(atomId: AtomId) = {
      val removedPosts = posts.get(atomId).map(_.id)
      val removedConnections = incidentConnectionsDeep(atomId)
      val removedContains = incidentContains(atomId)
      copy(
        posts = posts -- removedPosts,
        connections = connections -- removedConnections,
        containments = containments -- removedContains
      )
    }

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

    def parents(postId: AtomId): Seq[Post] = containments.values.collect { case c if c.childId == postId => posts(c.parentId) }.toSeq //TODO: breakout with generic on requested collection type
    def children(postId: AtomId): Seq[Post] = containments.values.collect { case c if c.parentId == postId => posts(c.childId) }.toSeq //TODO: breakout with generic on requested collection type
    def transitiveChildren(postId: AtomId) = Algorithms.depthFirstSearch[Post](posts(postId), (p: Post) => children(p.id))
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
