package wust.graph

import org.scalatest._
import wust.ids._
import wust.util.collection._

class GraphSpec extends FreeSpec with MustMatchers {
  implicit def intToNodeId(id: Int): NodeId = NodeId(Cuid(id, 0))
  implicit def idToPost(id: Int): Node = Node.Content(id = id, data = NodeData.PlainText("content"))
  implicit def postListToMap(posts: List[Int]): List[Node] = posts.map(idToPost)
  implicit def tupleIsConnection(t: (Int, Int)): Edge = Connection(t._1, t._2)
  implicit def connectionListIsMap(connections: List[(Int, Int)]): List[Edge] = connections.map(tupleIsConnection)

  implicit class ContainmentBuilder(parentId: Int) {
    def cont(childId: Int) = Containment(parentId, childId);
  }
  def Connection(sourceId: NodeId, targetId: NodeId) = wust.graph.Edge.Label(sourceId, EdgeData.Label("connector"), targetId)
  def Containment(parentId: NodeId, childId: NodeId) = wust.graph.Edge.Parent(childId, parentId)

  "graph" - {
    "empty is empty" in {
      Graph.empty.nodesById mustBe empty
      Graph.empty.labeledEdges mustBe empty

      Graph.empty.nodes mustBe empty

      Graph().nodesById mustBe empty
      Graph().labeledEdges mustBe empty

      Graph().nodes mustBe empty
    }

    "directed cycle" in {
      val graph = Graph(
        nodes = List(1, 11, 12),
        edges = List(Containment(1, 11), Containment(11, 12), Containment(12, 1))
      )

      graph.involvedInContainmentCycle(1) mustEqual true
    }

    "one contain" in {
      val graph = Graph(List(1, 11), List(Containment(11, 1)))
      graph.involvedInContainmentCycle(1) mustEqual false
    }

    "have transitive parents in cycle" in {
      val graph = Graph(List(1, 2, 3), List(1 cont 2, 2 cont 3, 3 cont 1))
      graph.ancestors(3).toSet mustEqual Set[NodeId](3, 2, 1)
    }

    "have transitive children in cycle" in {
      val graph = Graph(List(1, 2, 3), List(1 cont 2, 2 cont 3, 3 cont 1))
      graph.descendants(3).toSet mustEqual Set[NodeId](3, 2, 1)
    }

    "consistent on consistent graph" in {
      val graph = Graph(List(1, 2, 3), List[Edge](1 -> 2, 2 -> 3, 3 -> 1) ++ List[Edge](1 cont 2, 2 cont 3, 3 cont 1))
      graph.consistent mustEqual graph
    }

    "consistent on inconsistent graph" in {
      val connections: List[Edge] = List(1 -> 2, 2 -> 3)
      val newConnection = Connection(2, 1)
      val containments: List[Edge] = List(1 cont 2, 2 cont 3)
      val graph = Graph(
        List(1, 2, 3),
        connections ++ containments :+ newConnection :+ Containment(3, 5)
      )
      graph.consistent mustEqual Graph(List(1, 2, 3), connections ++ containments :+ newConnection)
    }

    "consistent on inconsistent graph (reverse)" in {
      val connections: List[Edge] = List(1 -> 2, 2 -> 3)
      val containments: List[Edge] = List(1 cont 2, 2 cont 3)
      val graph = Graph(
        List(1, 2, 3),
        connections ++ Seq(Connection(4, 1), Connection(1, 100)) ++ containments :+ Containment(3, 5)
      )
      graph.consistent mustEqual Graph(List(1, 2, 3), connections ++ containments)
    }

    "add post" in {
      val posts: List[Node] = List(1, 2, 3)
      val newPost: Node = 99
      val graph = Graph(posts, List[Edge](1 -> 2, 2 -> 3) ++ List(1 cont 2, 2 cont 3))
      (graph + newPost) mustEqual Graph(posts :+ newPost, graph.labeledEdges ++ graph.containments)
    }

    "add connection" in {
      val connections: List[Edge] = List(1 -> 2, 2 -> 3)
      val newConnection = Connection(3, 1)
      val graph = Graph(List(1, 2, 3), connections ++ List[Edge](1 -> 2, 2 -> 3))
      (graph + newConnection) mustEqual Graph(graph.nodes, connections ++ graph.containments :+ newConnection)
    }

    "add containment" in {
      val containments: List[Edge] = List(1 -> 2, 2 -> 3)
      val newContainment = Containment(3, 1)
      val graph = Graph(List(1, 2, 3), List[Edge](1 -> 2, 2 -> 3) ++ containments)
      (graph + newContainment) mustEqual Graph(graph.nodes, graph.labeledEdges ++ (containments :+ newContainment))
    }

    "filter" in {
      val connections: List[Edge] = List(1 -> 2, 2 -> 3)
      val containments: List[Edge] = List(1 cont 2, 2 cont 3)
      val graph = Graph(List(1, 2, 3), connections ++ containments)
      (graph filter Set[NodeId](1)) mustEqual Graph(List(1))
      (graph filter Set[NodeId](1, 2)) mustEqual Graph(List(1, 2), List(Connection(1, 2)) ++ List(Containment(1, 2)))
    }

    "remove post" in {
      val connections: List[Edge] = List(Connection(1, 2), Connection(2, 3))
      val containments: List[Edge] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connections ++ containments)
      (graph - (1 : NodeId)) mustEqual Graph(List(2, 3), List(Connection(2, 3)) ++ List(Containment(2, 3)))
    }

