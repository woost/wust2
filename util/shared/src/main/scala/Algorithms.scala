package wust.util

object algorithm {
  import scala.collection.{IterableLike, breakOut, mutable}
  import math.Ordering

  def defaultNeighbourhood[V, T](vertices: Iterable[V], default: T): scala.collection.Map[V,T] = {
    val map = mutable.HashMap[V,T]().withDefaultValue(default)
    map.sizeHint(vertices.size)
    vertices.foreach { v =>
      map(v) = default
    }
    map
  }

  def directedAdjacencyList[V1, E, V2](edges: Iterable[E], inf: E => V1, outf: E => V2): scala.collection.Map[V1, scala.collection.Set[V2]] = { // TODO: Multimap
    val map = mutable.HashMap[V1, scala.collection.Set[V2]]().withDefaultValue(mutable.HashSet.empty[V2])
    edges.foreach {e =>
      val in = inf(e)
      val out = outf(e)
      map(in) += out
    }
    map
  }

  def adjacencyList[V, E](edges: Iterable[E], inf: E => V, outf: E => V): scala.collection.Map[V, scala.collection.Set[V]] = { // TODO: Multimap
    val map = mutable.HashMap[V, scala.collection.Set[V]]().withDefaultValue(mutable.HashSet.empty[V])
    edges.foreach {e =>
      val in = inf(e)
      val out = outf(e)
      map(in) += out
      map(out) += in
    }
    map
  }

  def degreeSequence[V, E](edges: Iterable[E], inf: E => V, outf: E => V): scala.collection.Map[V, Int] = {
    val map = mutable.HashMap[V, Int]().withDefaultValue(0)
    edges.foreach {e =>
      val in = inf(e)
      val out = outf(e)
      map(in) += 1
      map(out) += 1
    }
    map
  }

  def directedDegreeSequence[V, E](edges: Iterable[E], inf: E => V): scala.collection.Map[V, Int] = {
    val map = mutable.HashMap[V, Int]().withDefaultValue(0)
    edges.foreach {e =>
      val in = inf(e)
      map(in) += 1
    }
    map
  }

  def directedIncidenceList[V, E](edges: Iterable[E], inf: E => V): scala.collection.Map[V, scala.collection.Set[E]] = { // TODO: Multimap
    val map = mutable.HashMap[V, scala.collection.Set[E]]().withDefaultValue(mutable.HashSet.empty[E])
    edges.foreach {e =>
      val in = inf(e)
      map(in) += e
    }
    map
  }

  def incidenceList[V, E](edges: Iterable[E], inf: E => V, outf: E => V): scala.collection.Map[V, scala.collection.Set[E]] = { // TODO: Multimap
    val map = mutable.HashMap[V, scala.collection.Set[E]]().withDefaultValue(mutable.HashSet.empty[E])
    edges.foreach {e =>
      val in = inf(e)
      val out = outf(e)
      map(in) += e
      map(out) += e
    }
    map
  }

  //TODO: depthfirstsearch producing a sequence
  def depthFirstSearch[V](start: V, continue: V => Iterable[V]) = new Iterable[V] {
    private var _startInvolvedInCycle = false
    def startInvolvedInCycle = _startInvolvedInCycle

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
          if(candidate == start) _startInvolvedInCycle = true
          if (!seen(candidate) && !onStack(candidate)) {
            stack push candidate
            onStack += candidate
          }
        }

        current
      }
    }
    iterator.size // consume iterator
  }

  def connectedComponents[V](vertices:Iterable[V], continue: V => Iterable[V]):List[Set[V]] = {
    val left = mutable.HashSet.empty ++ vertices
    var components:List[Set[V]] = Nil
    while(left.nonEmpty) {
      val next = left.head
      val component = depthFirstSearch(next, continue).toSet
      components ::= component
      left --= component
    }
    components
  }

  def topologicalSort[V, COLL[V]](vertices: IterableLike[V, COLL[V]], successors: V => Iterable[V]): List[V] = {
    var sorted: List[V] = Nil
    val unmarked = mutable.LinkedHashSet.empty[V] ++ vertices.toList.reverse
    val tempMarked = mutable.HashSet.empty[V]

    while (unmarked.nonEmpty) visit(unmarked.head)

    def visit(n: V):Unit = {
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
    private def findNextMap(trees: Seq[Tree[A]]): Map[Tree[A], Tree[A]] = {
      val sortedTrees = trees.sortBy(_.element)
      sortedTrees.zip(sortedTrees.drop(1)).toMap ++ trees.map(tree => findNextMap(tree.children)).fold(Map.empty)(_ ++ _)
    }

    lazy val parentMap: Map[Tree[A], Tree[A]] = trees.map(findParentMap _).fold(Map.empty)(_ ++ _)
    lazy val previousMap: Map[Tree[A], Tree[A]] = findPreviousMap(trees)
    lazy val nextMap: Map[Tree[A], Tree[A]] = findNextMap(trees)
  }

  def redundantSpanningTree[V](root: V, successors: V => Iterable[V]): Tree[V] = redundantSpanningTree(root, successors, Set(root))
  def redundantSpanningTree[V](root: V, successors: V => Iterable[V], seen: Set[V]): Tree[V] = {
    Tree(root, children = successors(root).filterNot(seen).map { child =>
      redundantSpanningTree(child, successors, seen ++ Seq(child))
    }(breakOut))
  }
}

