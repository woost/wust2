package wust.util

import flatland._
import org.scalatest._
import org.scalatest.matchers._
import wust.util.algorithm._
import org.scalatest.freespec.AnyFreeSpec

class AlgorithmsSpec extends AnyFreeSpec with must.Matchers {

  "connected components" - {
    "one vertex" in {
      val components = connectedComponents[Int](List(0), _ => Nil)
      components must contain theSameElementsAs List(Set(0))
    }

    "two isolated vertices" in {
      val components = connectedComponents[Int](List(0, 1), _ => Nil)
      components must contain theSameElementsAs List(Set(0), Set(1))
    }

    "cycles" in {
      val edges = Map(
        0 -> Seq(1, 2, 3),
        1 -> Seq(0, 2, 3),
        2 -> Seq(0, 1, 3),
        3 -> Seq(0, 1, 2),
        4 -> Seq(5),
        5 -> Seq(4)
      )

      val components = connectedComponents[Int](edges.keys, edges)
      components must contain theSameElementsAs List(Set(0, 1, 2, 3), Set(4, 5))
    }
  }

  "topological sort" - {
    def test(vertices: Array[Int], edges: NestedArrayInt): Unit = {
      val result = topologicalSort(vertices, edges)
      vertices.foreach(v => assert(result contains v))
      edges.zipWithIndex.foreach{
        case (successors, v) if result.indexOf(v) != -1 =>
          successors.foreach{ s =>
            assert(result.indexOf(s) != -1)
            //          println(s"$v < $s")
            assert(result.indexOf(v) < result.indexOf(s), s": $v was not before $s : ${result.toList}")
          }
      }
    }

    "empty" in {
      val list = topologicalSort(Array[Int](), NestedArrayInt(Array[Array[Int]]()))
      assert(list.toList == List())
    }

    "one vertex" in {
      val list = topologicalSort(Array(0), NestedArrayInt(Array[Array[Int]](Array())))
      assert(list.toList == List(0))
    }

    "with successor" in {
      val list = topologicalSort(Array(0, 1), NestedArrayInt(Array(Array(1), Array(1)))).toList
      assert(list.toList == List(0, 1))
    }

    "tolerate directed cycle" in {
      // 0 -> 1 -> 2 -> 3
      //      ^    |
      //      |    |
      //      +----+
      val edges = NestedArrayInt(Array(
        Array(1),
        Array(2),
        Array(1, 3),
        Array[Int]()
      ))

      val list = topologicalSort(Array(0, 1, 2, 3), edges).toList
      assert(list == List(0, 2, 1, 3) || list == List(0, 1, 2, 3))
    }

    "stable" - {

      "test 0" in {
        // 7<--1-->2-->3<--0
        // 5 -> 6
        //
        // 0 -> 3
        // 1 -> 2,7 -> 3
        // 5 -> 6
        // 4,7
        val vertices = Array(3, 0, 2, 1, 6, 4, 5, 7)
        val edges = NestedArrayInt(Array(
          /* 0 */ Array(3),
          /* 1 */ Array(2),
          /* 2 */ Array(3, 7),
          /* 3 */ Array[Int](),
          /* 4 */ Array[Int](),
          /* 5 */ Array(6),
          /* 6 */ Array[Int](),
          /* 7 */ Array[Int](),
        ))

        test(vertices, edges)
        val result = topologicalSort(vertices, edges)
        assert(result.toList == List(0, 1, 2, 3, 4, 5, 6, 7))
      }

      "test 1" in {
        // ... 1 -> 2 -> 3 ...
        val edges = NestedArrayInt(Array(
          Array[Int](),
          Array(2),
          Array(3),
          Array[Int](),
          Array[Int](),
        ))

        val list = topologicalSort(Array(0, 3, 1, 2, 4), edges).toList
        assert(list == List(0, 1, 2, 3, 4))
      }

      "test 2" in {
        val edges = NestedArrayInt(Array(
          Array[Int](),
          Array(2),
          Array(3),
          Array[Int](),
          Array[Int](),
        ))

        val list = topologicalSort(Array(0, 3, 1, 2, 4), edges).toList
        assert(list == List(0, 1, 2, 3, 4))
      }

      "test 3" in {
        val edges = NestedArrayInt(Array(
          Array(1),
          Array[Int](),
          Array[Int](),
          Array(1, 2),
          Array[Int](),
        ))

        val list = topologicalSort(Array(0, 2, 3, 1, 4), edges).toList
        assert(list == List(0, 3, 2, 1, 4))
      }

      "test 4" in {
        val edges = NestedArrayInt(Array(
          Array(1),
          Array[Int](),
          Array(1, 3),
          Array[Int](),
          Array[Int](),
        ))

        val list = topologicalSort(Array(0, 2, 3, 4, 1), edges).toList
        assert(list == List(0, 2, 3, 4, 1))
      }

      "test 5" in {
        val edges = NestedArrayInt(Array(
          Array(2),
          Array[Int](),
          Array(1),
          Array[Int](),
          Array[Int](),
        ))

        val list = topologicalSort(Array(0, 3, 2, 1, 4), edges).toList
        assert(list == List(0, 3, 2, 1, 4))
      }
    }
  }

