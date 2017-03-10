package wust.graph

import org.scalatest._
import wust.util.collection._

class GraphSpec extends FreeSpec with MustMatchers {
  val edgeId: () => Long = {
    var id = 1000
    () => { id += 1; id } // die kollidieren mit posts. wir brauchen für jeden typ ne eigene range
  }

  implicit def tupleConnects(t:(Long, Connects)): (ConnectsId, Connects) = (ConnectsId(t._1), t._2)
  implicit def tupleContains(t:(Long, Contains)): (ContainsId, Contains) = (ContainsId(t._1), t._2)
  implicit def intToPostId(id:Int):PostId = PostId(id)
  implicit def intToConnectsId(id:Int):ConnectsId = ConnectsId(id)
  implicit def intToContainsId(id:Int):ContainsId = ContainsId(id)
  implicit def idToPost(id: Int): Post = Post(id, "")
  implicit def postListToMap(posts: List[Int]) = posts.map(idToPost).by(_.id)
  implicit def tupleIsContains(t: (Int, Int)): Contains = Contains(edgeId(), PostId(t._1), PostId(t._2))
  implicit def containsListIsMap(contains: List[(Int, Int)]): Map[ContainsId, Contains] = contains.map(tupleIsContains).by(_.id)
  implicit def tupleIsConnects(t: (Int, Int)): Connects = Connects(edgeId(), PostId(t._1), PostId(t._2))
  implicit def connectsListIsMap(contains: List[(Int, Int)]): Map[ConnectsId, Connects] = contains.map(tupleIsConnects).by(_.id)

  "atom id" - {
    "ordering" in {
      val list = Seq(PostId(3), ConnectsId(1), ContainsId(2), UnknownConnectableId(0))
      list.sorted mustEqual Seq(UnknownConnectableId(0), ConnectsId(1), ContainsId(2), PostId(3))
    }
  }

  "graph" - {
    "empty is empty" in {
      Graph.empty mustEqual Graph(Map.empty, Map.empty, Map.empty)
      Graph() mustEqual Graph(Map.empty, Map.empty, Map.empty)
    }

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
      graph.transitiveParents(3).toSet mustEqual Set(3, 2, 1).map(PostId(_))
    }

    "have transitive children in cycle" in {
      val graph = Graph(List(1, 2, 3), Map.empty, List(1 -> 2, 2 -> 3, 3 -> 1))
      graph.transitiveChildren(3).toSet mustEqual Set(3, 2, 1).map(PostId(_))
    }

    "consistent on consistent graph" in {
      val graph = Graph(List(1, 2, 3), List(1 -> 2, 2 -> 3, 3 -> 1), List(1 -> 2, 2 -> 3, 3 -> 1))
      graph.consistent mustEqual graph
    }

    "consistent translates unknown connectable id" in {
      val connects: Map[ConnectsId, Connects] = List(1 -> 2, 2 -> 3)
      val contains: Map[ContainsId, Contains] = List(1 -> 2, 2 -> 3)
      val newConnects = Connects(100, 2, UnknownConnectableId(1))
      val graph = Graph(List(1, 2, 3), connects + (newConnects.id -> newConnects), contains + (101L -> Contains(101, 3, 5)))
      graph.consistent mustEqual Graph(List(1, 2, 3), connects + (newConnects.id -> newConnects.copy(targetId = PostId(1))), contains)
    }

    "consistent on inconsistent graph" in {
      val connects: Map[ConnectsId, Connects] = List(1 -> 2, 2 -> 3)
      val newConnects = Connects(100, 2, PostId(1))
      val contains: Map[ContainsId, Contains] = List(1 -> 2, 2 -> 3)
      val graph = Graph(
        List(1, 2, 3),
        connects ++ Seq(newConnects, Connects(101, 4, newConnects.id)).by(_.id),
        contains + (102L -> Contains(101, 3, 5)))
      println(graph.consistent)
      graph.consistent mustEqual Graph(List(1, 2, 3), connects + (newConnects.id -> newConnects), contains)
    }

    "consistent on inconsistent graph (reverse)" ignore { //TODO
      val connects: Map[ConnectsId, Connects] = List(1 -> 2, 2 -> 3)
      val contains: Map[ContainsId, Contains] = List(1 -> 2, 2 -> 3)
      val graph = Graph(
        List(1, 2, 3),
        connects ++ Seq(Connects(100, 4, PostId(1)), Connects(101, 1, ConnectsId(100))).by(_.id),
        contains + (102L -> Contains(101, 3, 5)))
      println(graph.consistent)
      graph.consistent mustEqual Graph(List(1, 2, 3), connects, contains)
    }

    "post without id" in {
      Post("title").id mustEqual PostId(0)
    }

    "connects without id" in {
      Connects(1, ConnectsId(2)).id mustEqual ConnectsId(0)
    }

    "contains without id" in {
      Contains(1, 2).id mustEqual ContainsId(0)
    }

