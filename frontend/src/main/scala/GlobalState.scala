package frontend

import graph._, api._
import mhtml._

case class InteractionMode(edit: Option[AtomId], focus: Option[AtomId])
object FocusMode {
  def unapply(mode: InteractionMode): Option[AtomId] = Some(mode) collect {
    case InteractionMode(None, Some(f)) => f
  }
}
object EditMode {
  def unapply(mode: InteractionMode): Option[AtomId] = Some(mode) collect {
    case InteractionMode(Some(e), _) => e
  }
}

class GlobalState {
  val rawGraph = RxVar(Graph.empty)
    .map(_.consistent)

  val currentView = RxVar[View]()

  val focusedPostId = RxVar[Option[AtomId]](None)
    .flatMap(source => rawGraph.map(g => source.filter(g.posts.isDefinedAt)))

  val editedPostId = RxVar[Option[AtomId]](None)
    .flatMap(source => rawGraph.map(g => source.filter(g.posts.isDefinedAt)))

  val mode = editedPostId.flatMap(e => focusedPostId.map(f => InteractionMode(edit = e, focus = f)))

  val collapsedPostIds = RxVar[Set[AtomId]](Set.empty)
    .flatMap(source => rawGraph.map(g => source.filter(g.posts.isDefinedAt)))

  val graph: RxVar[Graph, Graph] = for {
    graph <- rawGraph
    collapsed <- collapsedPostIds
  } yield {
    val toCollapse = collapsed
      .filterNot(id => graph.involvedInCycle(id) && graph.transitiveParents(id).map(_.id).exists(collapsed))

    val removePosts = toCollapse
      .map { collapsedId =>
        collapsedId -> graph.transitiveChildren(collapsedId).map(_.id)
      }.toMap

    val removeEdges = removePosts.values.flatten
      .map { p =>
        p -> graph.incidentConnections(p)
      }.toMap

    val addEdges = removePosts
      .flatMap { case (parent, children) => children.flatMap { child =>
        val edges = removeEdges(child)
        edges.flatMap{edgeid => 
          val edge = graph.connections(edgeid)
          if( edge.sourceId == child && edge.targetId == child )
            None
          else if (edge.sourceId == child)
            Some(edge.copy(sourceId = parent))
          else // edge.targetId == child
            Some(edge.copy(targetId = parent))
        }
      }
    }
    // TODO exclude overlappi
    graph
      .removePosts(removePosts.values.flatten)
      .removeConnections(removeEdges.values.flatten) ++ addEdges

  }

  // collapsedPostIds.debug("collapsed")
  // rawGraph.debug("rawGraph")
  // graph.debug("graph")

  val onApiEvent: ApiEvent => Unit = _ match {
    case NewPost(post) => graph.update(_ + post)
    case UpdatedPost(post) => graph.update(_ + post)
    case NewConnection(connects) => graph.update(_ + connects)
    case NewContainment(contains) => graph.update(_ + contains)

    case DeletePost(postId) => graph.update(_.removePost(postId))
    case DeleteConnection(connectsId) => graph.update(_.removeConnection(connectsId))
    case DeleteContainment(containsId) => graph.update(_.removeContainment(containsId))
  }
}
