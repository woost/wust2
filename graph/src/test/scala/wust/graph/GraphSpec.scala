package wust.graph

import org.scalatest._
import org.scalatest.matchers._
import org.scalatest.freespec.AnyFreeSpec
import wust.ids._

class GraphSpec extends AnyFreeSpec with must.Matchers {
  implicit def intToNodeId(id: Int): NodeId = NodeId(Cuid(id, 0))
  implicit def idToNode(id: Int): Node = Node.Content(id = id, data = NodeData.PlainText("content"), role = NodeRole.default, meta = NodeMeta(NodeAccess.ReadWrite))
  implicit def nodeListToMap(nodes: List[Int]): List[Node] = nodes.map(idToNode)
  implicit def tupleIsConnection(t: (Int, Int)): Edge = Connection(t._1, t._2)
  implicit def connectionListIsMap(connections: List[(Int, Int)]): List[Edge] = connections.map(tupleIsConnection)

  implicit def stringToCuid(id:String):Cuid = Cuid.fromBase58String("5Q4is6Gc5NbA7T7W7PvAUw".dropRight(id.length) + id).right.get
  val channelNode:Node = 0
  def user(id:Cuid) = Node.User(UserId(NodeId(id)), NodeData.User(id.toString, false, 0, None), NodeMeta.User)

  implicit class ContainmentBuilder(parentId: Int) {
    def cont(childId: Int) = Containment(parentId, childId);
  }
  def Connection(sourceId: NodeId, targetId: NodeId) = wust.graph.Edge.LabeledProperty(sourceId, EdgeData.LabeledProperty("connector"), PropertyId(targetId))
  def Containment(parentId: NodeId, childId: NodeId) = wust.graph.Edge.Child(ParentId(parentId), ChildId(childId))

