package wust.graph
import derive.derive
import wust.ids._

sealed trait Page {
  def add(parentId: PostId): Page
  def remove(parentId: PostId): Page
  def parentIds: Set[PostId]
}

object Page {
  case object Root extends Page {
    def add(parentId: PostId) = Union(Set(parentId))
    def remove(parentId: PostId) = Root
    def parentIds = Set.empty
  }
  case class Union(parentIds: Set[PostId]) extends Page {
    def add(parentId: PostId) = copy(parentIds + parentId)
    def remove(parentId: PostId) = {
      val newParentIds = parentIds - parentId
      if (newParentIds.nonEmpty) copy(newParentIds)
      else Root
    }
  }

  def default = Root

  def toParentConnections(page: Page, postId: PostId): Seq[Connection] = {
    page match {
      case Page.Union(parentIds) => parentIds.toSeq.map(Connection(postId, Label.parent, _))
      case _                               => Seq.empty
    }
  }
}

//TODO: this is a general mathematical set. put it in util.
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
  case class IdSet(set: PostId => Boolean) extends Selector {
    override def apply(id: PostId) = set(id)
  }
  case class Union(a: Selector, b: Selector) extends Selector {
    def apply(id: PostId) = a(id) || b(id)
  }
  case class Intersect(a: Selector, b: Selector) extends Selector {
    def apply(id: PostId) = a(id) && b(id)
  }
}
