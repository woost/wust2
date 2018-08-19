package wust.graph

import org.scalatest._

class HierarchicalTopologicalSortSpec extends FreeSpec with MustMatchers {

  def tsort(vs: List[Int], succ: Int => Iterable[Int] = _ => Nil, children: Int => Iterable[Int] = _ => Nil) = HierarchicalTopologicalSort(vs, succ, children)
  implicit def edgeSetToAdjacencyList(es: Set[(Int, Int)]): Map[Int, Set[Int]] = es.groupBy(_._1).mapValues(_.map(_._2)).withDefaultValue(Set.empty)

  "Hierarchical Topological Sort" - {
    "classic topological sort" - {
      def testBoth(vs: List[Int], es: Int => Iterable[Int], results: List[Int]*) = {
        results must contain (tsort(vs, succ = es))
        results must contain (tsort(vs, children = es))
        results must contain (tsort(vs, succ = es, children = es))
      }

      "empty" in {
        tsort(Nil) mustEqual Nil
      }

      "1 vertex" in {
        tsort(List(1)) mustEqual List(1)
      }

      "2 vertices" in {
        testBoth(List(1, 2), Set(1 -> 2), List(1, 2))
        testBoth(List(1, 2), Set(2 -> 1), List(2, 1))
      }

      "3 vertices: trivial cases" in {
        testBoth(List(1, 2, 3), Set(1 -> 2, 2 -> 3),
          List(1, 2, 3))
        testBoth(List(1, 2, 3), Set(1 -> 2),
          List(1, 2, 3), List(1, 3, 2), List(3, 1, 2))
        testBoth(List(1, 2, 3), Set(1 -> 2, 1 -> 3),
          List(1, 2, 3), List(1, 3, 2))
        testBoth(List(1, 2, 3), Set(1 -> 2, 1 -> 3, 2 -> 3),
          List(1, 2, 3))
      }

      "3 vertices: cycles" in {
        testBoth(List(1, 2, 3), Set(1 -> 2, 2 -> 3, 3 -> 2),
          List(1, 2, 3), List(1, 3, 2))
        testBoth(List(1, 2, 3), Set(1 -> 2, 2 -> 1, 2 -> 3),
          List(1, 2, 3), List(2, 1, 3))
      }
    }

    "mixed edge types" - {
      "3 vertices: connection and containment" in {
        tsort(List(1, 2, 3), succ = Set(1 -> 2), children = Set(1 -> 3)) mustEqual List(1, 2, 3)
      }
      "4 vertices: order parents" in {
        tsort(List(1, 2, 3, 4), succ = Set(1 -> 3), children = Set(1 -> 2, 3 -> 4)) mustEqual List(1, 2, 3, 4)
      }
      "4 vertices: order children" in {
        tsort(List(1, 2, 3, 4), succ = Set(2 -> 4), children = Set(1 -> 2, 3 -> 4)) mustEqual List(1, 2, 3, 4)
      }
      "4 vertices: prioritize parent order over child order" in {
        tsort(List(1, 2, 3, 4), succ = Set(1 -> 3, 4 -> 2), children = Set(1 -> 2, 3 -> 4)) mustEqual List(1, 3, 4, 2)
      }
      "edge conflicts" - {
        "2 vertices: prioritize containment over connection" in {
          tsort(List(1, 2), succ = Set(2 -> 1), children = Set(1 -> 2)) mustEqual List(1, 2)
        }
      }
    }
  }
}
