package util

object algorithm {
  import collection.mutable
  import collection.IterableLike

  def directedAdjacencyList[V, E](edges: Iterable[E], inf: E => V, outf: E => V): Map[V, Set[V]] = { // TODO: Multimap
    edges
      .foldLeft(Map.empty[V, Set[V]].withDefaultValue(Set.empty[V])) {
        (acc, e) =>
          val in = inf(e)
          val out = outf(e)
          val existing = acc(in)
          acc + (in -> (existing + out))
      }
  }

  def adjacencyList[V, E](edges: Iterable[E], inf: E => V, outf: E => V): Map[V, Set[V]] = { // TODO: Multimap
    edges
      .foldLeft(Map.empty[V, Set[V]].withDefaultValue(Set.empty[V])) {
        (acc, e) =>
          val in = inf(e)
          val out = outf(e)
          val existingOut = acc(in)
          val existingIn = acc(out)
          acc + (in -> (existingOut + out)) + (out -> (existingIn + in))
      }
  }

  def degreeSequence[V, E](edges: Iterable[E], inf: E => V, outf: E => V): Map[V, Int] = {
    edges
      .foldLeft(Map.empty[V, Int].withDefaultValue(0)) {
        (acc, e) =>
          val in = inf(e)
          val out = outf(e)
          val existingOut = acc(in)
          val existingIn = acc(out)
          acc + (in -> (existingOut + 1)) + (out -> (existingIn + 1))
      }
  }

  def directedDegreeSequence[V, E](edges: Iterable[E], inf: E => V): Map[V, Int] = {
    edges
      .foldLeft(Map.empty[V, Int].withDefaultValue(0)) {
        (acc, e) =>
          val in = inf(e)
          val existingOut = acc(in)
          acc + (in -> (existingOut + 1))
      }
  }

  def directedIncidenceList[V, E](edges: Iterable[E], inf: E => V): Map[V, Set[E]] = { // TODO: Multimap
    edges
      .foldLeft(Map.empty[V, Set[E]].withDefaultValue(Set.empty[E])) {
        (acc, e) =>
          val in = inf(e)
          val existing = acc(in)
          acc + (in -> (existing + e))
      }
  }

  def incidenceList[V, E](edges: Iterable[E], inf: E => V, outf: E => V): Map[V, Set[E]] = { // TODO: Multimap
    edges
      .foldLeft(Map.empty[V, Set[E]].withDefaultValue(Set.empty[E])) {
        (acc, e) =>
          val in = inf(e)
          val out = outf(e)
          val existingOut = acc(in)
          val existingIn = acc(out)
          acc + (in -> (existingOut + e)) + (out -> (existingIn + e))
      }
  }

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

  def topologicalSort[V, COLL[V]](vertices: IterableLike[V, COLL[V]], successors: V => Iterable[V]): Seq[V] = {
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
