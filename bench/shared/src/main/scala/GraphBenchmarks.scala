package wust.bench

import scala.concurrent.duration._
import bench._
import bench.util._
import wust.util.algorithm
import flatland.NestedArrayInt

object GraphBenchmarks {

  val topologicalSort = Comparison("Topological Sort", {
    import wust.graph._
    import wust.ids._
    def grid(size: Int): NestedArrayInt = {
      val n = Math.sqrt(size).floor.toInt
      NestedArrayInt(Array.tabulate(size){ i =>
        Array(i - 1).filter(x => x >= (i / n) * n) ++
          Array(i + 1).filter(x => x <= ((i / n) * n + n - 1) && x < size) ++
          Array(i - n).filter(x => x >= 0) ++
          Array(i + n).filter(x => x < size)
      })
    }

    Seq(
      Benchmark[(Array[Int], NestedArrayInt)](
        "toposort grid",
        { size =>
          ((0 until size).toArray, grid(size))
        }, {
          case ((vertices, successors)) =>
            algorithm.topologicalSortForward(vertices, successors)
        }
      ),
    )
  })

  val graphAlgorithms = Comparison("Graph Algorithms", {
    import wust.graph._
    import wust.ids._
    def randomGraph(size: Int, d: Double) = {
      val nodes = List.fill(size)(Node.Content(NodeData.PlainText(""), NodeRole.default))
      val edges = for (a <- nodes; b <- nodes if rDouble <= d) yield Edge.Child(ParentId(b.id), ChildId(a.id))
      Graph.from(nodes, edges)
    }

    def randomChannelGraph(size: Int, d: Double): (Graph, Node.User) = {
      val graph = randomGraph(size, d)
      val userNode = Node.User(UserId.fresh, NodeData.User("harals", true, 1), NodeMeta.User)
      val edges = graph.nodes.map(n => Edge.Pinned(n.id, userNode.id): Edge)
      (graph applyChanges GraphChanges(addEdges = edges), userNode)
    }

    Seq(
      Benchmark[Graph](
        "parents w/o edges",
        { size =>
          val nodes = List.fill(size)(Node.Content(NodeData.PlainText(""), NodeRole.default))
          Graph.from(nodes)
        },
        (graph) =>
          graph.parents(graph.nodes.head.id)
      ),
      Benchmark[Graph](
        "parents path",
        { size =>
          val nodes = List.fill(size)(Node.Content(NodeData.PlainText(""), NodeRole.default))
          val edges = nodes.zip(nodes.tail).map { case (a, b) => Edge.Child(ParentId(b.id), ChildId(a.id)) }
          Graph.from(nodes, edges)
        },
        (graph) =>
          graph.parents(graph.nodes.head.id)
      ),
      Benchmark[Graph](
        "parents rand(0.05)",
        { size =>
          randomGraph(size, 0.05)
        },
        (graph) =>
          graph.parents(graph.nodes.head.id)
      ),
    )
  })
}
