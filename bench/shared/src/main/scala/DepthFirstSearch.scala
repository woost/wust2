package wust.bench

import scala.concurrent.duration._
import bench._
import bench.util._
import flatland._
import collection.mutable
import scala.reflect.ClassTag

@inline final class NestedArrayIntFlat(val data: Array[Int]) extends IndexedSeq[ArraySliceInt] {
  @inline def length: Int = data(data.length - 1)
  @inline override def size: Int = length
  @inline override def isEmpty: Boolean = length == 0
  @inline override def nonEmpty: Boolean = length != 0

  @inline def sliceStart(idx: Int): Int = data(idx)
  @inline def sliceLength(idx: Int): Int = data(sliceStart(idx))
  @inline def sliceIsEmpty(idx: Int): Boolean = sliceLength(idx) == 0
  @inline def sliceNonEmpty(idx: Int): Boolean = sliceLength(idx) > 0
  @inline private def dataIndex(idx1: Int, idx2: Int): Int = sliceStart(idx1) + idx2 + 1

  @inline def anyContains(elem: Int) = data.contains(elem)

  @inline def apply(idx: Int): ArraySliceInt = new ArraySliceInt(data, sliceStart(idx) + 1, sliceLength(idx))
  @inline def safe(idx: Int): ArraySliceInt = {
    if (idx < 0 || length <= idx) new ArraySliceInt(data, 0, 0)
    else apply(idx)
  }
  @inline def apply(idx1: Int, idx2: Int): Int = data(dataIndex(idx1, idx2))
  @inline def get(idx1: Int, idx2: Int): Option[Int] = {
    if (0 <= idx1 && idx1 < length && 0 <= idx2 && idx2 < sliceLength(idx1))
      Some(apply(idx1, idx2))
    else None
  }
  @inline def update(idx1: Int, idx2: Int, newValue: Int): Unit = data(dataIndex(idx1, idx2)) = newValue
  @inline def foreachIndex(idx: Int)(f: (Int) => Unit): Unit = {
    loop(sliceLength(idx))(i => f(i))
  }
  @inline def foreachElement(idx: Int)(f: Int => Unit): Unit = {
    foreachIndex(idx) { i =>
      f(apply(idx, i))
    }
  }
  @inline def foreachIndexAndElement(idx: Int)(f: (Int, Int) => Unit): Unit = {
    foreachIndex(idx) { i =>
      f(i, apply(idx, i))
    }
  }
  @inline def foreachIndexAndSlice(f: (Int, ArraySliceInt) => Unit): Unit = {
    loop(length) { i =>
      f(i, apply(i))
    }
  }
  @inline def foreachSliceAndElement(idxArray: Array[Int])(f: Int => Unit): Unit = {
    idxArray.foreachElement { foreachElement(_)(f) }
  }
  @inline def forall(idx: Int)(f: Int => Boolean): Boolean = {
    var i = 0
    val n = sliceLength(idx)
    var all = true
    while (all && i < n) {
      if (!f(apply(idx, i))) all = false
      i += 1
    }
    all
  }

  @inline def exists(idx: Int)(f: Int => Boolean): Boolean = {
    var i = 0
    val n = sliceLength(idx)
    var notExists = true
    while (notExists && i < n) {
      if (f(apply(idx, i))) notExists = false
      i += 1
    }
    !notExists
  }

  @inline def contains(idx: Int)(elem: Int): Boolean = {
    exists(idx)(_ == elem)
  }

  @inline def collectFirst[T](idx: Int)(f: PartialFunction[Int, T]): Option[T] = {
    var i = 0
    val n = sliceLength(idx)
    val safef: PartialFunction[Int, Unit] = f.andThen { t => return Some(t) }
    while (i < n) {
      safef.applyOrElse(apply(idx, i), (_: Int) => ())
      i += 1
    }
    None
  }

  @inline def count(idx: Int)(f: Int => Boolean): Int = {
    var counter = 0
    foreachElement(idx) { elem =>
      if (f(elem)) counter += 1
    }
    counter
  }

  @inline def foldLeft[T](idx: Int)(neutral: T)(f: (T, Int) => T): T = {
    var agg: T = neutral
    foreachElement(idx) { elem =>
      agg = f(agg, elem)
    }
    agg
  }

  @inline def minByInt(idx: Int)(initMin: Int)(f: Int => Int): Int = {
    var min = initMin
    foreachElement(idx) { elem =>
      val current = f(elem)
      if (current < min) min = current
    }
    min
  }

