package wust.util

object algorithm {
  import scala.collection.{IterableLike, breakOut, mutable}
  import math.Ordering

  def defaultNeighbourhood[V, T](vertices: Iterable[V], default: T): scala.collection.Map[V, T] = {
    val map = mutable.HashMap[V, T]().withDefaultValue(default)
    map.sizeHint(vertices.size)
    vertices.foreach { v =>
      map(v) = default
    }
    map
  }

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

  def adjacencyList[V, E](
      edges: Iterable[E],
      inf: E => V,
      outf: E => V
  ): scala.collection.Map[V, scala.collection.Set[V]] = { // TODO: Multimap
    val map =
      mutable.HashMap[V, scala.collection.Set[V]]().withDefaultValue(mutable.HashSet.empty[V])
    edges.foreach { e =>
      val in = inf(e)
      val out = outf(e)
      map(in) += out
      map(out) += in
    }
    map
  }

  def degreeSequence[V, E](
      edges: Iterable[E],
      inf: E => V,
      outf: E => V
  ): scala.collection.Map[V, Int] = {
    val map = mutable.HashMap[V, Int]().withDefaultValue(0)
    edges.foreach { e =>
      val in = inf(e)
      val out = outf(e)
      map(in) += 1
      map(out) += 1
    }
    map
  }

  def directedDegreeSequence[V, E](
      edges: Iterable[E],
      inf: E => V
  ): scala.collection.Map[V, Int] = {
    val map = mutable.HashMap[V, Int]().withDefaultValue(0)
    edges.foreach { e =>
      val in = inf(e)
      map(in) += 1
    }
    map
  }

  def directedIncidenceList[V, E](
      edges: Iterable[E],
      inf: E => V
  ): scala.collection.Map[V, scala.collection.Set[E]] = { // TODO: Multimap
    val map =
      mutable.HashMap[V, scala.collection.Set[E]]().withDefaultValue(mutable.HashSet.empty[E])
    edges.foreach { e =>
      val in = inf(e)
      map(in) += e
    }
    map
  }

  def incidenceList[V, E](
      edges: Iterable[E],
      inf: E => V,
      outf: E => V
  ): scala.collection.Map[V, scala.collection.Set[E]] = { // TODO: Multimap
    val map =
      mutable.HashMap[V, scala.collection.Set[E]]().withDefaultValue(mutable.HashSet.empty[E])
    edges.foreach { e =>
      val in = inf(e)
      val out = outf(e)
      map(in) += e
      map(out) += e
    }
    map
  }

  def depthFirstSearch(start: Int, successors: NestedArrayInt):Array[Int] = {
    val stack = new ArrayStackInt(capacity = successors.size)
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


  def depthFirstSearchExists(start: Int, successors: NestedArrayInt, search:Int):Boolean = {
    if(start == search) return true

    val stack = new ArrayStackInt(capacity = successors.size)
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
    val stack = new ArrayStackInt(capacity = successors.size)
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


  def depthFirstSearchWithoutStart(start: Int, successors: NestedArrayInt):Array[Int] = {
    val stack = new ArrayStackInt(capacity = successors.size)
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

    var sorted = new Array[Int](vertexCount) // the result
    var sortCount = 0 // count to fill the result array from back to front
    val visited = new Array[Int](vertexCount)

    @inline def add(vertex:Int):Unit = {
      sorted(vertexCount - sortCount - 1) = vertex
      sortCount += 1
    }


    def visit(idx: Int): Unit = {
      val vertex = vertices(idx)
      visited(idx) = 1
      for (nextIdx <- successors(vertex)) {
        val revIdx = reverseLookup(nextIdx)
        if (revIdx != -1 && visited(revIdx) == 0) {
          visit(revIdx)
        }
      }
      add(vertex)
    }

    var i = vertices.length - 1
    while (i > -1) {
      if(visited(i) == 0) visit(i)
      i -= 1
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
