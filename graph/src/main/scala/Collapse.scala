package wust.graph

import wust.ids._
import wust.util.Selector
import wust.util.algorithm._

import scala.collection.breakOut

object Collapse {
  def apply(collapsing: Selector[NodeId])(displayGraph: DisplayGraph): DisplayGraph = {
    import displayGraph.graph

    val collapsingPosts: Set[NodeId] = graph.postsById.keySet.filter(collapsing)
    val hiddenPosts: Set[NodeId] = getHiddenPosts(graph, collapsingPosts)
    val alternativePosts: Map[NodeId, Set[NodeId]] = getAlternativePosts(graph, hiddenPosts, collapsingPosts)
    val redirectedConnections: Set[LocalConnection] = getRedirectedConnections(graph, alternativePosts)
    val hiddenContainments: Set[Edge] = collapsingPosts.flatMap(graph.incidentChildContainments) //(breakOut)
    val collapsedLocalContainments: Set[LocalConnection] = getLocalContainments(graph, hiddenPosts, hiddenContainments, collapsingPosts)

    // println("collapsingPosts: " + collapsingPosts)
    // println("hiddenPosts: " + hiddenPosts)
    // println("alternativePosts: " + alternativePosts)
    // println("redirectedConnections: " + redirectedConnections)
    // println("hiddenContainments: " + hiddenContainments.map(graph.containments))
    // println("collapsedLocalContainments: " + collapsedLocalContainments)

    displayGraph.copy(
      graph = graph removePosts hiddenPosts removeConnections hiddenContainments,
      redirectedConnections = redirectedConnections,
      collapsedContainments = collapsedLocalContainments
    )
  }

  def getHiddenPosts(graph: Graph, collapsingPosts: Set[NodeId]): Set[NodeId] = {
    val candidates = collapsingPosts.flatMap(graph.descendants)
    candidates
      .filterNot { child =>
        involvedInCycleWithCollapsedPost(graph, child, collapsingPosts) ||
          hasOneUncollapsedTransitiveParent(graph, child, collapsingPosts) // only hide post if all parents are collapsing
      }
  }

  def getAlternativePosts(graph: Graph, hiddenPosts: Set[NodeId], collapsingPosts: Set[NodeId]): Map[NodeId, Set[NodeId]] = {
    hiddenPosts.flatMap(graph.incidentParentContainments)
      .groupBy(_.sourceId)
      .mapValues(_.flatMap { c =>
        if (hiddenPosts(c.targetId))
          highestParents(graph, c.sourceId, collapsingPosts)
        else Option(c.targetId)
      }(breakOut): Set[NodeId])
      .withDefault(post => Set(post))
  }

  def getRedirectedConnections(graph: Graph, alternativePosts: Map[NodeId, Set[NodeId]]): Set[LocalConnection] = {
    (alternativePosts.keys.flatMap { post =>
      graph.incidentConnections(post).flatMap { c =>
        //TODO: assert(c.targetId is NodeId) => this will be different for hyperedges
        for (altSource <- alternativePosts(c.sourceId); altTarget <- alternativePosts(c.targetId)) yield {
          LocalConnection(sourceId = altSource, EdgeData.Label("redirected"), targetId = altTarget)
        }
      }
    }(breakOut): Set[LocalConnection])
      .filterNot(c => graph.successorsWithoutParent(c.sourceId) contains c.targetId) // drop already existing connections
  }

  def getLocalContainments(graph: Graph, hiddenPosts: Set[NodeId], hiddenContainments: Set[Edge], collapsingPosts: Set[NodeId]): Set[LocalConnection] = {
    collapsingPosts.flatMap { parent =>
      // children remain visible when:
      // - also contained in other uncollapsed post
      // - involved in containment cycle
      val visibleChildren = graph.descendants(parent).filterNot { child =>
        hiddenPosts(child) ||
          (!(graph.children(parent) contains child) && graph.involvedInContainmentCycle(child))
      }
      visibleChildren.map(LocalConnection(_, EdgeData.Parent, parent))
    }
  }

  def involvedInCycleWithCollapsedPost(graph: Graph, child: NodeId, collapsing: NodeId => Boolean): Boolean = {
    graph.involvedInContainmentCycle(child) && graph.descendants(child).exists(collapsing)
  }

  def hasOneUncollapsedTransitiveParent(graph: Graph, child: NodeId, collapsing: Set[NodeId]): Boolean = {
    graph.ancestors(child).exists(parent => graph.parents(parent).isEmpty && !collapsing(parent) && reachableByUncollapsedPath(child, parent, graph, collapsing))
  }

  def reachableByUncollapsedPath(sourceId: NodeId, targetId: NodeId, graph: Graph, collapsing: Set[NodeId]): Boolean = {
    val space = graph removePosts (collapsing - sourceId)
    depthFirstSearch(sourceId, space.parents).iterator contains targetId
  }

  def highestParents(graph: Graph, child: NodeId, predicate: NodeId => Boolean): Set[NodeId] = {
    graph.ancestors(child).filter(parent => predicate(parent) && graph.parents(parent).forall(!predicate(_))).toSet
  }
}