  @inline def maxByInt(idx: Int)(initMax: Int)(f: Int => Int): Int = {
    var max = initMax
    foreachElement(idx) { elem =>
      val current = f(elem)
      if (current > max) max = current
    }
    max
  }

  @inline def flatMap[T](idx: Int)(f: Int => Array[T])(implicit classTag: ClassTag[T]): Array[T] = {
    val result = Array.newBuilder[T]
    foreachElement(idx){ elem =>
      result ++= f(elem)
    }
    result.result
  }

  @inline def map[T](idx: Int)(f: Int => T)(implicit classTag: ClassTag[T]): Array[T] = {
    val n = sliceLength(idx)
    val result = new Array[T](n)
    foreachIndexAndElement(idx){ (i, elem) =>
      result(i) = f(elem)
    }
    result
  }

  @inline def collect[T](idx: Int)(f: PartialFunction[Int, T])(implicit classTag: ClassTag[T]): Array[T] = {
    val result = Array.newBuilder[T]
    val safef: PartialFunction[Int, Unit] = f.andThen { t => result += t }
    foreachIndexAndElement(idx){ (i, elem) =>
      safef.applyOrElse(elem, (_: Int) => ())
    }
    result.result
  }

  @inline def toArraySet(idx: Int): ArraySet = {
    val arraySet = ArraySet.create(this.length)
    foreachElement(idx)(arraySet.add)
    arraySet
  }

  // def transposed: NestedArrayInt = {
  //   val counts = new Array[Int](length)
  //   data.foreachElement(counts(_) += 1)
  //   val builder = NestedArrayInt.builder(counts)
  //   loop(length) { i =>
  //     foreachElement(i) { builder.add(_, i) }
  //   }
  //   builder.result()
  // }
}

object NestedArrayIntFlat {
  def apply(nested: Array[Array[Int]]): NestedArrayIntFlat = {
    var currentStart = nested.length
    nested.foreachElement { slice =>
      currentStart += (slice.length + 1)
    }

    val data = new Array[Int](currentStart + 1) // 1 is for number of nested arrays
    currentStart = nested.length
    nested.foreachIndexAndElement { (i, slice) =>
      val start = currentStart
      data(i) = start
      data(start) = nested(i).length
      slice.copyToArray(data, start + 1)
      currentStart += (slice.length + 1)
    }
    data(data.length - 1) = nested.length

    new NestedArrayIntFlat(data)
  }
}

object DepthFirstSearch {

