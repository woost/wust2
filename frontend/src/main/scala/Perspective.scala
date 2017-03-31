package wust.frontend

import collection.breakOut

import wust.graph._
import wust.util._
import wust.util.collection._

// views immutable, dass urls nicht kaputt gehen
// wust.space/view/$viewitd

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
case class DisplayGraph(graph: Graph, localConnections: List[LocalConnection] = Nil)

case class Perspective(
  collapsed: Selector = Selector.Nothing
) {
  def intersect(that: Perspective) = copy(collapsed = this.collapsed intersect that.collapsed)
  def union(that: Perspective) = copy(collapsed = this.collapsed union that.collapsed)
}

object Perspective {
  def apply(view: Perspective, graph: Graph): DisplayGraph = {
    DisplayGraph(graph) |> Collapse(view.collapsed)
  }
}