  "longest path for every node" - {
    "empty" in {
      val edges = NestedArrayInt(Array[Array[Int]]())
      val lengths = longestPathsIdx(edges)
      assert(lengths.length == 0)
    }
    "no edges" in {
      val edges = NestedArrayInt(Array(Array[Int](), Array[Int](), Array[Int]()))
      val lengths = longestPathsIdx(edges)
      assert(lengths.toList == List(0, 0, 0))
    }
    "simple case" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1),
        /* 1 */ Array[Int](2),
        /* 2 */ Array[Int]()
      ))
      val lengths = longestPathsIdx(edges)
      assert(lengths.toList == List(2, 1, 0))
    }
    "diamond case" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1, 2),
        /* 1 */ Array[Int](2),
        /* 2 */ Array[Int]()
      ))
      val lengths = longestPathsIdx(edges)
      assert(lengths.toList == List(2, 1, 0))
    }
    "simple cycle case" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1),
        /* 1 */ Array[Int](0, 3),
        /* 2 */ Array[Int](0),
        /* 3 */ Array[Int]()
      ))
      val lengths = shortestPathsIdx(edges)
      assert(lengths.toList == List(2, 1, 3, 0))
    }
    "complex cycle case" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1, 2, 5),
        /* 1 */ Array[Int](3),
        /* 2 */ Array[Int](1),
        /* 3 */ Array[Int](0, 2),
        /* 4 */ Array[Int](1), // before cycle
        /* 5 */ Array[Int]()
      ) // after cycle
      )
      val lengths = longestPathsIdx(edges)
      assert(lengths.toList == List(4, 3, 1, 2, 4, 0)) // mostly undefined, but does not crash
    }
  }

  "shortest path for every node" - {
    "empty" in {
      val edges = NestedArrayInt(Array[Array[Int]]())
      val lengths = shortestPathsIdx(edges)
      assert(lengths.length == 0)
    }
    "no edges" in {
      val edges = NestedArrayInt(Array(Array[Int](), Array[Int](), Array[Int]()))
      val lengths = shortestPathsIdx(edges)
      assert(lengths.toList == List(0, 0, 0))
    }
    "simple case" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1),
        /* 1 */ Array[Int](2),
        /* 2 */ Array[Int]()
      ))
      val lengths = shortestPathsIdx(edges)
      assert(lengths.toList == List(2, 1, 0))
    }
    "diamond case" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1, 2),
        /* 1 */ Array[Int](2),
        /* 2 */ Array[Int]()
      ))
      val lengths = shortestPathsIdx(edges)
      assert(lengths.toList == List(1, 1, 0))
    }
    "simple cycle case" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1),
        /* 1 */ Array[Int](0, 3),
        /* 2 */ Array[Int](0),
        /* 3 */ Array[Int]()
      ))
      val lengths = shortestPathsIdx(edges)
      assert(lengths.toList == List(2, 1, 3, 0))
    }
    "complex cycle case" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1, 2, 5),
        /* 1 */ Array[Int](3),
        /* 2 */ Array[Int](1),
        /* 3 */ Array[Int](0, 2),
        /* 4 */ Array[Int](1), // before cycle
        /* 5 */ Array[Int]()
      ) // after cycle
      )
      val lengths = shortestPathsIdx(edges)
      assert(lengths.toList == List(1, 2, 1, 1, 3, 0)) // mostly undefined, but does not crash
    }
  }

  "dijkstra" - {
    "for simple case" in {
      val (depths, predecessors) = dijkstra(Map(
        0 -> Seq(1),
        1 -> Seq()
      ), 0)
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

  "euler diagram dual graph" - {
    "full flat example" in {
      val parents = NestedArrayInt(Array(
        /* 0: A */ Array[Int](),
        /* 1: B */ Array[Int](),
        /* 2: C */ Array[Int](),
        /* 3: I */ Array[Int](),
        /* 4: 1 */ Array[Int](0, 2),
        /* 5: 2 */ Array[Int](0, 1),
        /* 6: 3 */ Array[Int](1),
        /* 7: 7 */ Array[Int](),
      ))
      val eulerSets = Set(0, 1, 2, 3)
      val children = parents.transposed
      val (eulerZones, eulerZoneNodes, edges) = eulerDiagramDualGraph(parents, children, eulerSets)
      eulerZones.toSet must contain theSameElementsAs Set(
        /* A  */ Set(0),
        /* AC */ Set(0, 2),
        /* C  */ Set(2),
        /* AB */ Set(0, 1),
        /* B  */ Set(1),
        /* _  */ Set(),
        /* I  */ Set(3),
      )
      /* A  -> A  */ eulerZoneNodes(eulerZones.indexOf(Set(0))).toSet mustEqual Set(0)
      /* AC -> 1  */ eulerZoneNodes(eulerZones.indexOf(Set(0, 2))).toSet mustEqual Set(4)
      /* C  -> C  */ eulerZoneNodes(eulerZones.indexOf(Set(2))).toSet mustEqual Set(2)
      /* AB -> A  */ eulerZoneNodes(eulerZones.indexOf(Set(0, 1))).toSet mustEqual Set(5)
      /* B  -> B3 */ eulerZoneNodes(eulerZones.indexOf(Set(1))).toSet mustEqual Set(6, 1)
      /* _  -> I7 */ eulerZoneNodes(eulerZones.indexOf(Set())).toSet mustEqual Set(7)
      /* I  -> I  */ eulerZoneNodes(eulerZones.indexOf(Set(3))).toSet mustEqual Set(3)

      edges.map{ case (a, b) => Set(a, b) }.toSet must contain theSameElementsAs Set(
        Set(eulerZones.indexOf(Set(2)), eulerZones.indexOf(Set(0, 2))),
        Set(eulerZones.indexOf(Set(0, 2)), eulerZones.indexOf(Set(0))),
        Set(eulerZones.indexOf(Set(0)), eulerZones.indexOf(Set(0, 1))),
        Set(eulerZones.indexOf(Set(0, 1)), eulerZones.indexOf(Set(1))),
      )
    }

    "full nested example" in {
      val parents = NestedArrayInt(Array(
        /* 0: D */ Array[Int](),
        /* 1: E */ Array[Int](0),
        /* 2: F */ Array[Int](0),
        /* 3: G */ Array[Int](0),
        /* 4: H */ Array[Int](),
        /* 5: 4 */ Array[Int](3),
        /* 6: 5 */ Array[Int](1, 3),
        /* 7: 6 */ Array[Int](1, 4),
        /* 8: 8 */ Array[Int](0),
        /* 9: 9 */ Array[Int](0, 1),
      ))
      val eulerSets = Set(0, 1, 2, 3, 4)
      val children = parents.transposed
      val (eulerZones, eulerZoneNodes, edges) = eulerDiagramDualGraph(parents, children, eulerSets)
      eulerZones.toSet must contain theSameElementsAs Set(
        /* D  */ Set(0),
        /* F  */ Set(2),
        /* G  */ Set(3),
        /* EG */ Set(1, 3),
        /* EH */ Set(1, 4),
        /* H  */ Set(4),
        /* E  */ Set(1),
      )
      /* D  -> D8 */ eulerZoneNodes(eulerZones.indexOf(Set(0))).toSet mustEqual Set(8, 0)
      /* F  -> F  */ eulerZoneNodes(eulerZones.indexOf(Set(2))).toSet mustEqual Set(2)
      /* G  -> G4 */ eulerZoneNodes(eulerZones.indexOf(Set(3))).toSet mustEqual Set(3, 5)
      /* EG -> 5  */ eulerZoneNodes(eulerZones.indexOf(Set(1, 3))).toSet mustEqual Set(6)
      /* EH -> 6  */ eulerZoneNodes(eulerZones.indexOf(Set(1, 4))).toSet mustEqual Set(7)
      /* H  -> H  */ eulerZoneNodes(eulerZones.indexOf(Set(4))).toSet mustEqual Set(4)
      /* E  -> E9 */ eulerZoneNodes(eulerZones.indexOf(Set(1))).toSet mustEqual Set(1, 9)

      edges.map{ case (a, b) => Set(eulerZones(a), eulerZones(b)) }.toSet must contain theSameElementsAs Set(
        //        Set(Set(0), Set(1)),
        //        Set(Set(0), Set(2)),
        //        Set(Set(0), Set(3)),
        Set(Set(3), Set(1, 3)),
        Set(Set(1, 3), Set(1)),
        Set(Set(1), Set(1, 4)),
        Set(Set(1, 4), Set(4)),
      )
    }
  }
}