    "add post" in {
      val posts: Map[PostId, Post] = List(1, 2, 3)
      val newPost = Post(99, "hans")
      val graph = Graph(posts, List(1 -> 2, 2 -> 3), List(1 -> 2, 2 -> 3))
      (graph + newPost) mustEqual Graph(posts + (newPost.id -> newPost), graph.connections, graph.containments)
    }

    "add connects" in {
      val connects: Map[ConnectsId, Connects] = List(1 -> 2, 2 -> 3)
      val newConnects = Connects(99, 3, PostId(1))
      val graph = Graph(List(1, 2, 3), connects, List(1 -> 2, 2 -> 3))
      (graph + newConnects) mustEqual Graph(graph.posts, connects + (newConnects.id -> newConnects), graph.containments)
    }

    "add hyper connects" in {
      val connects = Seq(Connects(99, 3, PostId(1))).by(_.id)
      val hyper = Connects(100, 1, ConnectsId(99))
      val graph = Graph(List(1, 2, 3), connects, List(1 -> 2, 2 -> 3))
      (graph + hyper) mustEqual Graph(graph.posts, connects + (hyper.id -> hyper), graph.containments)
    }

    "add contains" in {
      val contains: Map[ContainsId, Contains] = List(1 -> 2, 2 -> 3)
      val newContains = Contains(99, 3, 1)
      val graph = Graph(List(1, 2, 3), List(1 -> 2, 2 -> 3), contains)
      (graph + newContains) mustEqual Graph(graph.posts, graph.connections, contains + (newContains.id -> newContains))
    }

    "add multiple atoms" in {
      val newPost = Post(99, "hans")
      val newConnects = Connects(99, 3, PostId(1))
      val newContains = Contains(99, 3, 1)
      val newAtoms = Seq(newPost, newConnects, newContains)
      val graph = Graph(List(1, 2, 3), List(1 -> 2, 2 -> 3), List(1 -> 2, 2 -> 3))
      (graph ++ newAtoms) mustEqual Graph(
        graph.posts + (newPost.id -> newPost),
        graph.connections + (newConnects.id -> newConnects),
        graph.containments + (newContains.id -> newContains))
    }

    "remove post" in {
      val connects: Map[ConnectsId, Connects] = Map(10L -> Connects(10, 1, PostId(2)), 11L -> Connects(11, 2, PostId(3)))
      val contains: Map[ContainsId, Contains] = Map(20L -> Contains(20, 1, 2), 21L -> Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph - PostId(1)) mustEqual Graph(List(2, 3), Map(11L -> Connects(11, 2, PostId(3))), Map(21L -> Contains(21, 2, 3)))
    }

    "remove non-existing post" in {
      val connects: Map[ConnectsId, Connects] = Map(10L -> Connects(10, 1, PostId(2)), 11L -> Connects(11, 2, PostId(3)))
      val contains: Map[ContainsId, Contains] = Map(20L -> Contains(20, 1, 2), 21L -> Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph - PostId(4)) mustEqual graph
    }

    "remove connects" in {
      val connects: Map[ConnectsId, Connects] = Map(10L -> Connects(10, 1, PostId(2)), 11L -> Connects(11, 2, PostId(3)))
      val contains: Map[ContainsId, Contains] = Map(20L -> Contains(20, 1, 2), 21L -> Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph - ConnectsId(11)) mustEqual Graph(graph.posts, Map(10L -> Connects(10, 1, PostId(2))), graph.containments)
    }

    "remove non-existing connects" in {
      val connects: Map[ConnectsId, Connects] = Map(10L -> Connects(10, 1, PostId(2)), 11L -> Connects(11, 2, PostId(3)))
      val contains: Map[ContainsId, Contains] = Map(20L -> Contains(20, 1, 2), 21L -> Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph - ConnectsId(30)) mustEqual graph
    }

    "remove contains" in {
      val connects: Map[ConnectsId, Connects] = Map(10L -> Connects(10, 1, PostId(2)), 11L -> Connects(11, 2, PostId(3)))
      val contains: Map[ContainsId, Contains] = Map(20L -> Contains(20, 1, 2), 21L -> Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph - ContainsId(21)) mustEqual Graph(graph.posts, graph.connections, Map(20L -> Contains(20, 1, 2)))
    }

    "remove non-existing contains" in {
      val connects: Map[ConnectsId, Connects] = Map(10L -> Connects(10, 1, PostId(2)), 11L -> Connects(11, 2, PostId(3)))
      val contains: Map[ContainsId, Contains] = Map(20L -> Contains(20, 1, 2), 21L -> Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph - ContainsId(30)) mustEqual graph
    }

    "remove multiple atoms" in {
      val connects: Map[ConnectsId, Connects] = Map(10L -> Connects(10, 1, PostId(2)), 11L -> Connects(11, 2, PostId(3)))
      val contains: Map[ContainsId, Contains] = Map(20L -> Contains(20, 1, 2), 21L -> Contains(21, 2, 3))
      val delAtoms = Seq(PostId(1), ConnectsId(11), ContainsId(21))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph -- delAtoms) mustEqual Graph(List(2, 3))
    }
  }
}
