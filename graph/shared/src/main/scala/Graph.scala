package object graph {
  //TODO: different types of ids to restrict RespondsTo in/out
  //TODO: this also needs to be done as database contstraint
  type AtomId = Long

  // Database layout:
  // atoms(id)
  // posts(atomid, title)
  // respondsto(atomid)
  // contains(atomid)
  // incidences(atomid, in, out)
  //    costraints:
  //      keine atomids für posts
  //      wenn respondsto: in = post, out = post or respondsto
  //      wenn contains: in = post, out = post
  case class Graph(
    posts: Map[AtomId, Post],
    respondsTos: Map[AtomId, RespondsTo]
  )
  object Graph {
    def empty = Graph(Map.empty, Map.empty)
  }

  case class Post(id: AtomId, title: String) extends PostPlatformSpecificExtensions
  case class RespondsTo(id: AtomId, in: AtomId, out: AtomId) extends RespondsToPlatformSpecificExtensions
}
