package wust.frontend

import collection.breakOut

import wust.graph._
import wust.util._
import wust.util.collection._
import wust.util.algorithm._

object Collapse {
  def apply(collapsing: Selector)(displayGraph: DisplayGraph): DisplayGraph = {
    import displayGraph.graph
    val collapsingPosts: Set[PostId] = graph.postsById.keySet.filter(collapsing)
    // println("collapsingPosts: " + collapsingPosts)

    val hiddenPosts = getHiddenPosts(graph, collapsingPosts)
    println("hiddenPosts: " + hiddenPosts)

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
    println("alternativePosts: " + alternativePosts)

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
      val nonCollapsedIntersection = graph.transitiveChildren(c.parentId).filterNot(hiddenPosts)
      if (nonCollapsedIntersection.nonEmpty) nonCollapsedIntersection.map(LocalContainment(c.parentId, _))
      else List(LocalContainment(c.parentId, c.childId))
    }.filterNot(c => c.parentId == c.childId || hiddenPosts(c.parentId) || hiddenPosts(c.childId))
    // println("collapsedLocalContainments: " + collapsedLocalContainments)

    displayGraph.copy(
      graph = graph -- hiddenPosts -- hiddenContainments,
      redirectedConnections = redirectedConnections,
      collapsedContainments = collapsedLocalContainments
    )
  }

  def getHiddenPosts(graph: Graph, collapsingPosts: Set[PostId]): Set[PostId] = {
    val candidates = collapsingPosts.flatMap(graph.transitiveChildren)
    candidates
      .filterNot { child =>
        involvedInCycleWithCollapsedPost(graph, child, collapsingPosts) ||
          hasOneUncollapsedTransitiveParent(graph, child, collapsingPosts) // only hide post if all parents are collapsing
      }
  }

  def involvedInCycleWithCollapsedPost(graph: Graph, child: PostId, collapsing: PostId => Boolean): Boolean = {
    (graph.involvedInContainmentCycle(child) && graph.transitiveChildren(child).exists(collapsing))
  }

  // def hasOnlyUncollapsedTransitiveParents(graph: Graph, child: PostId, collapsing: PostId => Boolean): Boolean = {
  //   graph.parents(child).exists { parent =>
  //     val transitiveParents = parent :: graph.transitiveParents(parent).toList
  //     transitiveParents.nonEmpty && transitiveParents.forall(!collapsing(_))
  //   }
  // }

  def hasOneUncollapsedTransitiveParent(graph: Graph, child: PostId, collapsing: Set[PostId]): Boolean = {
    graph.transitiveParents(child).exists(parent => graph.parents(parent).isEmpty && !collapsing(parent) && reachableByUncollapsedPath(child, parent, graph, collapsing))
  }

  def reachableByUncollapsedPath(child: PostId, parent: PostId, graph: Graph, collapsing: Set[PostId]): Boolean = {
    val space = graph -- (collapsing - child)
    depthFirstSearch(child, space.parents).iterator contains parent
  }

  def highestParent(graph: Graph, parent: PostId, predicate: PostId => Boolean): Option[PostId] = {
    (parent :: graph.transitiveParents(parent).toList).filter(predicate).lastOption
  }
}
