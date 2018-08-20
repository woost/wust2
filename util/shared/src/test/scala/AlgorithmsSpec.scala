package wust.util

import org.scalatest._
import wust.util.algorithm._

class AlgorithmsSpec extends FreeSpec with MustMatchers {

  "indexing" - {
    val edges = List(
      1 -> 2,
      2 -> 3,
      1 -> 3
    )
    "default neighbourhood" - {
      "empty" in {
        val neighbourhood = defaultNeighbourhood[Int,Int](Nil, 0)
        neighbourhood mustEqual scala.collection.Map.empty[Int,Int]
        neighbourhood(7) mustEqual 0
      }
      "not empty" in {
        val neighbourhood = defaultNeighbourhood[Int,Int](List(2,3), 0)
        neighbourhood mustEqual scala.collection.Map[Int,Int](2 -> 0, 3 -> 0)
        neighbourhood(7) mustEqual 0
      }
    }
    "directed adjacency list" - {
      "empty" in {
        directedAdjacencyList[Int, Int, Int](Nil, identity, identity) mustEqual
          Map.empty
      }

      "successors" in {
        directedAdjacencyList[Int, (Int, Int), Int](edges, _._1, _._2) mustEqual
          Map(1 -> Set(2, 3), 2 -> Set(3))
      }
    }

    "adjacency list" - {
      "empty" in {
        adjacencyList[Int, Int](Nil, identity, identity) mustEqual Map.empty
      }

      "neighbours" in {
        adjacencyList[Int, (Int, Int)](edges, _._1, _._2) mustEqual
          Map(1 -> Set(2, 3), 2 -> Set(1, 3), 3 -> Set(1, 2))
      }
    }

    "directed incidence list" - {
      "empty" in {
        directedIncidenceList[Int, Int](Nil, identity) mustEqual Map.empty
      }

      "outgoing edges" in {
        directedIncidenceList[Int, (Int, Int)](edges, _._1) mustEqual
          Map(1 -> Set(1 -> 2, 1 -> 3), 2 -> Set(2 -> 3))
      }
    }
    "incidence list" - {
      "empty" in {
        incidenceList[Int, Int](Nil, identity, identity) mustEqual Map.empty
      }

      "incident edges" in {
        incidenceList[Int, (Int, Int)](edges, _._1, _._2) mustEqual
          Map(1 -> Set(1 -> 2, 1 -> 3),
              2 -> Set(1 -> 2, 2 -> 3),
              3 -> Set(1 -> 3, 2 -> 3))
      }
    }

    "degree sequence" - {
      "empty" in {
        degreeSequence[Int, Int](Nil, identity, identity) mustEqual Map.empty
      }

      "neighbours" in {
        degreeSequence[Int, (Int, Int)](edges, _._1, _._2) mustEqual
          Map(1 -> 2, 2 -> 2, 3 -> 2)
      }
    }

    "directed degree sequence" - {
      "empty" in {
        directedDegreeSequence[Int, Int](Nil, identity) mustEqual Map.empty
      }

      "neighbours" in {
        directedDegreeSequence[Int, (Int, Int)](edges, _._1) mustEqual
          Map(1 -> 2, 2 -> 1)
      }
    }
  }

  "depth first search" - {
    "one vertex" - {
      val dfs = depthFirstSearch[Int](0, _ => Seq.empty).toList
      assert(dfs == List(0))
    }

    "directed cycle" - {
      val edges = Map(
        0 -> Seq(1, 2),
        1 -> Seq(3),
        2 -> Seq(1),
        3 -> Seq(0, 2)
      )

      val dfs = depthFirstSearch[Int](0, edges).toList
      assert(dfs == List(0, 2, 1, 3))
    }

    "undirected cycle" - {
      val edges = Map(
        0 -> Seq(1, 2),
        1 -> Seq(3),
        2 -> Seq.empty,
        3 -> Seq(2)
      )

      val dfs = depthFirstSearch[Int](0, edges).toList
      assert(dfs == List(0, 2, 1, 3))
    }
  }

  "connected components" - {
    "one vertex" in {
      val components = connectedComponents[Int](List(0), _ => Nil)
      components must contain theSameElementsAs List(Set(0))
    }

    "two isolated vertices" in {
      val components = connectedComponents[Int](List(0,1), _ => Nil)
      components must contain theSameElementsAs List(Set(0), Set(1))
    }

    "cycles" in {
      val edges = Map(
        0 -> Seq(1, 2, 3),
        1 -> Seq(0,2,3),
        2 -> Seq(0,1,3),
        3 -> Seq(0,1,2),
        4 -> Seq(5),
        5 -> Seq(4)
      )

      val components = connectedComponents[Int](edges.keys, edges)
      components must contain theSameElementsAs List(Set(0,1,2,3), Set(4,5))
    }
  }

