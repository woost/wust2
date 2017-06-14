package wust.graph

import org.scalatest._
import wust.ids._
import wust.util._

import scala.collection.breakOut

class HierarchicalTopologicalSortSpec extends FreeSpec with MustMatchers {
  // implicit def intToUuidType(id: Int): UuidType = id.toString
  // implicit def intToPostId(id: Int): PostId = PostId(id.toString)
  // implicit def intToPost(id: Int): Post = Post(id.toString, "title")
  // implicit def intTupleToConnection(ts: (Int, Int)): Connection = Connection(ts)
  // implicit def intTupleToContainment(ts: (Int, Int)): Containment = Containment(ts)
  // implicit def intSetToSelectorIdSet(set: Set[Int]): Selector.IdSet = Selector.IdSet(set.map(id => PostId(id.toString)))
  // def PostIds(ids: Int*): Set[PostId] = ids.map(id => PostId(id.toString))(breakOut)
  // implicit class RichContainment(con: Containment) {
  //   def toLocal = LocalContainment(con.parentId, con.childId)
  // }
  // def Containment(ts: (Int, Int)): Containment = new Containment(ts._1.toString, ts._2.toString)
  // def Connection(ts: (Int, Int)): Connection = new Connection(ts._1.toString, ts._2.toString)
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
        tsort(List(1, 2, 3), succ = Set(1 -> 2), children = Set(1 -> 3)) mustEqual List(1, 3, 2)
      }
      "4 vertices: order parents" in {
        tsort(List(1, 2, 3, 4), succ = Set(1 -> 3), children = Set(1 -> 2, 3 -> 4)) mustEqual List(1, 2, 3, 4)
      }
      "4 vertices: order children" in {
        tsort(List(1, 2, 3, 4), succ = Set(2 -> 4), children = Set(1 -> 2, 3 -> 4)) mustEqual List(1, 2, 3, 4)
      }
      "4 vertices: prioritize parent order over child order" in {
        pending
        tsort(List(1, 2, 3, 4), succ = Set(1 -> 3, 4 -> 2), children = Set(1 -> 2, 3 -> 4)) mustEqual List(1, 2, 3, 4)
      }
      "edge conflicts" - {
        "2 vertices: prioritize containment over connection" in {
          tsort(List(1, 2), succ = Set(2 -> 1), children = Set(1 -> 2)) mustEqual List(1, 2)
        }
      }
    }
  }
}