  val comparison = Comparison("Depth First search (Grid Graph)", {
    import wust.graph._
    import wust.ids._
    def generateLatticeGraph(size: Int): NestedArrayInt = {
      val n = Math.sqrt(size).floor.toInt
      NestedArrayInt(Array.tabulate(size){ i =>
        Array(i - 1).filter(x => x >= (i / n) * n) ++
          Array(i + 1).filter(x => x <= ((i / n) * n + n - 1) && x < size) ++
          Array(i - n).filter(x => x >= 0) ++
          Array(i + n).filter(x => x < size)
      })
    }
    def generateLatticeGraphFlat(size: Int): NestedArrayIntFlat = {
      val n = Math.sqrt(size).floor.toInt
      NestedArrayIntFlat.apply(Array.tabulate(size){ i =>
        Array(i - 1).filter(x => x >= (i / n) * n) ++
          Array(i + 1).filter(x => x <= ((i / n) * n + n - 1) && x < size) ++
          Array(i - n).filter(x => x >= 0) ++
          Array(i + n).filter(x => x < size)
      })
    }

    case class Vertex(id: String, name: String)
    case class Edge(source: String, target: String)
    case class Graph(vertices: Array[Vertex], edges: Array[Edge])
    def generateLatticeGraphMap(size: Int): mutable.HashMap[String, Array[Vertex]] = {
      val flat = generateLatticeGraph(size)
      val vertices: Array[Vertex] = Array.tabulate(flat.size)(i => Vertex(i.toString, s"$i name"))
      val map = mutable.HashMap.empty[String, Array[Vertex]]
      map ++= flat.zipWithIndex.map{ case (successors, i) => vertices(i).id -> successors.map(vertices).toArray }
      map
    }

    Seq(
      // {
      //   def depthFirstSearch(start: String, successors: mutable.HashMap[String, Array[Vertex]]): Array[String] = {
      //     val stack = new mutable.Stack[String]
      //     val visited = new mutable.HashSet[String]
      //     val result = new mutable.ArrayBuilder.ofRef[String]
      //     @inline def stackPush(elem: String): Unit = {
      //       stack.push(elem)
      //       visited += elem
      //     }

      //     stackPush(start)

      //     while (!stack.isEmpty) {
      //       val current = stack.pop()

      //       result += current
      //       visited += current
      //       successors(current).foreach { next =>
      //         if (!visited.contains(next.id)) {
      //           stackPush(next.id)
      //         }
      //       }
      //     }

      //     result.result()
      //   }

      //   BenchmarkImmutableInit[mutable.HashMap[String, Array[Vertex]]](
      //     "hashmap",
      //     { size =>
      //       generateLatticeGraphMap(size)
      //     },
      //     { (successors) =>
      //       depthFirstSearch("0", successors)
      //     }
      //   )
      // },
      {
        def depthFirstSearch(start: Int, successors: NestedArrayInt): Array[Int] = {
          val stack = ArrayStackInt.create(capacity = successors.size)
          val visited = ArraySet.create(successors.size)
          val result = new mutable.ArrayBuilder.ofInt
          @inline def stackPush(elem: Int): Unit = {
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

        BenchmarkImmutableInit[NestedArrayInt](
          "result builder (master)",
          { size =>
            generateLatticeGraph(size)
          },
          { (successors) =>
            depthFirstSearch(0, successors)
          }
        )
      },
      {
        def depthFirstSearch(start: Int, successors: NestedArrayIntFlat): Array[Int] = {
          val stack = ArrayStackInt.create(capacity = successors.size)
          val visited = ArraySet.create(successors.size)
          val result = new mutable.ArrayBuilder.ofInt
          result.sizeHint(successors.size)
          @inline def stackPush(elem: Int): Unit = {
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

        BenchmarkImmutableInit[NestedArrayIntFlat](
          "sizehint+flat",
          { size =>
            generateLatticeGraphFlat(size)
          },
          { (successors) =>
            depthFirstSearch(0, successors)
          }
        )
      },
      {
        def depthFirstSearch(start: Int, successors: NestedArrayIntFlat): Array[Int] = {
          val stack = ArrayStackInt.create(capacity = successors.size)
          val visited = ArraySet.create(successors.size)
          val result = new mutable.ArrayBuilder.ofInt
          result.sizeHint(successors.size)
          @inline def stackPush(elem: Int): Unit = {
            stack.push(elem)
            visited += elem
          }

          // this part could also just be:
          stackPush(start)
          // but this one is faster, since it allows the first
          // step with fewer checks.
          // result += start
          // visited += start
          // successors.foreachElement(start)(stackPush)

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

        BenchmarkImmutableInit[NestedArrayIntFlat](
          "sizehint+flat+stackstart",
          { size =>
            generateLatticeGraphFlat(size)
          },
          { (successors) =>
            depthFirstSearch(0, successors)
          }
        )
      },
      // {
      //   def depthFirstSearch(start: Int, successors: NestedArrayInt): Array[Int] = {
      //     val stack = ArrayStackInt.create(capacity = successors.size)
      //     val visited = ArraySet.create(successors.size)
      //     val result = new mutable.ArrayBuilder.ofInt
      //     result.sizeHint(successors.size)
      //     @inline def stackPush(elem: Int): Unit = {
      //       stack.push(elem)
      //       visited += elem
      //     }

      //     // this part could also just be:
      //     // stackPush(start)
      //     // but this one is faster, since it allows the first
      //     // step with fewer checks.
      //     result += start
      //     visited += start
      //     successors.foreachElement(start)(stackPush)

      //     while (!stack.isEmpty) {
      //       val current = stack.pop()

      //       result += current
      //       visited += current
      //       successors.foreachElement(current) { next =>
      //         if (visited.containsNot(next)) {
      //           stackPush(next)
      //         }
      //       }
      //     }

      //     result.result()
      //   }

      //   BenchmarkImmutableInit[NestedArrayInt](
      //     "sizehint",
      //     { size =>
      //       generateLatticeGraph(size)
      //     },
      //     { (successors) =>
      //       depthFirstSearch(0, successors)
      //     }
      //   )
      // },
      // {
      //   def depthFirstSearch(start: Int, successors: NestedArrayIntFlat): Array[Int] = {
      //     val stack = ArrayStackInt.create(capacity = successors.size)
      //     val visited = ArraySet.create(successors.size)
      //     val result = new mutable.ArrayBuilder.ofInt
      //     @inline def stackPush(elem: Int): Unit = {
      //       stack.push(elem)
      //       visited += elem
      //     }

      //     // this part could also just be:
      //     // stackPush(start)
      //     // but this one is faster, since it allows the first
      //     // step with fewer checks.

      //     result += start
      //     visited += start
      //     successors.foreachElement(start){ next =>
      //       stackPush(next)
      //     }

      //     while (!stack.isEmpty) {
      //       val current = stack.pop()

      //       result += current
      //       visited += current
      //       successors.foreachElement(current) { next =>
      //         if (visited.containsNot(next)) {
      //           stackPush(next)
      //         }
      //       }
      //     }

      //     result.result()
      //   }

      //   BenchmarkImmutableInit[NestedArrayIntFlat](
      //     "nestedArrayFlat",
      //     { size =>
      //       generateLatticeGraphFlat(size)
      //     },
      //     { (successors) =>
      //       depthFirstSearch(0, successors)
      //     }
      //   )
      // },

      // {
      //   def depthFirstSearch(start: Int, successors: NestedArrayIntFlat): Array[Int] = {
      //     val stack = ArrayStackInt.create(capacity = successors.size)
      //     var visited = ArraySet.create(successors.size)
      //     var resultSize = 0
      //     @inline def stackPush(elem: Int): Unit = {
      //       stack.push(elem)
      //       visited += elem
      //     }

      //     // this part could also just be:
      //     // stackPush(start)
      //     // but this one is faster, since it allows the first
      //     // step with fewer checks.
      //     resultSize += 1
      //     visited += start
      //     successors.foreachElement(start)(stackPush)

      //     while (!stack.isEmpty) {
      //       val current = stack.pop()

      //       resultSize += 1
      //       visited += current
      //       successors.foreachElement(current) { next =>
      //         if (visited.containsNot(next)) {
      //           stackPush(next)
      //         }
      //       }
      //     }

      //     visited.clear()
      //     val result = new Array[Int](resultSize)
      //     var resultPos = 0
      //     @inline def resultAdd(elem: Int): Unit = {
      //       result(resultPos) = elem
      //       resultPos += 1
      //     }

      //     resultAdd(start)
      //     visited += start
      //     successors.foreachElement(start)(stackPush)

      //     while (!stack.isEmpty) {
      //       val current = stack.pop()

      //       resultAdd(current)
      //       visited += current
      //       successors.foreachElement(current) { next =>
      //         if (visited.containsNot(next)) {
      //           stackPush(next)
      //         }
      //       }
      //     }

      //     result
      //   }

      //   BenchmarkImmutableInit[NestedArrayIntFlat](
      //     "iterate twice+flat",
      //     { size =>
      //       generateLatticeGraphFlat(size)
      //     },
      //     { (successors) =>
      //       depthFirstSearch(0, successors)
      //     }
      //   )
      // },
      // {
      //   def depthFirstSearch(start: Int, successors: NestedArrayInt): Array[Int] = {
      //     val stack = ArrayStackInt.create(capacity = successors.size)
      //     var visited = ArraySet.create(successors.size)
      //     var resultSize = 0
      //     @inline def stackPush(elem: Int): Unit = {
      //       stack.push(elem)
      //       visited += elem
      //     }

      //     // this part could also just be:
      //     // stackPush(start)
      //     // but this one is faster, since it allows the first
      //     // step with fewer checks.
      //     resultSize += 1
      //     visited += start
      //     successors.foreachElement(start)(stackPush)

      //     while (!stack.isEmpty) {
      //       val current = stack.pop()

      //       resultSize += 1
      //       visited += current
      //       successors.foreachElement(current) { next =>
      //         if (visited.containsNot(next)) {
      //           stackPush(next)
      //         }
      //       }
      //     }

      //     visited.clear()
      //     val result = new Array[Int](resultSize)
      //     var resultPos = 0
      //     @inline def resultAdd(elem: Int): Unit = {
      //       result(resultPos) = elem
      //       resultPos += 1
      //     }

      //     resultAdd(start)
      //     visited += start
      //     successors.foreachElement(start)(stackPush)

      //     while (!stack.isEmpty) {
      //       val current = stack.pop()

      //       resultAdd(current)
      //       visited += current
      //       successors.foreachElement(current) { next =>
      //         if (visited.containsNot(next)) {
      //           stackPush(next)
      //         }
      //       }
      //     }

      //     result
      //   }

      //   BenchmarkImmutableInit[NestedArrayInt](
      //     "iterate twice",
      //     { size =>
      //       generateLatticeGraph(size)
      //     },
      //     { (successors) =>
      //       depthFirstSearch(0, successors)
      //     }
      //   )
      // }
    )
  })

}