    "remove non-existing post" in {
      val connections: List[Edge] = List(Connection(1, 2), Connection(2, 3))
      val containments: List[Edge] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connections ++ containments)
      (graph - (4 : NodeId)) mustEqual graph
    }

    "remove one post with removePosts" in {
      val connections: List[Edge] = List(Connection(1, 2), Connection(2, 3))
      val containments: List[Edge] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connections ++ containments)
      (graph removeNodes Seq[NodeId](1)) mustEqual Graph(List(2, 3), List(Connection(2, 3)) ++ List(Containment(2, 3)))
    }

    "remove multiple posts" in {
      val connections: List[Edge] = List(Connection(1, 2), Connection(2, 3))
      val containments: List[Edge] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3, 4), connections ++ containments)
      (graph removeNodes Seq[NodeId](1, 4)) mustEqual Graph(List(2, 3), List(Connection(2, 3)) ++  List(Containment(2, 3)))
    }

    "remove connection" in {
      val connections: List[Edge] = List(Connection(1, 2), Connection(2, 3))
      val containments: List[Edge] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connections ++ containments)
      (graph - Connection(2, 3)) mustEqual Graph(graph.nodes, List(Connection(1, 2)) ++ graph.containments)
    }

    "remove non-existing connection" in {
      val connections: List[Edge] = List(Connection(1, 2), Connection(2, 3))
      val containments: List[Edge] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connections ++ containments)
      (graph - Connection(5, 7)) mustEqual graph
    }

    "remove containment" in {
      val connections: List[Edge] = List(Connection(1, 2), Connection(2, 3))
      val containments: List[Edge] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connections ++ containments)
      (graph - Containment(2, 3)) mustEqual Graph(graph.nodes, graph.labeledEdges ++ List(Containment(1, 2)))
    }

    "remove non-existing containment" in {
      val connection: List[Edge] = List(Connection(1, 2), Connection(2, 3))
      val containment: List[Edge] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connection ++ containment)
      (graph - Containment(17, 18)) mustEqual graph
    }

    "successors of post" in {
      val graph = Graph(
        nodes = List(1, 11, 12, 13, 14),
        edges = List(Connection(1, 11), Connection(11, 12), Connection(12, 1), Connection(12, 13)) ++ List(Containment(12, 14))
      )

      graph.nodesById(12)
      graph.nodesById(12)
      graph.successorsWithoutParent(12) mustEqual Set[NodeId](1, 13)
      graph.successorsWithoutParent(13) mustEqual Set.empty
    }

    "predecessors of post" in {
      val graph = Graph(
        nodes = List(1, 11, 12, 13, 14),
        edges = List(Connection(1, 11), Connection(11, 12), Connection(12, 1), Connection(12, 13)) ++ List(Containment(12, 14))
      )

      graph.predecessorsWithoutParent(12) mustEqual Set[NodeId](11)
      graph.predecessorsWithoutParent(13) mustEqual Set[NodeId](12)
    }

    "neighbours of post" in {
      val graph = Graph(
        nodes = List(1, 11, 12, 13, 14),
        edges = List(Connection(1, 11), Connection(11, 12), Connection(12, 1), Connection(12, 13)) ++ List(Containment(12, 14))
      )

      graph.neighboursWithoutParent(12) mustEqual Set[NodeId](1, 11, 13)
      graph.neighboursWithoutParent(13) mustEqual Set[NodeId](12)
    }

    "children of post" in {
      val graph = Graph(
        nodes = List(1, 11, 12, 13, 14),
        edges = List(Connection(1, 14)) ++ List(Containment(1, 11), Containment(1, 12), Containment(13, 12))
      )

      graph.children(1:NodeId) mustEqual Set[NodeId](11, 12)
      graph.children(12:NodeId) mustEqual Set.empty
      graph.children(1:Node) mustEqual Set[NodeId](11, 12)
      graph.children(12:Node) mustEqual Set.empty
    }

    "parents of post" in {
      val graph = Graph(
        nodes = List(1, 11, 12, 13, 14),
        edges = List(Connection(1, 14)) ++ List(Containment(1, 11), Containment(1, 12), Containment(13, 12))
      )

      graph.parents(1:NodeId) mustEqual Set.empty
      graph.parents(12:NodeId) mustEqual Set[NodeId](1, 13)
      graph.parents(1:Node) mustEqual Set.empty
      graph.parents(12:Node) mustEqual Set[NodeId](1, 13)
    }

    "containment neighbours of post" in {
      val graph = Graph(
        nodes = List(1, 11, 12, 13, 14),
        edges = List(Connection(1, 14)) ++ List(Containment(1, 11), Containment(1, 12), Containment(13, 12))
      )

      graph.containmentNeighbours(1) mustEqual Set[NodeId](11, 12)
      graph.containmentNeighbours(12) mustEqual Set[NodeId](1, 13)
    }
  }
}
