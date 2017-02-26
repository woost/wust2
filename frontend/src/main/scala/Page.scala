package frontend

import graph._

// views immutable, dass urls nicht kaputt gehen
// wust.space/view/$viewitd

sealed trait View
object View {

  case object Empty extends View
  // case class Fixed(postId:AtomId) extends View
  // case class Search(str:String) extends View
  case class Collapse(collapsedIds: Set[AtomId]) extends View
  // case class Exclude(excloude:Post) extends View // Mir ist es egal, ob mein Problem ein todo ist oder nicht.
  // case class Include(post:Post) extends View // normal selection
  case class All(views: Iterable[View] = Nil) extends View
  case class Any(views: Iterable[View]) extends View
  case class NoneOf(views: Iterable[View]) extends View

  def All(views: View*) = new All(views)
  def Any(views: View*) = new Any(views)
  def NoneOf(views: View*) = new NoneOf(views)

  def apply(view: View, graph: Graph): Graph = {
    view match {
      case Empty => Graph.empty
      case Collapse(collapsed) =>
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
          .flatMap {
            case (parent, children) =>
              children.flatMap { child =>
                removeEdges(child).map(graph.connections).map {
                  case edge @ Connects(_, `child`, _) => edge.copy(sourceId = parent)
                  case edge @ Connects(_, _, `child`) => edge.copy(targetId = parent)
                }
              }
          }

        // TODO exclude overlappi
        graph
          .removeConnections(removeEdges.values.flatten)
          .++(addEdges)
          .removePosts(removePosts.values.flatten)
      case All(views) =>
        views.foldLeft(graph)((g, v) => View(v, g))
      case _ => graph
    }
  }
}
