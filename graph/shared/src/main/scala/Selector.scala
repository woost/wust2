package wust.graph

trait Selector extends (PostId => Boolean) {
  import Selector._
  def intersect(that: Selector): Selector = new Intersect(this, that)
  def union(that: Selector): Selector = new Union(this, that)
  def apply(id: PostId): Boolean
}

object Selector {
  // case class TitleMatch(regex: String) extends Selector
  case object Nothing extends Selector {
    override def apply(id: PostId) = false
  }
  case object All extends Selector {
    override def apply(id: PostId) = true
  }
  case class IdSet(set: Set[PostId]) extends Selector {
    override def apply(id: PostId) = set(id)
  }
  case class Union(a: Selector, b: Selector) extends Selector {
    def apply(id: PostId) = a(id) || b(id)
  }
  case class Intersect(a: Selector, b: Selector) extends Selector {
    def apply(id: PostId) = a(id) && b(id)
  }
}

case class LocalConnection(sourceId: PostId, targetId: PostId)
case class LocalContainment(parentId: PostId, childId: PostId)
case class DisplayGraph(
  graph: Graph,
  redirectedConnections: Set[LocalConnection] = Set.empty,
  collapsedContainments: Set[LocalContainment] = Set.empty
)
