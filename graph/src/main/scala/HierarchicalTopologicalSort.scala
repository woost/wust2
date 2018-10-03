package wust.graph

import wust.util.algorithm._

import scala.collection.{IterableLike, mutable}

// http://blog.gapotchenko.com/stable-topological-sort
// https://en.wikipedia.org/wiki/Feedback_arc_set
object HierarchicalTopologicalSort {
  def apply[V, COLL[V]](
      vertices: IterableLike[V, COLL[V]],
      successors: V => Iterable[V],
      children: V => Iterable[V]
  ): List[V] = {
    val connectionSort = topologicalSortSlow[V, COLL](vertices, successors)
    topologicalSortSlow[V, List](connectionSort, children)
  }
}