  "graph" - {
    "empty is empty" in {
      Graph.empty.isEmpty mustBe true

      Graph.empty.nodes mustBe empty

      Graph(Array.empty, Array.empty).isEmpty mustBe true

      Graph(Array.empty, Array.empty).nodes mustBe empty
    }

    "directed cycle" in {
      val graph = Graph(
        nodes = Array(1, 11, 12),
        edges = Array(Containment(1, 11), Containment(11, 12), Containment(12, 1))
      )

      graph.involvedInContainmentCycle(1) mustEqual true
    }

    "one contain" in {
      val graph = Graph(Array(1, 11), Array(Containment(11, 1)))
      graph.involvedInContainmentCycle(1) mustEqual false
    }

    "have transitive parents in cycle" in {
      val graph = Graph(Array(1, 2, 3), Array(1 cont 2, 2 cont 3, 3 cont 1))
      graph.ancestors(3).toSet mustEqual Set[NodeId](3, 2, 1)
    }

    "have transitive children in cycle" in {
      val graph = Graph(Array(1, 2, 3), Array(1 cont 2, 2 cont 3, 3 cont 1))
      graph.descendants(3).toSet mustEqual Set[NodeId](3, 2, 1)
    }

    "descendants don't include start" in {
      val graph = Graph(Array(1), Array())
      graph.descendants(1).toSet mustEqual Set[NodeId]()
    }
    "descendants handles diamond" in {
      val graph = Graph(Array(1,2,3), Array(1 cont 3, 1 cont 2, 2 cont 3))
      graph.descendants(1).toList mustEqual List[NodeId](2, 3)
      val graph2 = Graph(Array(1,2,3), Array(1 cont 2, 1 cont 3, 2 cont 3))
      graph2.descendants(1).toList mustEqual List[NodeId](3, 2)
    }

    "ancestors don't include start" in {
      val graph = Graph(Array(1), Array())
      graph.ancestors(1).toSet mustEqual Set[NodeId]()
    }
    "ancestors handles diamond" in {
      val graph = Graph(Array(1,2,3), Array(1 cont 2, 1 cont 3, 2 cont 3))
      graph.ancestors(3).toList mustEqual List[NodeId](2, 1)
      val graph2 = Graph(Array(1,2,3), Array(1 cont 2, 2 cont 3, 1 cont 3))
      graph2.ancestors(3).toList mustEqual List[NodeId](1, 2)
    }

    "children of node" in {
      val graph = Graph(
        nodes = Array(1, 11, 12, 13, 14),
        edges = Array(Connection(1, 14)) ++ Array(Containment(1, 11), Containment(1, 12), Containment(13, 12))
      )

      graph.children(1:NodeId) mustEqual Array[NodeId](11, 12)
      graph.children(12:NodeId) mustEqual Array.empty
    }

    "parents of node" in {
      val graph = Graph(
        nodes = Array(1, 11, 12, 13, 14),
        edges = Array(Connection(1, 14)) ++ Array(Containment(1, 11), Containment(1, 12), Containment(13, 12))
      )

      graph.parents(1:NodeId) mustEqual Seq.empty
      graph.parents(12:NodeId) mustEqual Seq[NodeId](1, 13)
    }

    "change graph" - {

      "replace parent edge in graph" in {
        val graph = Graph(
          nodes = Array(1, 2),
          edges = Array(Edge.Child(ParentId(1: NodeId), new EdgeData.Child(deletedAt = None, ordering = BigDecimal(5.0)), ChildId(2: NodeId)))
        )

        graph.parents(1: NodeId) mustEqual Seq.empty
        graph.parents(2: NodeId) mustEqual Seq[NodeId](1)
        assert(graph.edges.head.as[Edge.Child].data.ordering == BigDecimal(5.0))

        val newParent: List[Edge] = List(Edge.Child(ParentId(NodeId(Cuid(1, 0)): NodeId), EdgeData.Child(deletedAt = None, ordering = BigDecimal(7.0)), ChildId(NodeId(Cuid(2, 0)): NodeId)))
        val gc: GraphChanges = GraphChanges.from(addEdges = newParent)
        val newGraph: Graph = graph.applyChanges(gc)

        assert(newGraph.edges.head.as[Edge.Child].data.ordering == BigDecimal(7.0))
      }

      "replace parent edge in graph multiple parents" in {
        val graph = Graph(
          nodes = Array(1, 2, 3),
          edges = Array(
            Edge.Child(ParentId(1: NodeId), EdgeData.Child(deletedAt = None, ordering = BigDecimal(5.0)), ChildId(2: NodeId)),
            Edge.Child(ParentId(1: NodeId), EdgeData.Child(deletedAt = None, ordering = BigDecimal(4.0)), ChildId(3: NodeId))
          )
        )

        graph.parents(1: NodeId) mustEqual Seq.empty
        graph.parents(2: NodeId) mustEqual Seq[NodeId](1)
        graph.parents(3: NodeId) mustEqual Seq[NodeId](1)

        assert(graph.edges.find(e => e.targetId == (2: NodeId) && e.sourceId == (1: NodeId)).exists(_.as[Edge.Child].data.ordering == BigDecimal(5.0)))
        assert(graph.edges.find(e => e.targetId == (3: NodeId) && e.sourceId == (1: NodeId)).exists(_.as[Edge.Child].data.ordering == BigDecimal(4.0)))

        val newParent = Edge.Child(ParentId(1: NodeId), EdgeData.Child(deletedAt = None, ordering = BigDecimal(7.0)), ChildId(2: NodeId))
        val newGraph = graph.applyChanges(GraphChanges(addEdges = Array(newParent)))

        assert(newGraph.edges.find(e => e.targetId == (2: NodeId) && e.sourceId == (1: NodeId)).exists(_.as[Edge.Child].data.ordering == BigDecimal(7.0)))
      }

    }

    "permissions" - {
      // IMPORTANT:
      // exactly the same test cases as for stored procedure `can_access_node()`
      // when changing things, make sure to change them for the stored procedure as well.
      import wust.ids.AccessLevel._
      import wust.ids.NodeAccess.{Inherited, Level}
      def node(id:Cuid, nodeAccess: NodeAccess) = Node.Content(NodeId(id), NodeData.PlainText(id.toString), NodeRole.default, NodeMeta(nodeAccess))
      def member(user:Cuid, level:AccessLevel, node:Cuid) = Edge.Member(NodeId(node), EdgeData.Member(level), UserId(NodeId(user)))
      def parent(childId:Cuid, parentId:Cuid) = Edge.Child(ParentId(NodeId(parentId)), ChildId(NodeId(childId)))
      def access(g:Graph, user:Cuid, node:Cuid):Boolean = g.can_access_node(UserId(NodeId(user)), NodeId(node))

      "simple" - {
        "1" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Level(Restricted))),
            edges = Array(member("A", Restricted, "B"))
          )
          assert(access(g, "A", "B") == false)
        }
        "2" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Level(ReadWrite))),
            edges = Array(member("A", Restricted, "B"))
          )
          assert(access(g, "A", "B") == false)
        }
        "3" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Inherited)),
            edges = Array(member("A", Restricted, "B"))
          )
          assert(access(g, "A", "B") == false)
        }


        "4" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Level(Restricted))),
            edges = Array(member("A", ReadWrite, "B"))
          )
          assert(access(g, "A", "B") == true)
        }
        "5" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Level(ReadWrite))),
            edges = Array(member("A", ReadWrite, "B"))
          )
          assert(access(g, "A", "B") == true)
        }
        "6" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Inherited)),
            edges = Array(member("A", ReadWrite, "B"))
          )
          assert(access(g, "A", "B") == true)
        }


        "7" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Level(Restricted))),
            edges = Array()
          )
          assert(access(g, "A", "B") == false)
        }

        "8" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Level(ReadWrite))),
            edges = Array()
          )
          assert(access(g, "A", "B") == false)
        }

        "9" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Inherited)),
            edges = Array()
          )
          assert(access(g, "A", "B") == false)
        }

        "10" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Level(Restricted))),
            edges = Array(member("A", ReadWrite, "B"))
          )
          assert(access(g, "A", "B") == true)
        }

        "11" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Level(ReadWrite))),
            edges = Array(member("A", ReadWrite, "B"))
          )
          assert(access(g, "A", "B") == true)
        }

        "12" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Inherited)),
            edges = Array(member("A", ReadWrite, "B"))
          )
          assert(access(g, "A", "B") == true)
        }
      }

      "simple inheritance" - {
        "1" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Level(Restricted)), node("C", Inherited)),
            edges = Array(member("A", Restricted, "B"), parent("C", "B"))
          )
          assert(access(g, "A", "C") == false)
        }
        "2" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Level(ReadWrite)), node("C", Inherited)),
            edges = Array(member("A", Restricted, "B"), parent("C", "B"))
          )
          assert(access(g, "A", "C") == false)
        }
        "3" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Inherited), node("C", Inherited)),
            edges = Array(member("A", Restricted, "B"), parent("C", "B"))
          )
          assert(access(g, "A", "C") == false)
        }


        "4" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Level(Restricted)), node("C", Inherited)),
            edges = Array(member("A", ReadWrite, "B"), parent("C", "B"))
          )
          assert(access(g, "A", "C") == true)
        }
        "5" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Level(ReadWrite)), node("C", Inherited)),
            edges = Array(member("A", ReadWrite, "B"), parent("C", "B"))
          )
          assert(access(g, "A", "C") == true)
        }
        "6" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Inherited), node("C", Inherited)),
            edges = Array(member("A", ReadWrite, "B"), parent("C", "B"))
          )
          assert(access(g, "A", "C") == true)
        }


        "7" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Level(Restricted)), node("C", Inherited)),
            edges = Array(parent("C", "B"))
          )
          assert(access(g, "A", "C") == false)
        }

        "8" in {
          val g = Graph(
            nodes = Array(user("A"), channelNode, node("B", Level(ReadWrite)), node("C", Inherited)),
            edges = Array(member("A", ReadWrite, "B"), parent("C", "B"))
          )
          assert(access(g, "A", "C") == true)
        }
      }

      "multiple inheritance: max wins" in {
        val g = Graph(
          nodes = Array(
            user("A"), channelNode,
            node("B", Level(Restricted)),
            node("C", Level(ReadWrite)),
            node("D", Inherited)
          ),
          edges = Array(
            member("A", ReadWrite, "C"),
            parent("D", "B"),
            parent("D", "C")
          )
        )
        assert(access(g, "A", "D") == true)
      }

      "long inheritance chain: readwrite" in {
        val g = Graph(
          nodes = Array(
            user("A"), channelNode,
            node("B", Level(ReadWrite)),
            node("C", Inherited),
            node("D", Inherited)
          ),
          edges = Array(
            member("A", ReadWrite, "B"),
            parent("C", "B"),
            parent("D", "C")
          )
        )
        assert(access(g, "A", "D") == true)
      }

      "long inheritance chain: restricted" in {
        val g = Graph(
          nodes = Array(
            user("A"), channelNode,
            node("B", Level(Restricted)),
            node("C", Inherited),
            node("D", Inherited)
          ),
          edges = Array(
            parent("C", "B"),
            parent("D", "C")
          )
        )
        assert(access(g, "A", "D") == false)
      }

      "inheritance cycle: inherit without member" in {
        val g = Graph(
          nodes = Array(
            user("A"), channelNode,
            node("B", Inherited),
            node("C", Inherited),
            node("D", Inherited)
          ),
          edges = Array(
            parent("C", "B"),
            parent("D", "C"),
            parent("C", "D")
          )
        )
        assert(access(g, "A", "D") == false)
        assert(access(g, "A", "C") == false)
      }

      "inheritance cycle: inherit with member" in {
        val g = Graph(
          nodes = Array(
            user("A"), channelNode,
            node("B", Inherited),
            node("C", Inherited),
            node("D", Inherited)
          ),
          edges = Array(
            member("A", ReadWrite, "B"),
            parent("C", "B"),
            parent("D", "C"),
            parent("C", "D")
          )
        )
        assert(access(g, "A", "D") == true)
        assert(access(g, "A", "C") == true)
      }

      "inheritance cycle: readwrite without member" in {
        val g = Graph(
          nodes = Array(
            user("A"), channelNode,
            node("B", Level(ReadWrite)),
            node("C", Inherited),
            node("D", Inherited)
          ),
          edges = Array(
            parent("C", "B"),
            parent("D", "C"),
            parent("C", "D")
          )
        )
        assert(access(g, "A", "D") == false)
        assert(access(g, "A", "C") == false)
      }

      "inheritance cycle: readwrite with member" in {
        val g = Graph(
          nodes = Array(
            user("A"), channelNode,
            node("B", Level(ReadWrite)),
            node("C", Inherited),
            node("D", Inherited)
          ),
          edges = Array(
            member("A", ReadWrite, "B"),
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
          nodes = Array(
            user("A"), channelNode,
            node("B", Level(Restricted)),
            node("C", Inherited),
            node("D", Inherited)
          ),
          edges = Array(
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
          nodes = Array(
            user("A"), channelNode,
          ),
          edges = Array(
          )
        )
        assert(access(g, "A", "D") == true)
      }

      "inherit without any parent" in {
        val g = Graph(
          nodes = Array(
            user("A"), channelNode,
            node("B", Inherited)
          ),
          edges = Array(
          )
        )
        assert(access(g, "A", "B") == false)
      }
    }

    //"root nodes" - {
    //  implicit def node(id: String): Node = Node.Content(NodeId(stringToCuid(id)), NodeData.PlainText(id.toString), NodeRole.default)

    //  def parent(childId: Cuid, parentId: Cuid) = Edge.Child(ParentId(NodeId(parentId)), ChildId(NodeId(childId)))

    //  "empty" in {
    //    val g = Graph.empty
    //    assert(g.rootNodes.toSet == Set.empty)
    //  }

    //  "single node" in {
    //    val g = Graph(
    //      nodes = Array[Node]("A"),
    //    )
    //    assert(g.rootNodes.map(g.lookup.nodes).toSet == Set[Node]("A"))
    //  }

    //  "parent and child" in {
    //    val g = Graph(
    //      nodes = Array[Node]("A", "B"),
    //      edges = Array(parent("B", "A"))
    //    )
    //    assert(g.rootNodes.map(g.lookup.nodes).toSet == Set[Node]("A"))
    //  }

    //  "parent and child cycle" in {
    //    val g = Graph(
    //      nodes = Array[Node]("A", "B", "C"),
    //      edges = Array(parent("B", "A"), parent("C", "B"), parent("B", "C"))
    //    )
    //    assert(g.rootNodes.map(g.lookup.nodes).toSet == Set[Node]("A"))
    //  }

    //  "parents involved in cycle with child" in {
    //    val g = Graph(
    //      nodes = Array[Node]("A", "B", "C"),
    //      edges = Array(parent("B", "A"), parent("A", "B"), parent("C", "B"))
    //    )
    //    assert(g.rootNodes.map(g.lookup.nodes).toSet == Set[Node]("A", "B"))
    //  }

    //  "parent with child cycle with child" in {
    //    val g = Graph(
    //      nodes = Array[Node]("A", "B", "C", "D"),
    //      edges = Array(parent("B", "A"), parent("C", "B"), parent("B", "C"), parent("D", "C"))
    //    )
    //    assert(g.rootNodes.map(g.lookup.nodes).toSet == Set[Node]("A"))
    //  }

    //  // can not succeed with rootNodes approach
////      "root that is also child" in {
////        val g = Graph(
////          nodes = Array[Node]("A", "B", "C", "D", "E", "F"),
////          edges = Array(
////            parent("B", "A"),
////            parent("F", "B"),
////
////            parent("C", "B"),
////            parent("D", "C"),
////            parent("F", "D"),
////
////          )
////        )
////        assert(g.rootNodes.map(g.lookup.nodes).toSet == Set[Node]("A", "B", "E"))
////
////      }
    //}

  }
}