  "topological sort" - {
    "empty" in {
      val list = topologicalSort[Int, Seq](Seq.empty, _ => Seq.empty)
      assert(list == List())
    }

    "one vertex" in {
      val list = topologicalSort[Int, Seq](Seq(0), _ => Seq.empty)
      assert(list == List(0))
    }

    "with successor" in {
      val list = topologicalSort[Int, Seq](Seq(0, 1), _ => Seq(1)).toList
      assert(list == List(0, 1))
    }

    "tolerate directed cycle" in {
      val edges = Map(
        0 -> Seq(1),
        1 -> Seq(2),
        2 -> Seq(1, 3),
        3 -> Seq.empty
      )

      val list = topologicalSort[Int, Seq](Seq(0, 1, 2, 3), edges).toList
      assert(list == List(0, 2, 1, 3))
    }

   "stable" - {

      "test 0" in {
        val edges = Map(
          0 -> Seq(3),
          1 -> Seq(2),
          2 -> Seq(3, 99),
          5 -> Seq(6)
          ).withDefaultValue(Seq.empty)

        val list = topologicalSort[Int, Seq](Seq(3, 0, 2, 1, 3, 6, 4, 5, 99), edges).toList
        assert(list == List(0, 1, 2, 3, 4, 5, 6, 99))
      }

      "test 1" in {
        val edges = Map(
          125755 -> Seq(-23),
          -23 -> Seq(11231312)
        ).withDefaultValue(Seq.empty)

        val list = topologicalSort[Int, Seq](Seq(0, 11231312, 125755, -23, 12), edges).toList
        assert(list == List(0, 125755, -23, 11231312, 12))
      }

      "test 2" in {
        val edges = Map(
          125755 -> Seq(-23),
          -23 -> Seq(11231312)
        ).withDefaultValue(Seq.empty)

        val list = topologicalSort[Int, Seq](Seq(0, 11231312, 125755, -23, 12), edges).toList
        assert(list == List(0, 125755, -23, 11231312, 12))
      }

      "test 3" in {
        val edges = Map(
          -18 -> Seq(-1000),
          12 -> Seq(-1000, 7),
        ).withDefaultValue(Seq.empty)

        val list = topologicalSort[Int, Seq](Seq(-18, 7, 12, -1000, 4), edges).toList
        assert(list == List(-18, 12, 7, -1000, 4))
      }

      "test 4" in {
        val edges = Map(
          1234 -> Seq(-1),
          300033 -> Seq(-1, -133)
        ).withDefaultValue(Seq.empty)

        val list = topologicalSort[Int, Seq](Seq(1234, 300033, -133, 19, -1), edges).toList
        assert(list == List(1234, 300033, -133, 19, -1))
      }

      "test 5" in {
        val edges = Map(
          -1334 -> Seq(99281),
          99281 -> Seq(-1)
        ).withDefaultValue(Seq.empty)

        val list = topologicalSort[Int, Seq](Seq(-1334, 3321312, 99281, -1, 4), edges).toList
        assert(list == List(-1334, 3321312, 99281, -1, 4))
      }
    }
  }

  "dijkstra" - {
    "for simple case" in {
      val (depths, predecessors) = dijkstra(Map(0 -> Seq(1),
                                                1 -> Seq()), 0)
      assert(depths == Map(0 -> 0, 1 -> 1))
      assert(predecessors == Map(1 -> 0))
    }
    "for complex case" in {
      val edges = Map(
        0 -> Seq(10, 20),
        10 -> Seq(30),
        20 -> Seq(21),
        21 -> Seq(30),
        30 -> Seq()
      )
      val (depths, predecessors) = dijkstra(edges, 0)
      assert(depths == Map(0 -> 0, 10 -> 1, 20 -> 1, 21 -> 2, 30 -> 2))
      assert(predecessors == Map(30 -> 10, 10 -> 0, 21 -> 20, 20 -> 0))
    }
    "for case with cycles" in {
      val edges = Map(
        4 -> Seq(3),
        3 -> Seq(2),
        2 -> Seq(1),
        1 -> Seq(3, 0),
        0 -> Seq()
      )
      val (depths, predecessors) = dijkstra(edges, 4)
      assert(depths == Map(4 -> 0, 3 -> 1, 2 -> 2, 1 -> 3, 0 -> 4))
    }
  }
}
