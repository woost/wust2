package wust.util

import flatland._

import scala.collection.mutable

package object algorithm {

  def containsCycle(elements: Array[Int], successors: NestedArrayInt): Boolean = {
    val num = elements.length
    var i = 0

    while (i < num) {
      val idx = elements(i)
      if (dfs.exists(_(idx), dfs.afterStart, successors, isFound =  _ == idx))
        return true

      i += 1
    }
    false
  }

  def containmentsOfCycle(elements: Array[Int], successors: NestedArrayInt): Array[Int] = {
    val builder = new mutable.ArrayBuilder.ofInt
    elements.foreachElement { idx =>
      if (dfs.exists(_(idx), dfs.afterStart, successors, isFound = _ == idx))
        builder += idx
    }
    builder.result()
  }


  def shortestPathsIdx(next: NestedArrayInt) = {
    val n = next.length
    val memoizedLengths = Array.fill[Int](n)(-1)
    val visited = ArraySet.create(n) // to handle cycles

    def searchShortestPath(idx: Int): Int = {
      val shortestPath = memoizedLengths(idx)
      if (shortestPath == -1) {
        if (!visited(idx)) {
          visited += idx

          val d = if (next.sliceIsEmpty(idx)) 0
          else (next.minByInt(idx)(initMin = n)(searchShortestPath) + 1)

          memoizedLengths(idx) = d
          d
        } else 0 // cycle
      } else shortestPath
    }

    memoizedLengths.foreachIndexAndElement { (idx, depth) =>
      if (depth == -1) searchShortestPath(idx)
    }
    memoizedLengths
  }

  def longestPathsIdx(next: NestedArrayInt): Array[Int] = {
    val memoizedLengths = Array.fill[Int](next.length)(-1)
    val visited = ArraySet.create(next.length) // to handle cycles

    def searchLongestPath(idx: Int): Int = {
      val longestPath = memoizedLengths(idx)
      if (longestPath == -1) {
        if (!visited(idx)) {
          visited += idx

          val d = if (next.sliceIsEmpty(idx)) 0
          else (next.maxByInt(idx)(initMax = 0)(searchLongestPath) + 1)

          memoizedLengths(idx) = d
          d
        } else 0 // cycle
      } else longestPath
    }

    memoizedLengths.foreachIndexAndElement { (idx, depth) =>
      if (depth == -1) searchLongestPath(idx)
    }
    memoizedLengths
  }

  @deprecated("", "this is old and slow")
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

  @deprecated("", "this is old and slow")
  def connectedComponents[V](vertices: Iterable[V], continue: V => Iterable[V]): List[Set[V]] = {
    val left = mutable.HashSet.empty ++ vertices
    var components: List[Set[V]] = Nil
    while (left.nonEmpty) {
      val next = left.head
      val component = dfs.withStartInCycleDetection(next, continue).toSet
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

    val vertexCount = vertices.length // the number of vertices to sort

    val sorted = new Array[Int](vertexCount) // the result
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

  def eulerDiagramDualGraph(parents: NestedArrayInt, children: NestedArrayInt, isEulerSet: Int => Boolean): (Array[Set[Int]], NestedArrayInt, InterleavedArrayInt) = {

    // for each node, the set of parent nodes identifies its zone
    val zoneGrouping: Seq[(Set[Int], Seq[Int])] = parents.indices
      .groupBy{ nodeIdx =>
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
    val eulerZones: Array[Set[Int]] = zoneGrouping.iterator.map(_._1)(breakOut)
    val eulerZoneNodes: Array[Array[Int]] = zoneGrouping.iterator.map(_._2.toArray)(breakOut)

    def setDifference[T](a: Set[T], b: Set[T]) = (a union b) diff (a intersect b)
    val neighbourhoodBuilder = mutable.ArrayBuilder.make[(Int, Int)]

    eulerZones.foreachIndex2Combination { (zoneAIdx, zoneBIdx) =>
      // Two zones in an euler diagram are separated by a line iff their parentSet definitions differ by exactly one element
      @inline def zonesDifferByExactlyOneElement = setDifference(eulerZones(zoneAIdx), eulerZones(zoneBIdx)).size == 1
      @inline def zonesAreNotEmptyZone = eulerZones(zoneAIdx).nonEmpty && eulerZones(zoneBIdx).nonEmpty
      if (zonesAreNotEmptyZone && zonesDifferByExactlyOneElement) {
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
