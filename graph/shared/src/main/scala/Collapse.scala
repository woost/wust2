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
    val hiddenContainments: Set[ContainsId] = collapsingPosts.flatMap(graph.incidentChildContains)(breakOut)
    val collapsedLocalContainments: Set[LocalContainment] = getLocalContainments(graph, hiddenPosts, hiddenContainments, collapsingPosts)

    // println("collapsingPosts: " + collapsingPosts)
    // println("hiddenPosts: " + hiddenPosts)
    // println("alternativePosts: " + alternativePosts)
    // println("redirectedConnections: " + redirectedConnections)
    // println("hiddenContainments: " + hiddenContainments.map(graph.containmentsById))
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

  def getAlternativePosts(graph: Graph, hiddenPosts: Set[PostId], collapsingPosts: Set[PostId]): Map[PostId, Set[PostId]] = {
    hiddenPosts.flatMap(graph.incidentParentContains)
      .map(graph.containmentsById)
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
      graph.incidentConnections(post).flatMap { cid =>
        val c = graph.connectionsById(cid)
        //TODO: assert(c.targetId is PostId) => this will be different for hyperedges
        for (altSource <- alternativePosts(c.sourceId); altTarget <- alternativePosts(PostId(c.targetId.id))) yield {
          LocalConnection(sourceId = altSource, targetId = altTarget)
        }
      }
    }(breakOut): Set[LocalConnection])
      .filterNot(c => graph.successors(c.sourceId) contains c.targetId) // drop already existing connections
  }

  def getLocalContainments(graph: Graph, hiddenPosts: Set[PostId], hiddenContainments: Set[ContainsId], collapsingPosts: Set[PostId]): Set[LocalContainment] = {
    collapsingPosts.flatMap { parent =>
      // children remain visible when:
      // - also contained in other uncollapsed post
      // - involved in containment cycle
      val visibleChildren = graph.transitiveChildren(parent).filterNot { child =>
        hiddenPosts(child) ||
          (!(graph.children(parent) contains child) && graph.involvedInContainmentCycle(child))
      }
      visibleChildren.map(LocalContainment(parent, _))
    }
  }

  def involvedInCycleWithCollapsedPost(graph: Graph, child: PostId, collapsing: PostId => Boolean): Boolean = {
    graph.involvedInContainmentCycle(child) && graph.transitiveChildren(child).exists(collapsing)
  }

  def hasOneUncollapsedTransitiveParent(graph: Graph, child: PostId, collapsing: Set[PostId]): Boolean = {
    graph.transitiveParents(child).exists(parent => graph.parents(parent).isEmpty && !collapsing(parent) && reachableByUncollapsedPath(child, parent, graph, collapsing))
  }

  def reachableByUncollapsedPath(child: PostId, parent: PostId, graph: Graph, collapsing: Set[PostId]): Boolean = {
    val space = graph -- (collapsing - child)
    depthFirstSearch(child, space.parents).iterator contains parent
  }

  def highestParents(graph: Graph, child: PostId, predicate: PostId => Boolean): Set[PostId] = {
    graph.transitiveParents(child).filter(parent => predicate(parent) && graph.parents(parent).forall(!predicate(_))).toSet
  }
}
