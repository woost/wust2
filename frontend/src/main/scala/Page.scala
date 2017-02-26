package frontend

import graph._

// views immutable, dass urls nicht kaputt gehen
// wust.space/view/$viewitd

trait Selector {
  import Selector._
  def intersect(that: Selector): Selector = new Intersect(this, that)
  def union(that: Selector): Selector = new Union(this, that)
  def apply(id: AtomId): Boolean
}
object Selector {
  // case class TitleMatch(regex: String) extends Selector
  case object Nothing extends Selector {
    override def apply(id: AtomId) = false
  }
  case object All extends Selector {
    override def apply(id: AtomId) = true
  }
  case class IdSet(set: Set[AtomId]) extends Selector {
    override def apply(id: AtomId) = set(id)
  }
  case class Union(a: Selector, b: Selector) extends Selector {
    def apply(id: AtomId) = a(id) || b(id)
  }
  case class Intersect(a: Selector, b: Selector) extends Selector {
    def apply(id: AtomId) = a(id) && b(id)
  }
}

case class View(
    collapsed: Selector = Selector.Nothing) {
  def intersect(that: View) = copy(collapsed = this.collapsed intersect that.collapsed)
  def union(that: View) = copy(collapsed = this.collapsed union that.collapsed)
}

object View {
  def collapse(selector: Selector, graph: Graph): Graph = {
    val toCollapse = graph.posts.keys.filter(selector.apply)
      .filterNot(id => graph.involvedInCycle(id) && graph.transitiveParents(id).map(_.id).exists(selector.apply))

    val removePosts = toCollapse
      .map { collapsedId =>
        collapsedId -> graph.transitiveChildren(collapsedId).map(_.id)
      }.toMap

    val removeEdges = removePosts.values.flatten
      .map { p =>
        p -> graph.incidentConnections(p)
      }.toMap

    val addEdges = removePosts
      .flatMap {
        case (parent, children) =>
          children.flatMap { child =>
            removeEdges(child).map(graph.connections).map {
              case edge @ Connects(_, `child`, _) => edge.copy(sourceId = parent)
              case edge @ Connects(_, _, `child`) => edge.copy(targetId = parent)
            }
          }
      }

    // TODO exclude overlappi
    graph
      .removeConnections(removeEdges.values.flatten)
      .++(addEdges)
      .removePosts(removePosts.values.flatten)
  }

  def apply(view: View, graph: Graph): Graph = {
    val collapsed = collapse(view.collapsed, graph)
    collapsed
  }
}
