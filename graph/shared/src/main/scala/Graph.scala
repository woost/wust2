package object graph {
  //TODO: different types of ids to restrict RespondsTo in/out
  //TODO: this also needs to be done as database contstraint
  type AtomId = Long

  // Database layout:
  // atoms(id)
  // posts(atomid, title)
  // respondsto(atomid)
  // containment(atomid)
  // incidences(atomid, in, out)
  //    costraints:
  //      keine atomids für posts
  //      wenn respondsto: in = post, out = post or respondsto
  //      wenn containment: in = post, out = post
  case class Graph(
    posts: Map[AtomId, Post] = Map.empty,
    respondsTos: Map[AtomId, RespondsTo] = Map.empty, //TODO: rename: responding, responses?
    containment: Map[AtomId, Contains] = Map.empty
  ) {
    def respondsToDegree(post: Post) = respondsTos.values.count(r => r.in == post.id || r.out == post.id)
    def containsDegree(post: Post) = containment.values.count(r => r.parent == post.id || r.child == post.id)
    def fullDegree(post: Post) = respondsToDegree(post) + containsDegree(post)
  }
  object Graph {
    def empty = Graph()
  }

  case class Post(id: AtomId, title: String) extends PostPlatformSpecificExtensions
  case class RespondsTo(id: AtomId, in: AtomId, out: AtomId) extends RespondsToPlatformSpecificExtensions
  case class Contains(id: AtomId, parent: AtomId, child: AtomId) extends ContainsPlatformSpecificExtensions
}
