package wust.bench

import scala.concurrent.duration._
import bench._
import bench.util._
import flatland.{ ArrayStackInt, NestedArrayInt, ArraySet }
import collection.mutable

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
      {
        def depthFirstSearch(start: String, successors: mutable.HashMap[String, Array[Vertex]]): Array[String] = {
          val stack = new mutable.Stack[String]
          val visited = new mutable.HashSet[String]
          val result = new mutable.ArrayBuilder.ofRef[String]
          @inline def stackPush(elem: String): Unit = {
            stack.push(elem)
            visited += elem
          }

          stackPush(start)

          while (!stack.isEmpty) {
            val current = stack.pop()

            result += current
            visited += current
            successors(current).foreach { next =>
              if (!visited.contains(next.id)) {
                stackPush(next.id)
              }
            }
          }

          result.result()
        }

        Benchmark[mutable.HashMap[String, Array[Vertex]]](
          "hashmap",
          { size =>
            generateLatticeGraphMap(size)
          },
          { (successors) =>
            depthFirstSearch("0", successors)
          }
        )
      },
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

        Benchmark[NestedArrayInt](
          "result builder",
          { size =>
            generateLatticeGraph(size)
          },
          { (successors) =>
            depthFirstSearch(0, successors)
          }
        )
      },

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

      //   Benchmark[NestedArrayInt](
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
