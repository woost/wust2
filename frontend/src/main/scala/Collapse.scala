package wust.frontend

import collection.breakOut

import wust.graph._
import wust.util._
import wust.util.collection._

object Collapse {
  def apply(collapsing: Selector)(displayGraph: DisplayGraph): DisplayGraph = {
    import displayGraph.graph
    val collapsingPosts: Iterable[PostId] = graph.postsById.keys.filter(collapsing)
    // println("collapsingPosts: " + collapsingPosts)

    val hiddenPosts: Set[PostId] = collapsingPosts.flatMap { collapsedId =>
      graph.transitiveChildren(collapsedId)
        .filterNot { child =>
          involvedInCycleWithCollapsedPost(graph, child, collapsing) ||
            hasUncollapsedParent(graph, child, collapsing) // only hide post if all parents are collapsing
        }
    }(breakOut)
    // println("hiddenPosts: " + hiddenPosts)

    val alternativePosts: Map[PostId, Set[PostId]] = {
      hiddenPosts.flatMap(graph.incidentContains)
        .map(graph.containmentsById)
        .groupBy(_.childId)
        .mapValues(_.flatMap { c =>
          // println(s"  highestHidden(${c.parentId}): " + highestParent(graph, c.parentId, hiddenPosts))
          if (hiddenPosts(c.childId)) {
            if (hiddenPosts(c.parentId)) highestParent(graph, c.parentId, collapsing)
            else Some(c.parentId)
          } else None
        }(breakOut): Set[PostId])
        .withDefault(post => Set(post))
    }
    // println("alternativePosts: " + alternativePosts)

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
    // println("redirectedConnections: " + redirectedConnections)

    val hiddenContainments: Set[ContainsId] = collapsingPosts.flatMap(graph.incidentChildContains)(breakOut)
    // println("hiddenContainments: " + hiddenContainments.map(graph.containmentsById))

    val collapsedLocalContainments = hiddenContainments.flatMap { cid =>
      val c = graph.containmentsById(cid)
      val nonCollapsedIntersection = graph.transitiveChildren(c.parentId).filter(hasUncollapsedParent(graph, _, collapsing))
      if(nonCollapsedIntersection.nonEmpty) nonCollapsedIntersection.map(LocalContainment(c.parentId, _))
      else List(LocalContainment(c.parentId, c.childId))
    }.filterNot(c => c.parentId == c.childId || hiddenPosts(c.parentId) || hiddenPosts(c.childId))
    // println("collapsedLocalContainments: " + collapsedLocalContainments)

    displayGraph.copy(
      graph = graph -- hiddenPosts -- hiddenContainments,
      redirectedConnections = redirectedConnections,
      collapsedContainments = collapsedLocalContainments
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

  def highestParent(graph: Graph, parent: PostId, predicate: PostId => Boolean): Option[PostId] = {
    (parent :: graph.transitiveParents(parent).toList).filter(predicate).lastOption
  }
}
