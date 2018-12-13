package wust.graph

import org.scalatest._
import wust.ids._
import wust.util.collection._

class GraphSpec extends FreeSpec with MustMatchers {
  implicit def intToNodeId(id: Int): NodeId = NodeId(Cuid(id, 0))
  implicit def idToPost(id: Int): Node = Node.Content(id = id, data = NodeData.PlainText("content"), NodeRole.default)
  implicit def postListToMap(posts: List[Int]): List[Node] = posts.map(idToPost)
  implicit def tupleIsConnection(t: (Int, Int)): Edge = Connection(t._1, t._2)
  implicit def connectionListIsMap(connections: List[(Int, Int)]): List[Edge] = connections.map(tupleIsConnection)

  implicit def stringToCuid(id:String):Cuid = Cuid.fromBase58("5Q4is6Gc5NbA7T7W7PvAUw".dropRight(id.length) + id)
  val channelNode:Node = 0
  def user(id:Cuid) = Node.User(UserId(NodeId(id)), NodeData.User(id.toString, false, 0), NodeMeta.User)

  implicit class ContainmentBuilder(parentId: Int) {
    def cont(childId: Int) = Containment(parentId, childId);
  }
  def Connection(sourceId: NodeId, targetId: NodeId) = wust.graph.Edge.Label(sourceId, EdgeData.Label("connector"), targetId)
  def Containment(parentId: NodeId, childId: NodeId) = wust.graph.Edge.Parent(childId, parentId)

  def removeEdges(graph:Graph, es: Iterable[Edge]): Graph = new Graph(nodes = graph.nodes, edges = graph.edges.filterNot(es.toSet))
  def removeNodes(graph:Graph, nids: Iterable[NodeId]): Graph = graph.filterNotIds(nids.toSet)
  def addNodes(graph:Graph, newNodes: Iterable[Node]): Graph = new Graph(nodes = graph.nodes ++ newNodes, edges = graph.edges)
  def addEdges(graph:Graph, newEdges: Iterable[Edge]): Graph = new Graph(nodes = graph.nodes, edges = graph.edges ++ newEdges)


