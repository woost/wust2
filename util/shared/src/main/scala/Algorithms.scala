package wust.util

import scala.collection.{IterableLike, breakOut, mutable}
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

    while(i < num) {
      val idx = elements(i)
      if(depthFirstSearchExistsAfterStart(idx, successors, idx))
        return true

      i += 1
    }

    false
  }

  // @inline avoids the function call of append
  // stops whole traversal if append returns false
  @inline def depthFirstSearchWithManualAppendStopIfAppendFalse(start: Int, successors: NestedArrayInt, append: Int => Boolean):Unit = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = new Array[Int](successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet

    if (!append(start)) return

    visited(start) = 1
    var i = 0
    val startSuccessorCount = successors(start).length
    while(i < startSuccessorCount) {
      stack.push(successors(start, i))
      i += 1
    }

    while(!stack.isEmpty) {
      val current = stack.pop()
      if(visited(current) != 1) {
        if (!append(current)) return

        visited(current) = 1
        val nexts = successors(current)
        val nextCount = nexts.length
        i = 0
        while(i < nextCount) {
          val next = nexts(i)
          if(visited(next) != 1) stack.push(next)
          i += 1
        }
      }
    }
  }


  // @inline avoids the function call of append
  // stops whole traversal if append returns false
  @inline def depthFirstSearchWithManualAppend(start: Int, successors: NestedArrayInt, append: Int => Unit):Unit = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet

    visited += start
    append(start)

    successors.foreachElement(start) { succElem =>
      stack.push(succElem)
    }

    while(!stack.isEmpty) {
      val current = stack.pop()
      if(visited.containsNot(current)) {

        visited += current
        append(current)

        successors.foreachElement(current) { next =>
          if(visited.containsNot(next)) stack.push(next)
        }
      }
    }
  }


  // @inline avoids the function call of append
  // stops only traversing local branch
  @inline def depthFirstSearchAfterStartsWithContinue(starts: Array[Int], successors: NestedArrayInt, continue: Int => Boolean):Unit = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet

    starts.foreach { start =>
      successors.foreachElement(start) { succElem =>
        stack.push(succElem)
      }
    }

    while(!stack.isEmpty) {
      val current = stack.pop()
      if(visited.containsNot(current)) {
        visited += current
        if (continue(current)) {
          successors.foreachElement(current) { next =>
            if(visited.containsNot(next)) stack.push(next)
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
  @inline def depthFirstSearchWithParentSuccessors(starts: Array[Int], size: Int, successors: (Option[Int], Int) => (Int => Unit) => Unit):Unit = {
    val stack = ArrayStackInt.create(capacity = size)
    val stackParent = ArrayStackInt.create(capacity = size)
    val visited = ArraySet.create(size) // JS: Array[Int] faster than Array[Boolean] and BitSet

    starts.foreach { start =>
      successors(None, start) { succElem =>
        stack.push(succElem)
        stackParent.push(start)
      }
    }

    while(!stack.isEmpty) {
      val current = stack.pop()
      val currentParent = stackParent.pop()
      if(visited.containsNot(current)) {
        visited += current
        successors(Some(currentParent), current) { next =>
          if(visited.containsNot(next)) {
            stack.push(next)
            stackParent.push(current)
          }
        }

      }
    }

    None
  }


  def depthFirstSearchAfterStart(start: Int, successors: NestedArrayInt, search:Int): Option[Int] = {

    val stack = ArrayStackInt.create(capacity = 2 * successors.size)
    val visited = ArraySet.create(successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet

    successors.foreachElement(start) { succElem =>
      stack.push(start)
      stack.push(succElem)
    }

    while(!stack.isEmpty) {
      val current = stack.pop()
      val previous = stack.pop()
      if(current == search) return Some(previous)
      if(visited.containsNot(current)) {
        visited.add(current)

        successors.foreachElement(current) { next =>
          if(visited.containsNot(next)) {
            stack.push(current)
            stack.push(next)
          }
        }

      }
    }

    None
  }

  def containmentsOfCycle(elements: Array[Int], successors: NestedArrayInt): Array[Int] = {
    val num = elements.length
    var i = 0
    val result = new mutable.ArrayBuilder.ofInt

    while(i < num) {
      val idx = elements(i)
      if(depthFirstSearchExistsAfterStart(idx, successors, idx))
        result += idx

      i += 1
    }

    result.result()
  }

  /*
   * DFS starts after the start index and searches for `search`
   * Choosing start = search results in a cycle search
   */
  def depthFirstSearchExistsAfterStart(start: Int, successors: NestedArrayInt, search:Int):Boolean = {

    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = new Array[Int](successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet

    var i = 0
    val startSuccessorCount = successors(start).length
    while(i < startSuccessorCount) {
      stack.push(successors(start, i))
      i += 1
    }

    while(!stack.isEmpty) {
      val current = stack.pop()
      if(current == search) return true
      if(visited(current) != 1) {
        visited(current) = 1
        val nexts = successors(current)
        val nextCount = nexts.length
        i = 0
        while(i < nextCount) {
          val next = nexts(i)
          if(visited(next) != 1) stack.push(next)
          i += 1
        }
      }
    }

    false
  }

  def depthFirstSearch(start: Int, successors: NestedArrayInt):Array[Int] = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = new Array[Int](successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet
    val result = new mutable.ArrayBuilder.ofInt

    // this part could also just be:
    // stack.push(start)
    // but this one is faster, since it allows the first
    // step with fewer checks.
    result += start
    visited(start) = 1
    var i = 0
    val startSuccessorCount = successors(start).length
    while(i < startSuccessorCount) {
      stack.push(successors(start, i))
      i += 1
    }

    while(!stack.isEmpty) {
      val current = stack.pop()
      if(visited(current) != 1) {
        result += current
        visited(current) = 1
        val nexts = successors(current)
        val nextCount = nexts.length
        i = 0
        while(i < nextCount) {
          val next = nexts(i)
          if(visited(next) != 1) stack.push(next)
          i += 1
        }
      }
    }


    result.result()
  }


  def depthFirstSearchExists(starts: Iterable[Int], successors: NestedArrayInt, search: ArraySet):Boolean = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = ArraySet.create(successors.size)

    starts.foreach(stack.push)

    while(!stack.isEmpty) {
      val current = stack.pop()
      if(search.contains(current)) return true
      if(visited.containsNot(current)) {
        visited.add(current)
        successors.foreachElement(current) { next =>
          if(visited.containsNot(next)) stack.push(next)
        }
      }
    }

    false
  }


  def depthFirstSearchExists(start: Int, successors: NestedArrayInt, search:Int):Boolean = {
    if(start == search) return true

    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = new Array[Int](successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet

    visited(start) = 1
    var i = 0
    val startSuccessorCount = successors(start).length
    while(i < startSuccessorCount) {
      stack.push(successors(start, i))
      i += 1
    }

    while(!stack.isEmpty) {
      val current = stack.pop()
      if(current == search) return true
      if(visited(current) != 1) {
        visited(current) = 1
        val nexts = successors(current)
        val nextCount = nexts.length
        i = 0
        while(i < nextCount) {
          val next = nexts(i)
          if(visited(next) != 1) stack.push(next)
          i += 1
        }
      }
    }

    false
  }


  def depthFirstSearchExcludeExists(start: Int, successors: NestedArrayInt, exclude:Array[Int], search:Int):Boolean = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = new Array[Int](successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet

    visited(start) = 1
    var i = 0
    val startSuccessorCount = successors(start).length
    while(i < startSuccessorCount) {
      val next = successors(start, i)
      if(exclude(next) != 1) stack.push(next)
      i += 1
    }

    while(!stack.isEmpty) {
      val current = stack.pop()
      if(current == search) return true
      if(visited(current) != 1) {
        visited(current) = 1
        val nexts = successors(current)
        val nextCount = nexts.length
        i = 0
        while(i < nextCount) {
          val next = nexts(i)
          if(visited(next) != 1 && exclude(next) != 1) stack.push(next)
          i += 1
        }
      }
    }

    false
  }

  def depthFirstSearchAfterStarts(start: Array[Int], successors: NestedArrayInt):Array[Int] = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = new Array[Int](successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet
    val result = new mutable.ArrayBuilder.ofInt

    // start is intentionally left out.
    // it is still possible that start is visited later. (in a cycle)
    // result += start
    // visited(start) = 1

    var i = 0
    start.foreachElement { start =>
      val startSuccessorCount = successors(start).length
      while(i < startSuccessorCount) {
        stack.push(successors(start, i))
        i += 1
      }
    }

    while(!stack.isEmpty) {
      val current = stack.pop()
      if(visited(current) != 1) {
        result += current
        visited(current) = 1
        val nexts = successors(current)
        val nextCount = nexts.length
        i = 0
        while(i < nextCount) {
          val next = nexts(i)
          if(visited(next) != 1) stack.push(next)
          i += 1
        }
      }
    }


    result.result()
  }

  def depthFirstSearchAfterStart(start: Int, successors: NestedArrayInt):Array[Int] = {
    val stack = ArrayStackInt.create(capacity = successors.size)
    val visited = new Array[Int](successors.size) // JS: Array[Int] faster than Array[Boolean] and BitSet
    val result = new mutable.ArrayBuilder.ofInt

    // start is intentionally left out.
    // it is still possible that start is visited later. (in a cycle)
    // result += start
    // visited(start) = 1
    
    var i = 0
    val startSuccessorCount = successors(start).length
    while(i < startSuccessorCount) {
      stack.push(successors(start, i))
      i += 1
    }

    while(!stack.isEmpty) {
      val current = stack.pop()
      if(visited(current) != 1) {
        result += current
        visited(current) = 1
        val nexts = successors(current)
        val nextCount = nexts.length
        i = 0
        while(i < nextCount) {
          val next = nexts(i)
          if(visited(next) != 1) stack.push(next)
          i += 1
        }
      }
    }


    result.result()
  }

  @deprecated("This is the old, slow version of DFS","")
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

  def dijkstra[V](edges: V => Iterable[V], source: V): (Map[V, Int], Map[V, V]) = {
    def run(active: Set[V], res: Map[V, Int], pred: Map[V, V]): (Map[V, Int], Map[V, V]) =
      if (active.isEmpty) (res, pred)
      else {
        val node = active.minBy(res)
        val cost = res(node)
        val neighbours = (for {
          n <- edges(node) if cost + 1 < res.getOrElse(n, Int.MaxValue)
        } yield n -> (cost + 1)).toMap
        val active1 = active - node  ++ neighbours.keys
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
      while(i < vertices.length) {
        a(vertices(i)) = i+1 // offset, so that 0 corresponds to -1, this saves filling the array with -1 first
        i += 1
      }
      a
    }
    @inline def reverseLookup(i:Int) = _reverseLookup(i)-1

    val sorted = new Array[Int](vertexCount) // the result
    var sortCount = 0 // count to fill the result array from back to front
    val visited = ArraySet.create(vertexCount)

    @inline def addToSorted(vertex:Int):Unit = {
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
      if(visited.containsNot(i)) visit(i)
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

    @inline def add(vertex:Int):Unit = {
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
      if(visited(i) == 0) visit(i)
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

}
