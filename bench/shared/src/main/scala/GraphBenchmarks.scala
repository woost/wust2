package bench

import scala.concurrent.duration._
import Util._
import wust.util.{NestedArrayInt, algorithm}

object GraphBenchmarks {
  val algorithms = Comparison("Algorithms", {
    import wust.graph._
    import wust.ids._
    def grid(size: Int):NestedArrayInt = {
      val n = Math.sqrt(size).floor.toInt
      NestedArrayInt(Array.tabulate(size){i =>
        Array(i-1).filter(x => x >= (i/n)*n) ++
          Array(i+1).filter(x => x <= ((i/n)*n+n-1) && x < size) ++
          Array(i-n).filter(x => x >= 0) ++
          Array(i+n).filter(x => x < size)
      })
    }

    Seq(
      Benchmark[NestedArrayInt]("dfs grid",
        { size =>
          grid(size)
        },
        (successors, iterations) =>
          loop(iterations) {
            algorithm.depthFirstSearch(0, successors)
          }
      ),
      Benchmark[(Array[Int],NestedArrayInt)]("toposort grid",
        { size =>
          ((0 until size).toArray,grid(size))
        }, {
        case ((vertices,successors), iterations) =>
          loop(iterations) {
            algorithm.topologicalSortForward(vertices, successors)
          }
        }
      ),
    )
  })
  val graphAlgorithms = Comparison("Graph Algorithms", {
    import wust.graph._
    import wust.ids._
    def randomGraph(size:Int, d:Double) = {
      val nodes = List.fill(size)(Node.Content(NodeData.PlainText(""), NodeRole.default))
      val edges = for( a <- nodes; b <- nodes if rDouble <= d ) yield Edge.Parent(a.id, b.id)
      Graph(nodes, edges)
    }

    def randomChannelGraph(size:Int, d:Double):(Graph, Node.User) = {
      val graph = randomGraph(size, d)
      val userNode = Node.User(UserId.fresh, NodeData.User("harals", true, 1), NodeMeta.User)
      val edges = graph.nodes.map(n => Edge.Pinned(userNode.id, n.id))
      (graph addEdges edges, userNode)
    }

    Seq(
      Benchmark[Graph]("parents w/o edges",
      { size =>
        val nodes = List.fill(size)(Node.Content(NodeData.PlainText(""), NodeRole.default))
        Graph(nodes)
      },
      (graph, _) =>
        graph.parents(graph.nodes.head.id)
      ),
      Benchmark[Graph]("parents path",
      { size =>
        val nodes = List.fill(size)(Node.Content(NodeData.PlainText(""), NodeRole.default))
        val edges = nodes.zip(nodes.tail).map {case (a,b) => Edge.Parent(a.id, EdgeData.Parent, b.id)}
        Graph(nodes, edges)
      },
      (graph, _) =>
        graph.parents(graph.nodes.head.id)
      ),
      Benchmark[Graph]("parents rand(0.05)",
      { size =>
        randomGraph(size, 0.05)
      },
      (graph, _) =>
        graph.parents(graph.nodes.head.id)
      ),
      Benchmark[(Graph,Node)]("channelTree rand(0.05)",
      { size =>
        randomChannelGraph(size, 0.05)
      },
      { case ((graph, userNode: Node.User), _) =>
        graph.channelTree(userNode.id)
      }
      ),
    )
  })
}
