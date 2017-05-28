package wust.util

object algorithm {
  import scala.collection.{IterableLike, breakOut, mutable}
  import scala.math.Ordering

  def directedAdjacencyList[V1, E, V2](edges: Iterable[E], inf: E => V1, outf: E => V2): Map[V1, Set[V2]] = { // TODO: Multimap
    edges
      .foldLeft(Map.empty[V1, Set[V2]].withDefaultValue(Set.empty[V2])) {
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

  case class Tree[A](element: A, children: Seq[Tree[A]])
  class TreeContext[A](trees: Tree[A]*)(implicit ordering: Ordering[A])  {
    private def findParentMap(tree: Tree[A]): Map[Tree[A], Tree[A]] = {
      tree.children.map(child => child -> tree).toMap ++ tree.children.map(findParentMap _).fold(Map.empty)(_ ++ _)
    }
    private def findPreviousMap(trees: Seq[Tree[A]]): Map[Tree[A], Tree[A]] = {
      val sortedTrees = trees.sortBy(_.element)
      sortedTrees.drop(1).zip(sortedTrees).toMap ++ trees.map(tree => findPreviousMap(tree.children)).fold(Map.empty)(_ ++ _)
    }

    lazy val parentMap: Map[Tree[A], Tree[A]] = trees.map(findParentMap _).fold(Map.empty)(_ ++ _)
    lazy val previousMap: Map[Tree[A], Tree[A]] = findPreviousMap(trees)
  }

  def redundantSpanningTree[V](root: V, successors: V => Iterable[V]): Tree[V] = redundantSpanningTree(root, successors, Set(root))
  def redundantSpanningTree[V](root: V, successors: V => Iterable[V], seen: Set[V]): Tree[V] = {
    Tree(root, children = successors(root).filterNot(seen).map { child =>
      redundantSpanningTree(child, successors, seen ++ Seq(child))
    }(breakOut))
  }
}

