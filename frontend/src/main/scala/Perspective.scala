package wust.frontend

import collection.breakOut

import wust.graph._
import wust.util._
import wust.util.collection._

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

case class Perspective(
  collapsed: Selector = Selector.Nothing
) {
  def intersect(that: Perspective) = copy(collapsed = this.collapsed intersect that.collapsed)
  def union(that: Perspective) = copy(collapsed = this.collapsed union that.collapsed)
}

object Perspective {
  def collapse(selector: Selector, graph: Graph): Graph = {
    val nextId = AutoId(start = -1, delta = -1)

    val toCollapse: Iterable[PostId] = graph.postsById.keys.filter(selector.apply)

    val hiddenPosts: Set[PostId] = toCollapse.flatMap { collapsedId =>
      graph.transitiveChildren(collapsedId)
        .filterNot { child =>
          involvedInCycleWithCollapsedPost(graph, child, selector.apply) ||
            hasNotCollapsedParents(graph, child, selector.apply)
        }
    }(breakOut)

    val alternativePosts: Map[PostId, Seq[PostId]] = (hiddenPosts.map { post =>
      post -> graph.parents(post).flatMap { parent =>
        if (hiddenPosts(parent))
          highestCollapsedParent(graph, parent, selector.apply)
        else
          Some(parent) // parent is probably involven in cycle and therefore not hidden
      }(breakOut).distinct
    }(breakOut): Map[PostId, Seq[PostId]]).withDefault(post => Seq(post))

    val redirectedEdges: Seq[Connects] = (hiddenPosts.flatMap { post =>
      graph.incidentConnections(post).flatMap { cid =>
        val c = graph.connectionsById(cid)
        //TODO: assert(c.targetId is PostId) => this will be different for hyperedges
        for (altSource <- alternativePosts(c.sourceId); altTarget <- alternativePosts(PostId(c.targetId.id))) yield {
          c.copy(sourceId = altSource, targetId = altTarget)
        }
      }
    }(breakOut): Seq[Connects])
      .distinctBy(c => (c.sourceId, c.targetId))
      .map(_.copy(id = nextId()))

    graph -- hiddenPosts ++ redirectedEdges
  }

  def involvedInCycleWithCollapsedPost(graph: Graph, child: PostId, collapsed: PostId => Boolean): Boolean = (graph.involvedInContainmentCycle(child) && graph.transitiveChildren(child).exists(collapsed))

  def hasNotCollapsedParents(graph: Graph, child: PostId, collapsed: PostId => Boolean): Boolean = {
    graph.parents(child).exists { parent =>
      val transitiveParents = parent :: graph.transitiveParents(parent).toList
      transitiveParents.nonEmpty && transitiveParents.forall(!collapsed(_))
    }
  }

  def highestCollapsedParent(graph: Graph, parent: PostId, collapsed: PostId => Boolean): Option[PostId] = {
    (parent :: graph.transitiveParents(parent).toList).filter(collapsed).lastOption
  }

  def apply(view: Perspective, graph: Graph): Graph = {
    val collapsed = collapse(view.collapsed, graph)
    collapsed
  }
}
