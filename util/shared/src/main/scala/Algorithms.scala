package wust.util

import scala.collection.{ IterableLike, breakOut, mutable }
import flatland._

object algorithm {

  @deprecated("Old and slow Graph algorithm. Don't use this.", "")
  def defaultNeighbourhood[V, T](vertices: Iterable[V], default: T): scala.collection.Map[V, T] = {
    val map = mutable.HashMap[V, T]().withDefaultValue(default)
    map.sizeHint(vertices.size)
    vertices.foreach { v =>
      map(v) = default
    }
    map
  }

  @deprecated("Old and slow Graph algorithm. Don't use this.", "")
  def directedAdjacencyList[V1, E, V2](
    edges: Iterable[E],
    inf: E => V1,
    outf: E => V2
  ): scala.collection.Map[V1, scala.collection.Set[V2]] = { // TODO: Multimap
    val map =
      mutable.HashMap[V1, scala.collection.Set[V2]]().withDefaultValue(mutable.HashSet.empty[V2])
    edges.foreach { e =>
      val in = inf(e)
      val out = outf(e)
      map(in) += out
    }
    map
  }

  def containsCycle(elements: Array[Int], successors: NestedArrayInt): Boolean = {

    val num = elements.length
    var i = 0

    while (i < num) {
      val idx = elements(i)
      if (depthFirstSearchExistsAfterStart(idx, successors, idx))
        return true

      i += 1
    }

    false
  }

  // @inline avoids the function call of append
  // stops whole traversal if append returns false
  @inline def depthFirstSearchWithManualAppendStopIfAppendFalse(start: Int, successors: NestedArrayInt, append: Int => Boolean): Unit = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet
    @inline def stackPush(elem:Int):Unit = {
      stack.push(elem)
      visited += elem
    }

    if (!append(start)) return

    visited += start
    successors.foreachElement(start)(stackPush)

