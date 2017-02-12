package graph
package object algorithm {
  import collection.mutable

  //TODO: depthfirstsearch producing a sequence
  def depthFirstSearch[V](start: V, continue: V => Iterable[V]) = new Iterable[V] {
    def iterator = new Iterator[V] {

      val stack = mutable.Stack(start)
      val onStack = mutable.Set[V]()
      val seen = mutable.Set[V]()

      override def hasNext: Boolean = stack.nonEmpty
      override def next: V = {
        val current = stack.pop
        onStack -= current
        seen += current

        for (candidate <- continue(current)) {
          if (!seen(candidate) && !onStack(candidate)) {
            stack push candidate
            onStack += candidate
          }
        }

        current
      }
    }
  }

  def topologicalSort[V](vertices: Iterable[V], successors: V => Iterable[V]): Seq[V] = {
    // TODO: give result even if there is a cycle

    var sorted: List[V] = Nil
    val unmarked = mutable.HashSet.empty[V] ++ vertices
    val tempMarked = mutable.HashSet.empty[V]

    while (unmarked.nonEmpty) visit(unmarked.head)

    def visit(n: V) {
      if (unmarked(n)) {
        tempMarked += n
        unmarked -= n
        for (m <- successors(n)) visit(m)
        tempMarked -= n
        sorted ::= n
      }
    }

    sorted
  }
}
