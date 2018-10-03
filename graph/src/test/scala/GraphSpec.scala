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

  implicit def stringToCuid(id:String):Cuid = Cuid.fromBase58("5Q4is6Gc5NbA7T7W7PvAUw".dropRight(id.length) + id)
  val channelNode:Node = 0
  def user(id:Cuid) = Node.User(UserId(NodeId(id)), NodeData.User(id.toString, false, 0, channelNode.id), NodeMeta.User)

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

    "add post" in {
      val posts: List[Node] = List(1, 2, 3)
      val newPost: Node = 99
      val graph = Graph(posts, List[Edge](1 -> 2, 2 -> 3) ++ List(1 cont 2, 2 cont 3))
      (graph + newPost) mustEqual Graph(posts :+ newPost, graph.edges)
    }

    "add node with same hashcode (update)" in {
      val oldPost: Node = Node.Content(id = 99, data = NodeData.PlainText("old content"))
      val newPost: Node = Node.Content(id = 99, data = NodeData.PlainText("updated content"))
      val postsWithOld: List[Node] = List(1, 2, 3, oldPost)
      val postsWithNew: List[Node] = List(1, 2, 3, newPost)
      (Graph(postsWithOld) + newPost) mustEqual Graph(postsWithNew)
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
      (graph filterIds Set[NodeId](1)) mustEqual Graph(List(1))
      (graph filterIds Set[NodeId](1, 2)) mustEqual Graph(List(1, 2), List(Connection(1, 2)) ++ List(Containment(1, 2)))
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
    }

    "parents of post" in {
      val graph = Graph(
        nodes = List(1, 11, 12, 13, 14),
        edges = List(Connection(1, 14)) ++ List(Containment(1, 11), Containment(1, 12), Containment(13, 12))
      )

      graph.parents(1:NodeId) mustEqual Set.empty
      graph.parents(12:NodeId) mustEqual Set[NodeId](1, 13)
    }

    "containment neighbours of post" in {
      val graph = Graph(
        nodes = List(1, 11, 12, 13, 14),
        edges = List(Connection(1, 14)) ++ List(Containment(1, 11), Containment(1, 12), Containment(13, 12))
      )

      graph.containmentNeighbours(1) mustEqual Set[NodeId](11, 12)
      graph.containmentNeighbours(12) mustEqual Set[NodeId](1, 13)
    }

    "permissions" - {
      // IMPORTANT:
      // exactly the same test cases as for stored procedure `can_access_node()`
      // when changing things, make sure to change them for the stored procedure as well.
      import wust.ids.NodeAccess.{Level, Inherited}
      import wust.ids.AccessLevel._
      def node(id:Cuid, nodeAccess: NodeAccess) = Node.Content(NodeId(id), NodeData.PlainText(id.toString), NodeMeta(nodeAccess))
      def member(user:Cuid, level:AccessLevel, node:Cuid) = Edge.Member(UserId(NodeId(user)), EdgeData.Member(level), NodeId(node))
      def parent(childId:Cuid, parentId:Cuid) = Edge.Parent(NodeId(childId), NodeId(parentId))
      def access(g:Graph, user:Cuid, node:Cuid):Boolean = g.can_access_node(UserId(NodeId(user)), NodeId(node))

      "simple" - {
        "1" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Level(Restricted))),
            edges = Set(member("A", Restricted, "B"))
          )
          assert(access(g, "A", "B") == false)
        }
        "2" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Level(ReadWrite))),
            edges = Set(member("A", Restricted, "B"))
          )
          assert(access(g, "A", "B") == false)
        }
        "3" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Inherited)),
            edges = Set(member("A", Restricted, "B"))
          )
          assert(access(g, "A", "B") == false)
        }


        "4" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Level(Restricted))),
            edges = Set(member("A", ReadWrite, "B"))
          )
          assert(access(g, "A", "B") == true)
        }
        "5" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Level(ReadWrite))),
            edges = Set(member("A", ReadWrite, "B"))
          )
          assert(access(g, "A", "B") == true)
        }
        "6" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Inherited)),
            edges = Set(member("A", ReadWrite, "B"))
          )
          assert(access(g, "A", "B") == true)
        }


        "7" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Level(Restricted))),
            edges = Set()
          )
          assert(access(g, "A", "B") == false)
        }
        "8" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Level(ReadWrite))),
            edges = Set()
          )
          assert(access(g, "A", "B") == true)
        }
      }

      "simple inheritance" - {
        "1" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Level(Restricted)), node("C", Inherited)),
            edges = Set(member("A", Restricted, "B"), parent("C", "B"))
          )
          assert(access(g, "A", "C") == false)
        }
        "2" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Level(ReadWrite)), node("C", Inherited)),
            edges = Set(member("A", Restricted, "B"), parent("C", "B"))
          )
          assert(access(g, "A", "C") == false)
        }
        "3" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Inherited), node("C", Inherited)),
            edges = Set(member("A", Restricted, "B"), parent("C", "B"))
          )
          assert(access(g, "A", "C") == false)
        }


        "4" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Level(Restricted)), node("C", Inherited)),
            edges = Set(member("A", ReadWrite, "B"), parent("C", "B"))
          )
          assert(access(g, "A", "C") == true)
        }
        "5" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Level(ReadWrite)), node("C", Inherited)),
            edges = Set(member("A", ReadWrite, "B"), parent("C", "B"))
          )
          assert(access(g, "A", "C") == true)
        }
        "6" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Inherited), node("C", Inherited)),
            edges = Set(member("A", ReadWrite, "B"), parent("C", "B"))
          )
          assert(access(g, "A", "C") == true)
        }


        "7" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Level(Restricted)), node("C", Inherited)),
            edges = Set(parent("C", "B"))
          )
          assert(access(g, "A", "C") == false)
        }
        "8" in {
          val g = Graph(
            nodes = Set(user("A"), channelNode, node("B", Level(ReadWrite)), node("C", Inherited)),
            edges = Set(parent("C", "B"))
          )
          assert(access(g, "A", "C") == true)
        }
      }

      "multiple inheritance: max wins" in {
        val g = Graph(
          nodes = Set(
            user("A"), channelNode,
            node("B", Level(Restricted)),
            node("C", Level(ReadWrite)),
            node("D", Inherited)
          ),
          edges = Set(
            parent("D", "B"),
            parent("D", "C")
          )
        )
        assert(access(g, "A", "D") == true)
      }

      "long inheritance chain: readwrite" in {
        val g = Graph(
          nodes = Set(
            user("A"), channelNode,
            node("B", Level(ReadWrite)),
            node("C", Inherited),
            node("D", Inherited)
          ),
          edges = Set(
            parent("C", "B"),
            parent("D", "C")
          )
        )
        assert(access(g, "A", "D") == true)
      }

      "long inheritance chain: restricted" in {
        val g = Graph(
          nodes = Set(
            user("A"), channelNode,
            node("B", Level(Restricted)),
            node("C", Inherited),
            node("D", Inherited)
          ),
          edges = Set(
            parent("C", "B"),
            parent("D", "C")
          )
        )
        assert(access(g, "A", "D") == false)
      }

      "inheritance cycle: readwrite" in {
        val g = Graph(
          nodes = Set(
            user("A"), channelNode,
            node("B", Level(ReadWrite)),
            node("C", Inherited),
            node("D", Inherited)
          ),
          edges = Set(
            parent("C", "B"),
            parent("D", "C"),
            parent("C", "D")
          )
        )
        assert(access(g, "A", "D") == true)
        assert(access(g, "A", "C") == true)
      }

      "inheritance cycle: restricted" in {
        val g = Graph(
          nodes = Set(
            user("A"), channelNode,
            node("B", Level(Restricted)),
            node("C", Inherited),
            node("D", Inherited)
          ),
          edges = Set(
            parent("C", "B"),
            parent("D", "C"),
            parent("C", "D")
          )
        )
        assert(access(g, "A", "D") == false)
        assert(access(g, "A", "C") == false)
      }

      "non-existing nodes" in {
        val g = Graph(
          nodes = Set(
            user("A"), channelNode,
          ),
          edges = Set(
          )
        )
        assert(access(g, "A", "D") == true)
      }

      "inherit without any parent" in {
        val g = Graph(
          nodes = Set(
            user("A"), channelNode,
            node("B", Inherited)
          ),
          edges = Set(
          )
        )
        assert(access(g, "A", "B") == false)
      }
      // "use shortest depth of cycled nodes" in {
      //   val g = Graph(
      //     nodes = List(1, 2, 3, 4, 5, 10),
      //     // Containment(X, Y) == X <- Y
      //     edges = List(Containment(1, 2), Containment(2, 3), Containment(3, 4), Containment(4, 5),
      //                  Containment(50, 5),
      //                  Containment(4, 2) /* circle */)
      //   )
      //   assert(g.involvedInContainmentCycle(1) == false)
      //   assert(g.involvedInContainmentCycle(2) == true)
      //   assert(g.involvedInContainmentCycle(5) == false)
      //   assert(g.parentDepths(1:NodeId) == g.parentDepths(5:NodeId))
      // }
    }

    "root nodes" - {
      implicit def node(id: String): Node = Node.Content(NodeId(stringToCuid(id)), NodeData.PlainText(id.toString))

      def parent(childId: Cuid, parentId: Cuid) = Edge.Parent(NodeId(childId), NodeId(parentId))

      "empty" in {
        val g = Graph.empty
        assert(g.rootNodes.toSet == Set.empty)
      }

      "single node" in {
        val g = Graph(
          nodes = Set[Node]("A"),
        )
        assert(g.rootNodes.map(g.lookup.nodes).toSet == Set[Node]("A"))
      }

      "parent and child" in {
        val g = Graph(
          nodes = Set[Node]("A", "B"),
          edges = Set(parent("B", "A"))
        )
        assert(g.rootNodes.map(g.lookup.nodes).toSet == Set[Node]("A"))
      }

      "parent and child cycle" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "B"), parent("B", "C"))
        )
        assert(g.rootNodes.map(g.lookup.nodes).toSet == Set[Node]("A"))
      }

      "parents involved in cycle with child" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("A", "B"), parent("C", "B"))
        )
        assert(g.rootNodes.map(g.lookup.nodes).toSet == Set[Node]("A", "B"))
      }

      "parent with child cycle with child" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C", "D"),
          edges = Set(parent("B", "A"), parent("C", "B"), parent("B", "C"), parent("D", "C"))
        )
        assert(g.rootNodes.map(g.lookup.nodes).toSet == Set[Node]("A"))
      }
    }

    "redundant tree - including cycle leafs" - {
      import Tree._
      implicit def node(id:String):Node = Node.Content(NodeId(stringToCuid(id)), NodeData.PlainText(id.toString))
      def parent(childId:Cuid, parentId:Cuid) = Edge.Parent(NodeId(childId), NodeId(parentId))

      "root only" in {
        val g = Graph(
          nodes = Set[Node]("A"),
        )
        assert(g.redundantTree("A", excludeCycleLeafs = false) == Leaf("A"))
      }

      "single child" in {
        val g = Graph(
          nodes = Set[Node]("A", "B"),
          edges = Set(parent("B", "A"))
        )
        assert(g.redundantTree("A", excludeCycleLeafs = false) == Parent("A", List(Leaf("B"))))
      }

      "two children" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "A"))
        )
        assert(g.redundantTree("A", excludeCycleLeafs = false) == Parent("A", List(Leaf("B"), Leaf("C"))))
      }

      "diamond" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "B"), parent("C", "A"))
        )
        assert(g.redundantTree("A", excludeCycleLeafs = false) == Parent("A", List(Parent("B", List(Leaf("C"))), Leaf("C"))))
      }

      "cycle" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "B"), parent("A", "C"))
        )
        assert(g.redundantTree("A", excludeCycleLeafs = false) == Parent("A", List(Parent("B", List(Parent("C", List(Leaf("A"))))))))
      }
    }

    "redundant tree - excluding cycle leafs" - {
      import Tree._
      implicit def node(id:String):Node = Node.Content(NodeId(stringToCuid(id)), NodeData.PlainText(id.toString))
      def parent(childId:Cuid, parentId:Cuid) = Edge.Parent(NodeId(childId), NodeId(parentId))

      "root only" in {
        val g = Graph(
          nodes = Set[Node]("A"),
        )
        assert(g.redundantTree("A", excludeCycleLeafs = true) == Leaf("A"))
      }

      "single child" in {
        val g = Graph(
          nodes = Set[Node]("A", "B"),
          edges = Set(parent("B", "A"))
        )
        assert(g.redundantTree("A", excludeCycleLeafs = true) == Parent("A", List(Leaf("B"))))
      }

      "two children" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "A"))
        )
        assert(g.redundantTree("A", excludeCycleLeafs = true) == Parent("A", List(Leaf("B"), Leaf("C"))))
      }

      "diamond" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "B"), parent("C", "A"))
        )
        assert(g.redundantTree("A", excludeCycleLeafs = true) == Parent("A", List(Parent("B", List(Leaf("C"))), Leaf("C"))))
      }

      "cycle" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "B"), parent("A", "C"))
        )
        assert(g.redundantTree("A", excludeCycleLeafs = true) == Parent("A", List(Parent("B", List(Leaf("C"))))))
      }
    }

    "channel tree" - {
      import Tree._
      implicit def node(id:String):Node = Node.Content(NodeId(stringToCuid(id)), NodeData.PlainText(id.toString))
      def parent(childId:Cuid, parentId:Cuid) = Edge.Parent(NodeId(childId), NodeId(parentId))

      "root only" in {
        val g = Graph(
          nodes = Set[Node]("A"),
        )
        assert(g.channelTree("A") == Parent("A", List.empty))
      }

      "single child" in {
        val g = Graph(
          nodes = Set[Node]("A", "B"),
          edges = Set(parent("B", "A"))
        )
        assert(g.channelTree("A") == Parent("A", List(Leaf("B"))))
      }

      "two children" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "A"))
        )
        assert(g.channelTree("A") == Parent("A", List(Leaf("B"), Leaf("C"))))
      }

      "diamond on root level" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "B"), parent("C", "A"))
        )
        assert(g.channelTree("A") == Parent("A", List(Parent("B", List(Leaf("C"))))))
      }

      "diamond on deeper level" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C", "D"),
          edges = Set(
            parent("B", "A"), parent("C", "A"), parent("D", "A"),
            parent("D", "B"), parent("D", "C"), parent("C","B")
          )
        )
        assert(g.channelTree("A") == Parent("A", List(Parent("B", List(Leaf("D"), Parent("C", List(Leaf("D"))))))))
      }

      "cycle involving root" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "A"), parent("C", "B"), parent("A", "C"))
        )
        assert(g.channelTree("A") == Parent("A", List(Parent("B", List(Leaf("C"))))))
      }

      "cycle" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "A"), parent("C", "B"), parent("B", "C"))
        )
        assert(g.channelTree("A") == Parent("A", List(Parent("B", List(Leaf("C"))), Parent("C", List(Leaf("B"))))))
      }

      "topological Minor" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C", "D"),
          edges = Set(parent("B", "A"), parent("C","B"), parent("D", "C"), parent("D","A"))
        )
        assert(g.channelTree("A") == Parent("A", List(Parent("B", List(Leaf("D"))))))
      }

      "topological Minor - skip redundant edges" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C", "D"),
          edges = Set(parent("B", "A"), parent("C","B"), parent("D", "C"), parent("C","A"), parent("D","A"))
        )
        assert(g.channelTree("A") == Parent("A", List(Parent("B", List(Parent("C", List(Leaf("D"))))))))
      }
    }
  }
}
