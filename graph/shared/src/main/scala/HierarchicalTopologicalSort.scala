package wust.graph

import wust.util.algorithm._

import scala.collection.{IterableLike, mutable}

// http://blog.gapotchenko.com/stable-topological-sort
// https://en.wikipedia.org/wiki/Feedback_arc_set
object HierarchicalTopologicalSort {
  def apply[V, COLL[V]](vertices: IterableLike[V, COLL[V]], successors: V => Iterable[V], children: V => Iterable[V]): Seq[V] = {
    var sorted: List[V] = Nil
    val unmarked = mutable.LinkedHashSet.empty[V] ++ topologicalSort(vertices, children)
    val tempMarked = mutable.HashSet.empty[V]

    while (unmarked.nonEmpty) visit(unmarked.head)

    def visit(n: V) {
      if (unmarked(n)) {
        tempMarked += n
        unmarked -= n
        // for (m <- topologicalSort(children(n), successors)) visit(m)
        for (m <- children(n)) visit(m)
        for (m <- successors(n)) visit(m)
        tempMarked -= n
        sorted ::= n
      }
    }

    sorted
  }
}
