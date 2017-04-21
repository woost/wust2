package wust.util

import org.scalatest._
import algorithm._

class AlgorithmsSpec extends FreeSpec with MustMatchers {

  "indexing" - {
    val edges = List(
      1 -> 2,
      2 -> 3,
      1 -> 3
    )
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

  "topological sort" - {
    "empty" - {
      val list = topologicalSort[Int, Seq](Seq.empty, _ => Seq.empty)
      assert(list == List())
    }

    "one vertex" - {
      val list = topologicalSort[Int, Seq](Seq(0), _ => Seq.empty)
      assert(list == List(0))
    }

    "with successor" - {
      val list = topologicalSort[Int, Seq](Seq(0, 1), _ => Seq(1)).toList
      assert(list == List(0, 1))
    }

    "tolerate directed cycle" - {
      val edges = Map(
        0 -> Seq(1),
        1 -> Seq(2),
        2 -> Seq(1, 3),
        3 -> Seq.empty
      )

      val list = topologicalSort[Int, Seq](Seq(0, 1, 2, 3), edges).toList
      assert(list == List(0, 1, 2, 3))
    }
  }

  "redundant spanning tree" - {
    "one vertex" - {
      val tree = redundantSpanningTree(1, (_: Int) => List.empty)
      assert(tree == Tree(1, List.empty))
    }

    "same children" in {
      val tree = redundantSpanningTree(1, (_: Int) => List(2, 3))
      assert(
        tree ==
          Tree(1,
               List(Tree(2, List(Tree(3, List.empty))),
                    Tree(3, List(Tree(2, List.empty))))))
    }

    "directed cycle" in {
      val edges = Map(
        0 -> Seq(1),
        1 -> Seq(2),
        2 -> Seq(1, 3),
        3 -> Seq.empty
      )

      val tree = redundantSpanningTree(0, edges)
      assert(
        tree ==
          Tree(0, List(Tree(1, List(Tree(2, List(Tree(3, List.empty))))))))
    }
  }
}