    while (!stack.isEmpty) {
      val current = stack.pop()
      if (!append(current)) return

      successors.foreachElement(current) { next =>
        if (visited.containsNot(next)) {
          stackPush(next)
        }
      }
    }
  }

  // @inline avoids the function call of append
  // stops whole traversal if append returns false
  @inline def depthFirstSearchWithManualAppend(start: Int, successors: NestedArrayInt, append: Int => Unit): Unit = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet
    @inline def stackPush(elem:Int):Unit = {
      stack.push(elem)
      visited += elem
    }

    visited += start
    append(start)

    successors.foreachElement(start)(stackPush)

    while (!stack.isEmpty) {
      val current = stack.pop()
      append(current)

      successors.foreachElement(current) { next =>
        if (visited.containsNot(next)) {
          stackPush(next)
        }
      }
    }
  }

  // @inline avoids the function call of append
  // stops only traversing local branch
  @inline def depthFirstSearchWithManualSuccessors(start: Int, size: Int, successors: Int => (Int => Unit) => Unit, f: Int => Unit): Unit = {
    val stack = ArrayStackInt.create(capacity = size)
    val visited = ArraySet.create(size) // JS: Array[Int] faster than Array[Boolean] and BitSet
    @inline def stackPush(elem:Int):Unit = {
      stack.push(elem)
      visited += elem
    }

    successors(start){ next =>
      stackPush(next)
    }

    while (!stack.isEmpty) {
      val current = stack.pop()
      f(current)
      successors(current) { next =>
        if (visited.containsNot(next)) {
          stackPush(next)
        }
      }
    }
  }

  // @inline avoids the function call of append
  // stops only traversing local branch
  @inline def depthFirstSearchAfterStartWithContinue(start: Int, successors: NestedArrayInt, continue: Int => Boolean): Unit = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet
    @inline def stackPush(elem:Int):Unit = {
      stack.push(elem)
      visited += elem
    }

    successors.foreachElement(start)(stackPush)

    while (!stack.isEmpty) {
      val current = stack.pop()
      if (continue(current)) {
        successors.foreachElement(current) { next =>
          if (visited.containsNot(next)) {
            stackPush(next)
          }
        }
      }
    }
  }

  // @inline avoids the function call of append
  // stops only traversing local branch
  @inline def depthFirstSearchAfterStartsWithContinue(starts: Array[Int], successors: NestedArrayInt, continue: Int => Boolean): Unit = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet
    @inline def stackPush(elem:Int):Unit = {
      stack.push(elem)
      visited += elem
    }

    starts.foreach { start =>
      successors.foreachElement(start)(stackPush)
    }

    while (!stack.isEmpty) {
      val current = stack.pop()
      if (continue(current)) {
        successors.foreachElement(current) { next =>
          if (visited.containsNot(next)) {
            stackPush(next)
          }
        }
      }
    }
  }

  /*
   * DFS starts after the start index and searches for `search`
   * Choosing start = search results in a cycle search
   * Returns the index of the preceding element that has been found
   */
  // @inline avoids the function call of append
  // stops only traversing local branch
  @inline def depthFirstSearchWithParentSuccessors(starts: Array[Int], size: Int, successors: (Option[Int], Int) => (Int => Unit) => Unit): Unit = {
    val stack = ArrayStackInt.create(capacity = size)
    val stackParent = ArrayStackInt.create(capacity = size)
    val visited = ArraySet.create(size) // JS: Array[Int] faster than Array[Boolean] and BitSet
    @inline def stackPush(elem:Int):Unit = {
      stack.push(elem)
      visited += elem
    }

    starts.foreach { start =>
      successors(None, start) { succElem =>
        stackPush(succElem)
        stackParent.push(start)
      }
    }

    while (!stack.isEmpty) {
      val current = stack.pop()
      val currentParent = stackParent.pop()
      successors(Some(currentParent), current) { next =>
        if (visited.containsNot(next)) {
          stackPush(next)
          stackParent.push(current)
        }
      }
    }
  }

  def depthFirstSearchAfterStart(start: Int, successors: NestedArrayInt, search: Int): Option[Int] = {

    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet
    @inline def stackPush(elem:Int):Unit = {
      stack.push(elem)
      visited += elem
    }

    stackPush(start)
    successors.foreachElement(start)(stackPush)

    while (!stack.isEmpty) {
      val current = stack.pop()
      val previous = stack.pop()
      if (current == search) return Some(previous)

      successors.foreachElement(current) { next =>
        if (visited.containsNot(next)) {
          stackPush(next)
        }
      }
    }

    None
  }

  def containmentsOfCycle(elements: Array[Int], successors: NestedArrayInt): Array[Int] = {
    val num = elements.length
    var i = 0
    val result = new mutable.ArrayBuilder.ofInt

    while (i < num) {
      val idx = elements(i)
      if (depthFirstSearchExistsAfterStart(idx, successors, idx))
        result += idx

      i += 1
    }

    result.result()
  }

  /*
   * DFS starts after the start index and searches for `search`
   * Choosing start = search results in a cycle search
   */
  def depthFirstSearchExistsAfterStart(start: Int, successors: NestedArrayInt, search: Int): Boolean = {

    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet
    @inline def stackPush(elem:Int):Unit = {
      stack.push(elem)
      visited += elem
    }

    successors.foreachElement(start)(stackPush)

    while (!stack.isEmpty) {
      val current = stack.pop()
      if (current == search) return true

      successors.foreachElement(current) { next =>
        if (visited.containsNot(next)) {
          stackPush(next)
        }
      }
    }

    false
  }

  def depthFirstSearch(start: Int, successors: NestedArrayInt): Array[Int] = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size)
    val result = new mutable.ArrayBuilder.ofInt
    @inline def stackPush(elem:Int):Unit = {
      stack.push(elem)
      visited += elem
    }

    // this part could also just be:
    // stackPush(start)
    // but this one is faster, since it allows the first
    // step with fewer checks.
    result += start
    visited += start
    successors.foreachElement(start)(stackPush)

    while (!stack.isEmpty) {
      val current = stack.pop()

      result += current
      visited += current
      successors.foreachElement(current) { next =>
        if (visited.containsNot(next)) {
          stackPush(next)
        }
      }
    }

    result.result()
  }

  def depthFirstSearchExists(starts: Iterable[Int], successors: NestedArrayInt, search: ArraySet): Boolean = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size)
    @inline def stackPush(elem:Int):Unit = {
      stack.push(elem)
      visited += elem
    }

    starts.foreach(stackPush)

    while (!stack.isEmpty) {
      val current = stack.pop()
      if (search.contains(current)) return true

      successors.foreachElement(current) { next =>
        if (visited.containsNot(next)) {
          stackPush(next)
        }
      }
    }

    false
  }

  def depthFirstSearchExists(start: Int, successors: NestedArrayInt, search: Int): Boolean = {
    if (start == search) return true

    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size)
    @inline def stackPush(elem:Int):Unit = {
      stack.push(elem)
      visited += elem
    }

    visited += start
    successors.foreachElement(start)(stackPush)

    while (!stack.isEmpty) {
      val current = stack.pop()
      if (current == search) return true

      successors.foreachElement(current) { next =>
        if (visited.containsNot(next)) {
          stackPush(next)
        }
      }
    }

    false
  }

  def depthFirstSearchExcludeExists(start: Int, successors: NestedArrayInt, exclude: ArraySet, search: Int): Boolean = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size)
    @inline def stackPush(elem:Int):Unit = {
      stack.push(elem)
      visited += elem
    }

    visited += start
    successors.foreachElement(start){ elem =>
      if (exclude.containsNot(elem)) {
        stackPush(elem)
      }
    }

    while (!stack.isEmpty) {
      val current = stack.pop()
      if (current == search) return true

      successors.foreachElement(current) { next =>
        if (visited.containsNot(next) && exclude.containsNot(next)) {
          stackPush(next)
        }
      }
    }

    false
  }

  def depthFirstSearchAfterStarts(start: Array[Int], successors: NestedArrayInt): Array[Int] = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size)
    val result = new mutable.ArrayBuilder.ofInt
    @inline def stackPush(elem:Int):Unit = {
      stack.push(elem)
      visited += elem
    }

    // start is intentionally left out.
    // it is still possible that start is visited later. (in a cycle)
    // result += start
    // visited(start) = 1

    start.foreachElement { start =>
      successors.foreachElement(start)(stackPush)
    }

    while (!stack.isEmpty) {
      val current = stack.pop()
      result += current

      successors.foreachElement(current) { next =>
        if (visited.containsNot(next)) {
          stackPush(next)
        }
      }
    }

    result.result()
  }

  def depthFirstSearchAfterStart(start: Int, successors: NestedArrayInt): Array[Int] = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size)
    val result = new mutable.ArrayBuilder.ofInt
    @inline def stackPush(elem:Int):Unit = {
      stack.push(elem)
      visited += elem
    }

    // start is intentionally left out.
    // it is still possible that start is visited later. (in a cycle)
    // result += start
    // visited += start

    successors.foreachElement(start)(stackPush)

    while (!stack.isEmpty) {
      val current = stack.pop()
      result += current

      successors.foreachElement(current) { next =>
        if (visited.containsNot(next)) {
          stackPush(next)
        }
      }
    }

    result.result()
  }

  @deprecated("This is the old, slow version of DFS", "")
  def depthFirstSearchWithStartInCycleDetection[V](start: V, continue: V => Iterable[V]) = new Iterable[V] {
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
          if (candidate == start) _startInvolvedInCycle = true
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

  def shortestPathsIdx(next: NestedArrayInt) = {
    val n = next.length
    val memoizedLengths = Array.fill[Int](n)(-1)
    val visited = ArraySet.create(n) // to handle cycles

    def searchShortestPath(idx: Int): Int = {
      val shortestPath = memoizedLengths(idx)
      if(shortestPath == -1) {
        if (!visited(idx)) {
          visited += idx

          val d = if(next.sliceIsEmpty(idx)) 0
          else (next.minByInt(idx)(initMin = n)(searchShortestPath) + 1)

          memoizedLengths(idx) = d
          d
        } else 0 // cycle
      } else shortestPath
    }

    memoizedLengths.foreachIndexAndElement { (idx, depth) =>
      if(depth == -1) searchShortestPath(idx)
    }
    memoizedLengths
  }

  def longestPathsIdx(next: NestedArrayInt):Array[Int] = {
    val memoizedLengths = Array.fill[Int](next.length)(-1)
    val visited = ArraySet.create(next.length) // to handle cycles

    def searchLongestPath(idx: Int): Int = {
      val longestPath = memoizedLengths(idx)
      if(longestPath == -1) {
        if (!visited(idx)) {
          visited += idx

          val d = if(next.sliceIsEmpty(idx)) 0
          else (next.maxByInt(idx)(initMax = 0)(searchLongestPath) + 1)

          memoizedLengths(idx) = d
          d
        } else 0 // cycle
      } else longestPath
    }

    memoizedLengths.foreachIndexAndElement { (idx, depth) =>
      if(depth == -1) searchLongestPath(idx)
    }
    memoizedLengths
  }


  def dijkstra[V](edges: V => Iterable[V], source: V): (Map[V, Int], Map[V, V]) = {
    def run(active: Set[V], res: Map[V, Int], pred: Map[V, V]): (Map[V, Int], Map[V, V]) =
      if (active.isEmpty) (res, pred)
      else {
        val node = active.minBy(res)
        val cost = res(node)
        val neighbours = (for {
          n <- edges(node) if cost + 1 < res.getOrElse(n, Int.MaxValue)
        } yield n -> (cost + 1)).toMap
        val active1 = active - node ++ neighbours.keys
        val preds = neighbours mapValues (_ => node)
        run(active1, res ++ neighbours, pred ++ preds)
      }

    run(Set(source), Map(source -> 0), Map.empty)
  }

  def connectedComponents[V](vertices: Iterable[V], continue: V => Iterable[V]): List[Set[V]] = {
    val left = mutable.HashSet.empty ++ vertices
    var components: List[Set[V]] = Nil
    while (left.nonEmpty) {
      val next = left.head
      val component = depthFirstSearchWithStartInCycleDetection(next, continue).toSet
      components ::= component
      left --= component
    }
    components
  }

  def topologicalSort(
    vertices: Array[Int], // indices of vertices in successors
    successors: NestedArrayInt
  ): Array[Int] = {
    // assert(vertices.length <= successors.length)
    // assert(vertices.length == vertices.toSet.size, "no duplicates allowed")
    // https://en.wikipedia.org/wiki/Topological_sorting#Depth-first_search

    // vertices.size may be smaller than successors.size

    val n = successors.length // the number of total vertices in the graph
    val vertexCount = vertices.length // the number of vertices to sort

    val _reverseLookup = {
      val a = new Array[Int](n)
      var i = 0
      while (i < vertices.length) {
        a(vertices(i)) = i + 1 // offset, so that 0 corresponds to -1, this saves filling the array with -1 first
        i += 1
      }
      a
    }
    @inline def reverseLookup(i: Int) = _reverseLookup(i) - 1

    val sorted = new Array[Int](vertexCount) // the result
    var sortCount = 0 // count to fill the result array from back to front
    val visited = ArraySet.create(vertexCount)

    @inline def addToSorted(vertex: Int): Unit = {
      sorted(vertexCount - sortCount - 1) = vertex
      sortCount += 1
    }

    def visit(idx: Int): Unit = {
      val vertex = vertices(idx)
      visited += idx
      successors.foreachElement(vertex) { nextIdx =>
        val revIdx = reverseLookup(nextIdx)
        if (revIdx != -1 && visited.containsNot(revIdx)) {
          visit(revIdx)
        }
      }
      addToSorted(vertex)
    }

    var i = vertices.length - 1
    while (i > -1) {
      if (visited.containsNot(i)) visit(i)
      i -= 1
    }

    sorted
  }

  def topologicalSortForward(
    vertices: Array[Int], // indices of vertices in successors
    successors: NestedArrayInt
  ): Array[Int] = {

    val n = successors.length // the number of total vertices in the graph
    val vertexCount = vertices.length // the number of vertices to sort

    var sorted = new Array[Int](vertexCount) // the result
    var sortCount = 0 // count to fill the result array from back to front
    val visited = new Array[Int](vertexCount)

    @inline def add(vertex: Int): Unit = {
      sorted(sortCount) = vertex
      sortCount += 1
    }

    def visit(idx: Int): Unit = {
      val vertex = vertices(idx)
      visited(idx) = 1
      for (nextIdx <- successors(vertex)) {
        val revIdx = vertices(nextIdx)
        if (revIdx != -1 && visited(revIdx) == 0) {
          visit(revIdx)
        }
      }
      add(vertex)
    }

    var i = 0
    while (i < vertexCount) {
      if (visited(i) == 0) visit(i)
      i += 1
    }

    sorted
  }

  @deprecated("This is the old, slow version of topologicalSort", "")
  def topologicalSortSlow[V, COLL[V]](
    vertices: IterableLike[V, COLL[V]],
    successors: V => Iterable[V]
  ): List[V] = {
    var sorted: List[V] = Nil
    val unmarked = mutable.LinkedHashSet.empty[V] ++ vertices.toList.reverse // for stable algorithm

    def visit(n: V): Unit = {
      if (unmarked(n)) {
        unmarked -= n
        for (m <- successors(n)) visit(m)
        sorted ::= n
      }
    }

    while (unmarked.nonEmpty) visit(unmarked.head)

    sorted
  }

  def eulerDiagramDualGraph(parents:NestedArrayInt, children:NestedArrayInt, isEulerSet: Int => Boolean): (Array[Set[Int]], NestedArrayInt, InterleavedArrayInt) = {

    // for each node, the set of parent nodes identifies its zone
    val zoneGrouping:Seq[(Set[Int],Seq[Int])] = parents.indices
      .groupBy{nodeIdx =>
        val parentSet = parents(nodeIdx).toSet
          // remove redundant higher-level parents
          parentSet.filter(parentIdx => children(parentIdx).filter(parentSet).count(isEulerSet) == 0)
      }.flatMap{
      case (parentSet, nodeIndices) if parentSet.isEmpty => // All isolated nodes
        Array(parentSet -> nodeIndices.filterNot(isEulerSet)) // remove parents from isolated nodes
      case (parentSet, nodeIndices) if parentSet.size == 1 => // non overlapping area of eulerset
        Array(parentSet -> (nodeIndices.filterNot(isEulerSet) :+ parentSet.head))
      case other => Array(other)
    }.toSeq.filter(_._2.nonEmpty) ++ parents.indices.filter(i => (children.sliceNonEmpty(i) || isEulerSet(i)) && children.forall(i)(c => parents.sliceLength(c) != 1)).map(i => (Set(i), Seq(i)))
    val eulerZones:Array[Set[Int]] = zoneGrouping.map(_._1)(breakOut)
    val eulerZoneNodes:Array[Array[Int]] = zoneGrouping.map(_._2.toArray)(breakOut)




    def setDifference[T](a:Set[T], b:Set[T]) = (a union b) diff (a intersect b)
    val neighbourhoodBuilder = mutable.ArrayBuilder.make[(Int,Int)]

    eulerZones.foreachIndex2Combination { (zoneAIdx, zoneBIdx) =>
      // Two zones in an euler diagram are separated by a line iff their parentSet definitions differ by exactly one element
      @inline def zonesDifferByExactlyOneElement = setDifference(eulerZones(zoneAIdx),eulerZones(zoneBIdx)).size == 1
      @inline def zonesAreNotEmptyZone = eulerZones(zoneAIdx).nonEmpty && eulerZones(zoneBIdx).nonEmpty
      if( zonesAreNotEmptyZone && zonesDifferByExactlyOneElement ) {
        neighbourhoodBuilder += ((zoneAIdx, zoneBIdx))
      }
    }

    val neighbourhoods = neighbourhoodBuilder.result()
    val edges = InterleavedArrayInt.create(neighbourhoods.length)
    neighbourhoods.foreachIndexAndElement{ (i, ab) =>
      edges.updatea(i, ab._1)
      edges.updateb(i, ab._2)
    }

    (eulerZones, NestedArrayInt(eulerZoneNodes), edges)
  }

}