  "graph" - {
    "empty is empty" in {
      Graph.empty.lookup.idToIdx mustBe empty

      Graph.empty.nodes mustBe empty

      Graph().lookup.idToIdx mustBe empty

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

    "filter" in {
      val connections: List[Edge] = List(1 -> 2, 2 -> 3)
      val containments: List[Edge] = List(1 cont 2, 2 cont 3)
      val graph = Graph(List(1, 2, 3), connections ++ containments)

      val filteredGraph = graph filterIds Set[NodeId](1)
      filteredGraph.nodes.toSet mustEqual Graph(List(1)).nodes.toSet
      filteredGraph.edges.toSet mustEqual Graph(List(1)).edges.toSet

      val filteredGraph2 = graph filterIds Set[NodeId](1, 2)
      val comparisonGraph2 = Graph(List(1, 2), List(Connection(1, 2)) ++ List(Containment(1, 2)))
      filteredGraph2.nodes.toSet mustEqual comparisonGraph2.nodes.toSet
      filteredGraph2.edges.toSet mustEqual comparisonGraph2.edges.toSet
    }

    "successors of post" in {
      pending
      val graph = Graph(
        nodes = List(1, 11, 12, 13, 14),
        edges = List(Connection(1, 11), Connection(11, 12), Connection(12, 1), Connection(12, 13)) ++ List(Containment(12, 14))
      )

      graph.nodesById(12)
      graph.nodesById(12)
      graph.successorsWithoutParent(12) mustEqual Set[NodeId](1, 13)
      graph.successorsWithoutParent(13) mustEqual Set.empty
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

    "change graph" - {

      "replace parent edge in graph" in {
        val graph = Graph(
          nodes = List(1, 2),
          edges = Set(Edge.Parent(2: NodeId, new EdgeData.Parent(deletedAt = None, ordering = Some(5)), 1: NodeId))
        )

        graph.parents(1: NodeId) mustEqual Set.empty
        graph.parents(2: NodeId) mustEqual Set[NodeId](1)
        assert(graph.edges.head.asInstanceOf[Edge.Parent].data.ordering.get.toInt == 5)

        val newParent = Edge.Parent(2: NodeId, new EdgeData.Parent(deletedAt = None, ordering = Some(7)), 1: NodeId)
        val newGraph = graph.applyChanges(GraphChanges(addEdges = Set(newParent)))

        assert(newGraph.edges.head.asInstanceOf[Edge.Parent].data.ordering.get.toInt == 7)
      }

      "replace parent edge (with ordering) in graph by empty parent edge" in {
        val graph = Graph(
          nodes = List(1, 2),
          edges = Set()
        )

        val oldParent = Edge.Parent(2: NodeId, new EdgeData.Parent(deletedAt = None, ordering = Some(5)), 1: NodeId)
        val newParent = Edge.Parent(2: NodeId, new EdgeData.Parent(deletedAt = None, ordering = None), 1: NodeId)
        val newGraph = graph.applyChanges(GraphChanges(addEdges = Set(oldParent,newParent)))

        assert(newGraph.edges.size == 1)
        assert(newGraph.edges.head.asInstanceOf[Edge.Parent].data.ordering == None)
      }

      "replace parent edge in graph multiple parents" in {
        val graph = Graph(
          nodes = List(1, 2, 3),
          edges = Set(
            Edge.Parent(2: NodeId, new EdgeData.Parent(deletedAt = None, ordering = Some(5)), 1: NodeId),
            Edge.Parent(3: NodeId, new EdgeData.Parent(deletedAt = None, ordering = Some(4)), 1: NodeId)
          )
        )

        graph.parents(1: NodeId) mustEqual Set.empty
        graph.parents(2: NodeId) mustEqual Set[NodeId](1)
        graph.parents(3: NodeId) mustEqual Set[NodeId](1)

        assert(graph.edges.find(e => e.sourceId == (2: NodeId) && e.targetId == (1: NodeId)).get.asInstanceOf[Edge.Parent].data.ordering.get.toInt == 5)
        assert(graph.edges.find(e => e.sourceId == (3: NodeId) && e.targetId == (1: NodeId)).get.asInstanceOf[Edge.Parent].data.ordering.get.toInt == 4)

        val newParent = Edge.Parent(2: NodeId, new EdgeData.Parent(deletedAt = None, ordering = Some(7)), 1: NodeId)
        val newGraph = graph.applyChanges(GraphChanges(addEdges = Set(newParent)))

        assert(newGraph.edges.find(e => e.sourceId == (2: NodeId) && e.targetId == (1: NodeId)).get.asInstanceOf[Edge.Parent].data.ordering.get.toInt == 7)
      }

    }

    "permissions" - {
      // IMPORTANT:
      // exactly the same test cases as for stored procedure `can_access_node()`
      // when changing things, make sure to change them for the stored procedure as well.
      import wust.ids.NodeAccess.{Level, Inherited}
      import wust.ids.AccessLevel._
      def node(id:Cuid, nodeAccess: NodeAccess) = Node.Content(NodeId(id), NodeData.PlainText(id.toString), NodeRole.default, NodeMeta(nodeAccess))
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
    }

    "root nodes" - {
      implicit def node(id: String): Node = Node.Content(NodeId(stringToCuid(id)), NodeData.PlainText(id.toString), NodeRole.default)

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

      // can not succeed with rootNodes approach
//      "root that is also child" in {
//        val g = Graph(
//          nodes = Set[Node]("A", "B", "C", "D", "E", "F"),
//          edges = Set(
//            parent("B", "A"),
//            parent("F", "B"),
//
//            parent("C", "B"),
//            parent("D", "C"),
//            parent("F", "D"),
//
//          )
//        )
//        assert(g.rootNodes.map(g.lookup.nodes).toSet == Set[Node]("A", "B", "E"))
//
//      }
    }

    "redundant tree - including cycle leafs" - {
      import Tree._
      implicit def node(id:String):Node = Node.Content(NodeId(stringToCuid(id)), NodeData.PlainText(id.toString), NodeRole.default)
      def parent(childId:Cuid, parentId:Cuid) = Edge.Parent(NodeId(childId), NodeId(parentId))
      def redundantTree(g:Graph, node:Node) = g.redundantTree(g.idToIdx(node.id), excludeCycleLeafs = false)

      "root only" in {
        val g = Graph(
          nodes = Set[Node]("A"),
        )
        assert(redundantTree(g, "A") == Leaf("A"))
      }

      "single child" in {
        val g = Graph(
          nodes = Set[Node]("A", "B"),
          edges = Set(parent("B", "A"))
        )
        assert(redundantTree(g, "A") == Parent("A", List(Leaf("B"))))
      }

      "two children" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "A"))
        )
        assert(redundantTree(g, "A") == Parent("A", List(Leaf("B"), Leaf("C"))))
      }

