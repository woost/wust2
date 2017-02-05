package object Algorithms {
  import collection.mutable

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
}
