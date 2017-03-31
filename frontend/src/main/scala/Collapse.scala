package wust.frontend

import collection.breakOut

import wust.graph._
import wust.util._
import wust.util.collection._

object Collapse {
  def apply(collapsing: Selector, graph: Graph): Graph = {
    val collapsingPosts: Iterable[PostId] = graph.postsById.keys.filter(collapsing)

    val hiddenPosts: Set[PostId] = collapsingPosts.flatMap { collapsedId =>
      graph.transitiveChildren(collapsedId)
        .filterNot { child =>
          involvedInCycleWithCollapsedPost(graph, child, collapsing) ||
            hasUncollapsedParent(graph, child, collapsing) // only collapsing post if all parents are collapsing
        }
    }(breakOut)

    val alternativePosts: Map[PostId, Seq[PostId]] = (hiddenPosts.map { post =>
      post -> graph.parents(post).flatMap { parent =>
        if (hiddenPosts(parent))
          highestCollapsedParent(graph, parent, collapsing)
        else
          Some(parent) // parent is probably involved in cycle and therefore not hidden
      }(breakOut).distinct
    }(breakOut): Map[PostId, Seq[PostId]]).withDefault(post => Seq(post))

    val nextId = AutoId(start = -1, delta = -1)
    val redirectedEdges: Seq[Connects] = (hiddenPosts.flatMap { post =>
      graph.incidentConnections(post).flatMap { cid =>
        val c = graph.connectionsById(cid)
        //TODO: assert(c.targetId is PostId) => this will be different for hyperedges
        for (altSource <- alternativePosts(c.sourceId); altTarget <- alternativePosts(PostId(c.targetId.id))) yield {
          c.copy(sourceId = altSource, targetId = altTarget)
        }
      }
    }(breakOut): Seq[Connects])
      .distinctBy(c => (c.sourceId, c.targetId)) // edge bundling
      .map(_.copy(id = nextId()))

    graph -- hiddenPosts ++ redirectedEdges
  }

  def involvedInCycleWithCollapsedPost(graph: Graph, child: PostId, collapsing: PostId => Boolean): Boolean = {
    (graph.involvedInContainmentCycle(child) && graph.transitiveChildren(child).exists(collapsing))
  }

  def hasUncollapsedParent(graph: Graph, child: PostId, collapsing: PostId => Boolean): Boolean = {
    graph.parents(child).exists { parent =>
      val transitiveParents = parent :: graph.transitiveParents(parent).toList
      transitiveParents.nonEmpty && transitiveParents.forall(!collapsing(_))
    }
  }

  def highestCollapsedParent(graph: Graph, parent: PostId, collapsing: PostId => Boolean): Option[PostId] = {
    (parent :: graph.transitiveParents(parent).toList).filter(collapsing).lastOption
  }

}
