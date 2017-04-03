package wust.frontend

import collection.breakOut

import wust.graph._
import wust.util._
import wust.util.collection._

object Collapse {
  def apply(collapsing: Selector)(displayGraph: DisplayGraph): DisplayGraph = {
    import displayGraph.graph
    val collapsingPosts: Iterable[PostId] = graph.postsById.keys.filter(collapsing)

    val hiddenPosts: Set[PostId] = collapsingPosts.flatMap { collapsedId =>
      graph.transitiveChildren(collapsedId)
        .filterNot { child =>
          involvedInCycleWithCollapsedPost(graph, child, collapsing) ||
            hasUncollapsedParent(graph, child, collapsing) // only hide post if all parents are collapsing
        }
    }(breakOut)

    val hiddenContainments: Set[ContainsId] = (collapsingPosts.flatMap(graph.incidentChildContains)(breakOut): Set[ContainsId]) ++
      (hiddenPosts.flatMap(graph.incidentContains)(breakOut): Set[ContainsId])

    val alternativePosts: Map[PostId, Set[PostId]] = {
      hiddenContainments
        .map(graph.containmentsById)
        .groupBy(_.childId)
        .mapValues(_.flatMap { c =>
          if (hiddenPosts(c.parentId)) highestCollapsedParent(graph, c.parentId, collapsing)
          else Some(c.parentId)
        }(breakOut): Set[PostId])
        .withDefault(post => Set(post))
    }

    val redirectedConnections: Set[LocalConnection] = (alternativePosts.keys.flatMap { post =>
      graph.incidentConnections(post).flatMap { cid =>
        val c = graph.connectionsById(cid)
        //TODO: assert(c.targetId is PostId) => this will be different for hyperedges
        for (altSource <- alternativePosts(c.sourceId); altTarget <- alternativePosts(PostId(c.targetId.id))) yield {
          LocalConnection(sourceId = altSource, targetId = altTarget)
        }
      }
    }(breakOut): Set[LocalConnection])
      .filterNot(c => graph.successors(c.sourceId) contains c.targetId) // drop already existing connections

    displayGraph.copy(
      graph = graph -- hiddenPosts -- hiddenContainments,
      redirectedConnections = redirectedConnections
    )
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
