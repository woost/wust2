package wust.frontend

import collection.breakOut

import wust.graph._

// views immutable, dass urls nicht kaputt gehen
// wust.space/view/$viewitd

trait Selector {
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

case class View(
  collapsed: Selector = Selector.Nothing
) {
  def intersect(that: View) = copy(collapsed = this.collapsed intersect that.collapsed)
  def union(that: View) = copy(collapsed = this.collapsed union that.collapsed)
}

object View {
  def collapse(selector: Selector, graph: Graph): Graph = {
    //TODO: currently only top-level-parents can be collapsed
    val toCollapse: Iterable[PostId] = graph.posts.keys.filter(selector.apply)
      //TODO: only the cycle should not be collapsed, but we should still collapse other children (not in the cycle)
      .filterNot(id => graph.involvedInCycle(id) && graph.transitiveParents(id).exists(selector.apply))

    val collapseChildren: Map[PostId, Iterable[PostId]] = toCollapse
      .map { collapsedId =>
        collapsedId -> graph.transitiveChildren(collapsedId)
      }(breakOut)

    val removableChildren: Set[PostId] = collapseChildren.values.flatten
      .filter(id => graph.transitiveParents(id).toList.diff(collapseChildren.flatMap { case (k, v) => k :: v.toList }.toList).isEmpty)
      .toSet

    val removePosts: Map[PostId, Iterable[PostId]] = collapseChildren.mapValues(_.filter(removableChildren))

    val removeEdges: Map[PostId, Set[ConnectsId]] = removePosts.values.flatten // TODO: use adjacency lists of graph directly
      .map { p =>
        p -> graph.incidentConnections(p)
      }(breakOut)

    def edgesToParents(addEdges: Map[ConnectsId, Connects], parent: PostId, child: PostId): Map[ConnectsId, Connects] = {
      val connectionMap = graph.connections ++ addEdges
      addEdges ++ removeEdges(child).map(connectionMap).map {
        case edge @ Connects(id, `child`, _) => id -> edge.copy(sourceId = parent)
        case edge @ Connects(id, _, `child`) => id -> edge.copy(targetId = parent)
      }
    }

    val addEdges = removePosts.toList.flatMap { case (parent, children) => children.map(child => parent -> child) }
      .foldLeft(Map.empty[ConnectsId, Connects])((edges, pc) => edgesToParents(edges, pc._1, pc._2))

    graph
      .--(removeEdges.values.flatten)
      .++(addEdges.values)
      .--(removePosts.values.flatten)
  }

  def apply(view: View, graph: Graph): Graph = {
    val collapsed = collapse(view.collapsed, graph)
    collapsed
  }
}