      "diamond" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "B"), parent("C", "A"))
        )
        assert(redundantTree(g, "A") == Parent("A", List(Parent("B", List(Leaf("C"))), Leaf("C"))))
      }

      "different depth diamond" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C", "D", "E", "F"),
          edges = Set(
            parent("B", "A"),
            parent("F", "B"),

            parent("C", "A"),
            parent("D", "C"),
            parent("F", "D"),

          )
        )
        assert(redundantTree(g, "A") ==
          Parent("A", List(
            Parent("B", List(
              Leaf("F")
            )),
            Parent("C", List(
              Parent("D", List(
                Leaf("F"))
              ))
            ))
          )
        )
      }

      "cycle" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "B"), parent("A", "C"))
        )
        assert(redundantTree(g, "A") == Parent("A", List(Parent("B", List(Parent("C", List(Leaf("A"))))))))
      }
    }

    "redundant tree - excluding cycle leafs" - {
      import Tree._
      implicit def node(id:String):Node = Node.Content(NodeId(stringToCuid(id)), NodeData.PlainText(id.toString), NodeRole.default)
      def parent(childId:Cuid, parentId:Cuid) = Edge.Parent(NodeId(childId), NodeId(parentId))
      def redundantTree(g:Graph, node:Node) = g.redundantTree(g.idToIdx(node.id), excludeCycleLeafs = true)

      "root only" in {
        val g = Graph(
          nodes = Set[Node]("A"),
        )
        assert(redundantTree(g, "A") == Leaf("A"))
      }

      "single child" in {
        val g = Graph(
          nodes = Set[Node]("A", "B"),
          edges = Set(parent("B", "A"))
        )
        assert(redundantTree(g, "A") == Parent("A", List(Leaf("B"))))
      }

      "two children" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "A"))
        )
        assert(redundantTree(g, "A") == Parent("A", List(Leaf("B"), Leaf("C"))))
      }

      "diamond" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "B"), parent("C", "A"))
        )
        assert(redundantTree(g, "A") == Parent("A", List(Parent("B", List(Leaf("C"))), Leaf("C"))))
      }

      "cycle" in {
        val g = Graph(
          nodes = Set[Node]("A", "B", "C"),
          edges = Set(parent("B", "A"), parent("C", "B"), parent("A", "C"))
        )
        assert(redundantTree(g, "A") == Parent("A", List(Parent("B", List(Leaf("C"))))))
      }
    }

    "channel tree" - {
      import Tree._
      implicit def node(id:String):Node = Node.Content(NodeId(stringToCuid(id)), NodeData.PlainText(id.toString), NodeRole.default)
      def user(id:String):Node = Node.User(UserId(NodeId(stringToCuid(id))), NodeData.User("hans", false, 0), NodeMeta.User)
      def parent(childId:Cuid, parentId:Cuid) = Edge.Parent(NodeId(childId), NodeId(parentId))
      def pinned(userId:Cuid, nodeId:Cuid) = Edge.Pinned(UserId(NodeId(userId)), NodeId(nodeId))

      "empty" in {
        val g = Graph(
          nodes = Set[Node](user("User")),
          edges = Set[Edge]()
        )
        assert(g.channelTree(UserId(NodeId("User": Cuid))) == Nil)
      }

      "single channel" in {
        val g = Graph(
          nodes = Set[Node]("B", user("User")),
          edges = Set(pinned("User", "B"))
        )
        assert(g.channelTree(UserId(NodeId("User": Cuid))) == Leaf("B") :: Nil)
      }

      "two children" in {
        val g = Graph(
          nodes = Set[Node]("B", "C", user("User")),
          edges = Set(pinned("User", "B"), pinned("User", "C"))
        )
        assert(g.channelTree(UserId(NodeId("User": Cuid))) == List(Leaf("B"), Leaf("C")))
      }

      "one transitive child" in {
        val g = Graph(
          nodes = Set[Node]("B", "C", user("User")),
          edges = Set(
            parent("C", "B"),
            pinned("User", "B"), pinned("User", "C"))
        )
        assert(g.channelTree(UserId(NodeId("User": Cuid))) == List(Parent("B", List(Leaf("C")))))
      }

      "diamond" in {
        val g = Graph(
          nodes = Set[Node]("B", "C", "D", user("User")),
          edges = Set(
            parent("D", "B"), parent("D", "C"), parent("C","B"),
            pinned("User", "B"), pinned("User", "C"), pinned("User", "D")
          )
        )
        assert(g.channelTree(UserId(NodeId("User": Cuid))) == List(Parent("B", List(Parent("C", List(Leaf("D"))), Leaf("D")))))
      }

      "cycle" in {
        val g = Graph(
          nodes = Set[Node]("B", "C", user("User")),
          edges = Set(parent("C", "B"), parent("B", "C"), pinned("User", "B"), pinned("User", "C"))
        )
        assert(g.channelTree(UserId(NodeId("User": Cuid))) == List(Parent("B", List(Leaf("C"))), Parent("C", List(Leaf("B")))))
      }

      "topological Minor" in {
        val g = Graph(
          nodes = Set[Node]("B", "C", "D", user("User")),
          edges = Set(parent("C","B"), parent("D", "C"), pinned("User", "B"), pinned("User", "D"))
        )
        assert(g.channelTree(UserId(NodeId("User": Cuid))) == List(Parent("B", List(Leaf("D")))))
      }

      "topological Minor -- only channels" in {
        val g = Graph(
          nodes = Set[Node]("B", "C", "D", user("User")),
          edges = Set(parent("C","B"), parent("D", "C"), pinned("User", "B"), pinned("User", "C"), pinned("User", "D"))
        )
        assert(g.channelTree(UserId(NodeId("User": Cuid))) == List(Parent("B", List(Parent("C", List(Leaf("D")))))))
      }
    }

  }
}
