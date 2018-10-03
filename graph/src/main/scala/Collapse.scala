package wust.graph

import wust.graph.CollapsedGraph.LocalConnection
import wust.ids._
import wust.util.Selector
import wust.util.algorithm._

import scala.collection.breakOut

object Collapse {
  def apply(collapsing: Selector[NodeId])(displayGraph: CollapsedGraph): CollapsedGraph = {
    import displayGraph.graph

    val collapsingPosts: collection.Set[NodeId] = graph.nodeIds.filter(collapsing).toSet
    val hiddenPosts: collection.Set[NodeId] = getHiddenNodes(graph, collapsingPosts)
    val alternativePosts: collection.Map[NodeId, Set[NodeId]] =
      getAlternativePosts(graph, hiddenPosts, collapsingPosts)
    val redirectedConnections: Set[LocalConnection] =
      getRedirectedConnections(graph, alternativePosts)
    val hiddenContainments: collection.Set[Edge] =
      collapsingPosts.flatMap(graph.incidentChildContainments) //(breakOut)
    val collapsedLocalContainments: collection.Set[LocalConnection] =
      getLocalContainments(graph, hiddenPosts, hiddenContainments, collapsingPosts)

    // println("collapsingPosts: " + collapsingPosts)
    // println("hiddenPosts: " + hiddenPosts)
    // println("alternativePosts: " + alternativePosts)
    // println("redirectedConnections: " + redirectedConnections)
    // println("hiddenContainments: " + hiddenContainments.map(graph.containments))
    // println("collapsedLocalContainments: " + collapsedLocalContainments)

    displayGraph.copy(
      graph = graph removeNodes hiddenPosts removeConnections hiddenContainments,
      redirectedConnections = redirectedConnections,
      collapsedContainments = collapsedLocalContainments
    )
  }

  def getHiddenNodes(
      graph: Graph,
      collapsingPosts: collection.Set[NodeId]
  ): collection.Set[NodeId] = {
    val candidates = collapsingPosts.flatMap(graph.descendants)
    candidates
      .filterNot { child =>
        involvedInCycleWithCollapsedPost(graph, child, collapsingPosts) ||
        hasOneUncollapsedTransitiveParent(graph, child, collapsingPosts) // only hide post if all parents are collapsing
      }
  }

  def getAlternativePosts(
      graph: Graph,
      hiddenPosts: collection.Set[NodeId],
      collapsingPosts: collection.Set[NodeId]
  ): Map[NodeId, Set[NodeId]] = {
    hiddenPosts
      .flatMap(graph.incidentParentContainments)
      .groupBy(_.sourceId)
      .mapValues(_.flatMap { c =>
        if (hiddenPosts(c.targetId))
          highestParents(graph, c.sourceId, collapsingPosts)
        else Option(c.targetId)
      }(breakOut): Set[NodeId])
      .withDefault(post => Set(post))
  }

  def getRedirectedConnections(
      graph: Graph,
      alternativePosts: collection.Map[NodeId, Set[NodeId]]
  ): Set[LocalConnection] = {
    (alternativePosts.keys.flatMap { post =>
      graph.lookup.incidentContainments(post).flatMap { c =>
        //TODO: assert(c.targetId is NodeId) => this will be different for hyperedges
        for (altSource <- alternativePosts(c.sourceId); altTarget <- alternativePosts(c.targetId))
          yield {
            LocalConnection(
              sourceId = altSource,
              EdgeData.Label("redirected"),
              targetId = altTarget
            )
          }
      }
    }(breakOut): Set[LocalConnection])
      .filterNot(c => graph.successorsWithoutParent(c.sourceId) contains c.targetId) // drop already existing connections
  }

  def getLocalContainments(
      graph: Graph,
      hiddenPosts: collection.Set[NodeId],
      hiddenContainments: collection.Set[Edge],
      collapsingPosts: collection.Set[NodeId]
  ): collection.Set[LocalConnection] = {
    collapsingPosts.flatMap { parentId =>
      // children remain visible when:
      // - also contained in other uncollapsed post
      // - involved in containment cycle
      val visibleChildren = graph.descendants(parentId).filterNot { child =>
        hiddenPosts(child) ||
        (!(graph.children(parentId) contains child) && graph.involvedInContainmentCycle(child))
      }
      visibleChildren.map(LocalConnection(_, EdgeData.Parent, parentId))
    }
  }

  def involvedInCycleWithCollapsedPost(
      graph: Graph,
      child: NodeId,
      collapsing: NodeId => Boolean
  ): Boolean = {
    graph.involvedInContainmentCycle(child) && graph.descendants(child).exists(collapsing)
  }

  def hasOneUncollapsedTransitiveParent(
      graph: Graph,
      child: NodeId,
      collapsing: collection.Set[NodeId]
  ): Boolean = {
    graph
      .ancestors(child)
      .exists(
        parent =>
          graph.parents(parent).isEmpty && !collapsing(parent) && reachableByUncollapsedPath(
            child,
            parent,
            graph,
            collapsing
          )
      )
  }

  def reachableByUncollapsedPath(
      sourceId: NodeId,
      targetId: NodeId,
      graph: Graph,
      collapsing: collection.Set[NodeId]
  ): Boolean = {
    val space = graph removeNodes (collapsing - sourceId)
    depthFirstSearchWithStartInCycleDetection[NodeId](sourceId, space.parents).iterator contains targetId
  }

  def highestParents(graph: Graph, child: NodeId, predicate: NodeId => Boolean): Set[NodeId] = {
    graph
      .ancestors(child)
      .filter(parent => predicate(parent) && graph.parents(parent).forall(!predicate(_)))
      .toSet
  }
}
