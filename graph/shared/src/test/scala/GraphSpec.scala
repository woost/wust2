package graph

import org.scalatest._
import util.collection._

class GraphSpec extends FreeSpec with MustMatchers {
  val containsId: () => Long = {
    var id = 1000
    () => { id += 1; id } // die kollidieren mit posts. wir brauchen für jeden typ ne eigene range
  }

  implicit def idToPost(id: Int): Post = Post(id, "")
  implicit def postListToMap(posts: List[Int]) = posts.map(idToPost).by(_.id)
  implicit def tupleIsContains(t: (Int, Int)): Contains = Contains(containsId(), t._1, t._2)
  implicit def containsListIsMap(contains: List[(Int, Int)]): Map[Long, Contains] = contains.map(tupleIsContains).by(_.id)

  "graph" - {
    "directed cycle" in {
      val graph = Graph(
        posts = List(Post(1, "title"), Post(11, "title2"), Post(12, "test3")).by(_.id),
        connections = Map.empty,
        containments = List(Contains(3, 1, 11), Contains(4, 11, 12), Contains(5, 12, 1)).by(_.id)
      )

      graph.involvedInCycle(1L) mustEqual true
    }

    "one contain" in {
      val graph = Graph(List(Post(1, "title"), Post(11, "title2")).by(_.id), Map.empty, List(Contains(3, 1, 11)).by(_.id))

      graph.involvedInCycle(1) mustEqual false
    }

    "have transitive parents in cycle" in {
      val graph = Graph(List(1, 2, 3), Map.empty, List(1 -> 2, 2 -> 3, 3 -> 1))
      graph.transitiveParents(3).toSet mustEqual Set(3, 2, 1)
    }

    "have transitive children in cycle" in {
      val graph = Graph(List(1, 2, 3), Map.empty, List(1 -> 2, 2 -> 3, 3 -> 1))
      graph.transitiveChildren(3).toSet mustEqual Set(3, 2, 1)
    }
  }
}
