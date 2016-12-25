package object graph {
  //TODO: different types of ids to restrict RespondsTo in/out
  type AtomId = Long

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
