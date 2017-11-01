package wust.graph

import wust.ids._
import wust.util.algorithm._

import scala.collection.breakOut

object Collapse {
  def apply(collapsing: Selector)(displayGraph: DisplayGraph): DisplayGraph = {
    import displayGraph.graph

    val collapsingPosts: Set[PostId] = graph.postsById.keySet.filter(collapsing)
    val hiddenPosts: Set[PostId] = getHiddenPosts(graph, collapsingPosts)
    val alternativePosts: Map[PostId, Set[PostId]] = getAlternativePosts(graph, hiddenPosts, collapsingPosts)
    val redirectedConnections: Set[LocalConnection] = getRedirectedConnections(graph, alternativePosts)
    val hiddenContainments: Set[Containment] = collapsingPosts.flatMap(graph.incidentChildContainments) //(breakOut)
    val collapsedLocalContainments: Set[LocalContainment] = getLocalContainments(graph, hiddenPosts, hiddenContainments, collapsingPosts)

    // println("collapsingPosts: " + collapsingPosts)
    // println("hiddenPosts: " + hiddenPosts)
    // println("alternativePosts: " + alternativePosts)
    // println("redirectedConnections: " + redirectedConnections)
    // println("hiddenContainments: " + hiddenContainments.map(graph.containments))
    // println("collapsedLocalContainments: " + collapsedLocalContainments)

    displayGraph.copy(
      graph = graph removePosts hiddenPosts removeContainments hiddenContainments,
      redirectedConnections = redirectedConnections,
      collapsedContainments = collapsedLocalContainments
    )
  }

  def getHiddenPosts(graph: Graph, collapsingPosts: Set[PostId]): Set[PostId] = {
    val candidates = collapsingPosts.flatMap(graph.descendants)
    candidates
      .filterNot { child =>
        involvedInCycleWithCollapsedPost(graph, child, collapsingPosts) ||
          hasOneUncollapsedTransitiveParent(graph, child, collapsingPosts) // only hide post if all parents are collapsing
      }
  }

  def getAlternativePosts(graph: Graph, hiddenPosts: Set[PostId], collapsingPosts: Set[PostId]): Map[PostId, Set[PostId]] = {
    hiddenPosts.flatMap(graph.incidentParentContainments)
      .groupBy(_.childId)
      .mapValues(_.flatMap { c =>
        if (hiddenPosts(c.parentId))
          highestParents(graph, c.childId, collapsingPosts)
        else Option(c.parentId)
      }(breakOut): Set[PostId])
      .withDefault(post => Set(post))
  }

  def getRedirectedConnections(graph: Graph, alternativePosts: Map[PostId, Set[PostId]]): Set[LocalConnection] = {
    (alternativePosts.keys.flatMap { post =>
      graph.incidentConnections(post).flatMap { c =>
        //TODO: assert(c.targetId is PostId) => this will be different for hyperedges
        for (altSource <- alternativePosts(c.sourceId); altTarget <- alternativePosts(c.targetId)) yield {
          LocalConnection(sourceId = altSource, targetId = altTarget)
        }
      }
    }(breakOut): Set[LocalConnection])
      .filterNot(c => graph.successors(c.sourceId) contains c.targetId) // drop already existing connections
  }

  def getLocalContainments(graph: Graph, hiddenPosts: Set[PostId], hiddenContainments: Set[Containment], collapsingPosts: Set[PostId]): Set[LocalContainment] = {
    collapsingPosts.flatMap { parent =>
      // children remain visible when:
      // - also contained in other uncollapsed post
      // - involved in containment cycle
      val visibleChildren = graph.descendants(parent).filterNot { child =>
        hiddenPosts(child) ||
          (!(graph.children(parent) contains child) && graph.involvedInContainmentCycle(child))
      }
      visibleChildren.map(LocalContainment(parent, _))
    }
  }

  def involvedInCycleWithCollapsedPost(graph: Graph, child: PostId, collapsing: PostId => Boolean): Boolean = {
    graph.involvedInContainmentCycle(child) && graph.descendants(child).exists(collapsing)
  }

  def hasOneUncollapsedTransitiveParent(graph: Graph, child: PostId, collapsing: Set[PostId]): Boolean = {
    graph.ancestors(child).exists(parent => graph.parents(parent).isEmpty && !collapsing(parent) && reachableByUncollapsedPath(child, parent, graph, collapsing))
  }

  def reachableByUncollapsedPath(childId: PostId, parentId: PostId, graph: Graph, collapsing: Set[PostId]): Boolean = {
    val space = graph removePosts (collapsing - childId)
    depthFirstSearch(childId, space.parents).iterator contains parentId
  }

  def highestParents(graph: Graph, child: PostId, predicate: PostId => Boolean): Set[PostId] = {
    graph.ancestors(child).filter(parent => predicate(parent) && graph.parents(parent).forall(!predicate(_))).toSet
  }
}
