package wust.graph
import wust.ids._

sealed trait GraphSelection {
  def add(parentId: PostId): GraphSelection
  def remove(parentId: PostId): GraphSelection
}

object GraphSelection {
  case object Root extends GraphSelection {
    def add(parentId: PostId) = Union(Set(parentId))
    def remove(parentId: PostId) = Root
  }
  case class Union(parentIds: Set[PostId]) extends GraphSelection {
    def add(parentId: PostId) = copy(parentIds + parentId)
    def remove(parentId: PostId) = {
      val newParentIds = parentIds - parentId
      if (newParentIds.nonEmpty) copy(newParentIds)
      else Root
    }
  }

  def default = Root
}

trait Selector extends (PostId => Boolean) {
  import Selector._
  def intersect(that: Selector): Selector = Intersect(this, that)
  def union(that: Selector): Selector = Union(this, that)
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
