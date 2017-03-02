package graph

import org.scalatest._
import util.collection._

class GraphSpec extends FreeSpec with MustMatchers {
  val edgeId: () => Long = {
    var id = 1000
    () => { id += 1; id } // die kollidieren mit posts. wir brauchen für jeden typ ne eigene range
  }

  implicit def idToPost(id: Int): Post = Post(id, "")
  implicit def postListToMap(posts: List[Int]) = posts.map(idToPost).by(_.id)
  implicit def tupleIsContains(t: (Int, Int)): Contains = Contains(edgeId(), t._1, t._2)
  implicit def containsListIsMap(contains: List[(Int, Int)]): Map[Long, Contains] = contains.map(tupleIsContains).by(_.id)
  implicit def tupleIsConnects(t: (Int, Int)): Connects = Connects(edgeId(), t._1, t._2)
  implicit def connectsListIsMap(contains: List[(Int, Int)]): Map[Long, Connects] = contains.map(tupleIsConnects).by(_.id)

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

    "consistent on consistent graph" in {
      val graph = Graph(List(1, 2, 3), List(1 -> 2, 2 -> 3, 3 -> 1), List(1 -> 2, 2 -> 3, 3 -> 1))
      graph.consistent mustEqual graph
    }

    "consistent on inconsistent graph" in {
      val connects: Map[AtomId, Connects] = List(1 -> 2, 2 -> 3)
      val contains: Map[AtomId, Contains] = List(1 -> 2, 2 -> 3)
      val graph = Graph(List(1, 2, 3), connects + (100L -> Connects(100, 4, 1)), contains + (101L -> Contains(101, 3, 5)))
      graph.consistent mustEqual Graph(List(1, 2, 3), connects, contains)
    }

    "add post" in {
      val posts: Map[AtomId, Post] = List(1, 2, 3)
      val newPost = Post(99, "hans")
      val graph = Graph(posts, List(1 -> 2, 2 -> 3), List(1 -> 2, 2 -> 3))
      (graph + newPost) mustEqual Graph(posts + (newPost.id -> newPost), graph.connections, graph.containments)
    }

    "add connects" in {
      val connects: Map[AtomId, Connects] = List(1 -> 2, 2 -> 3)
      val newConnects = Connects(99, 3, 1)
      val graph = Graph(List(1, 2, 3), connects, List(1 -> 2, 2 -> 3))
      (graph + newConnects) mustEqual Graph(graph.posts, connects + (newConnects.id -> newConnects), graph.containments)
    }

    "add contains" in {
      val contains: Map[AtomId, Contains] = List(1 -> 2, 2 -> 3)
      val newContains = Contains(99, 3, 1)
      val graph = Graph(List(1, 2, 3), List(1 -> 2, 2 -> 3), contains)
      (graph + newContains) mustEqual Graph(graph.posts, graph.connections, contains + (newContains.id -> newContains))
    }

    "remove post" in {
      val connects: Map[AtomId, Connects] = Map(10L -> Connects(10, 1, 2), 11L -> Connects(11, 2, 3))
      val contains: Map[AtomId, Contains] = Map(20L -> Contains(20, 1, 2), 21L -> Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph removePost 1) mustEqual Graph(List(2, 3), Map(11L -> Connects(11, 2, 3)), Map(21L -> Contains(21, 2, 3)))
    }

    "remove non-existing post" in {
      val connects: Map[AtomId, Connects] = Map(10L -> Connects(10, 1, 2), 11L -> Connects(11, 2, 3))
      val contains: Map[AtomId, Contains] = Map(20L -> Contains(20, 1, 2), 21L -> Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph removePost 4) mustEqual graph
    }

    "remove connects" in {
      val connects: Map[AtomId, Connects] = Map(10L -> Connects(10, 1, 2), 11L -> Connects(11, 2, 3))
      val contains: Map[AtomId, Contains] = Map(20L -> Contains(20, 1, 2), 21L -> Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph removeConnection 11) mustEqual Graph(graph.posts, Map(10L -> Connects(10, 1, 2)), graph.containments)
    }

    "remove non-existing connects" in {
      val connects: Map[AtomId, Connects] = Map(10L -> Connects(10, 1, 2), 11L -> Connects(11, 2, 3))
      val contains: Map[AtomId, Contains] = Map(20L -> Contains(20, 1, 2), 21L -> Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph removeConnection 30) mustEqual graph
    }

    "remove contains" in {
      val connects: Map[AtomId, Connects] = Map(10L -> Connects(10, 1, 2), 11L -> Connects(11, 2, 3))
      val contains: Map[AtomId, Contains] = Map(20L -> Contains(20, 1, 2), 21L -> Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph removeContainment 21) mustEqual Graph(graph.posts, graph.connections, Map(20L -> Contains(20, 1, 2)))
    }

    "remove non-existing contains" in {
      val connects: Map[AtomId, Connects] = Map(10L -> Connects(10, 1, 2), 11L -> Connects(11, 2, 3))
      val contains: Map[AtomId, Contains] = Map(20L -> Contains(20, 1, 2), 21L -> Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph removeContainment 30) mustEqual graph
    }
  }
}
