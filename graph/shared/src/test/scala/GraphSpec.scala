package wust.graph

import org.scalatest._
import wust.util.collection._

class GraphSpec extends FreeSpec with MustMatchers {
  val edgeId: () => Long = {
    var id = 1000
    () => { id += 1; id } // die kollidieren mit posts. wir brauchen für jeden typ ne eigene range
  }

  implicit def tupleConnects(t: (Long, Connects)): (ConnectsId, Connects) = (ConnectsId(t._1), t._2)
  implicit def tupleContains(t: (Long, Contains)): (ContainsId, Contains) = (ContainsId(t._1), t._2)
  implicit def intToPostId(id: Int): PostId = PostId(id)
  implicit def intToConnectsId(id: Int): ConnectsId = ConnectsId(id)
  implicit def intToContainsId(id: Int): ContainsId = ContainsId(id)
  implicit def idToPost(id: Int): Post = Post(id, "")
  implicit def postListToMap(posts: List[Int]) = posts.map(idToPost)
  implicit def tupleIsContains(t: (Int, Int)): Contains = Contains(edgeId(), PostId(t._1), PostId(t._2))
  implicit def containsListIsMap(contains: List[(Int, Int)]): List[Contains] = contains.map(tupleIsContains)
  implicit def tupleIsConnects(t: (Int, Int)): Connects = Connects(edgeId(), PostId(t._1), PostId(t._2))
  implicit def connectsListIsMap(contains: List[(Int, Int)]): List[Connects] = contains.map(tupleIsConnects)

  "atom id" - {
    "ordering" in {
      val list = Seq(PostId(3), ConnectsId(1), ContainsId(2), UnknownConnectableId(0))
      list.sorted mustEqual Seq(UnknownConnectableId(0), ConnectsId(1), ContainsId(2), PostId(3))
    }
  }

  "graph" - {
    "empty is empty" in {
      Graph.empty.postsById mustBe empty
      Graph.empty.connectionsById mustBe empty
      Graph.empty.containmentsById mustBe empty

      Graph.empty.posts mustBe empty
      Graph.empty.connections mustBe empty
      Graph.empty.containments mustBe empty

      Graph().postsById mustBe empty
      Graph().connectionsById mustBe empty
      Graph().containmentsById mustBe empty

      Graph().posts mustBe empty
      Graph().connections mustBe empty
      Graph().containments mustBe empty
    }

    "directed cycle" in {
      val graph = Graph(
        posts = List(Post(1, "title"), Post(11, "title2"), Post(12, "test3")),
        connections = Nil,
        containments = List(Contains(3, 1, 11), Contains(4, 11, 12), Contains(5, 12, 1))
      )

      graph.involvedInCycle(1L) mustEqual true
    }

    "one contain" in {
      val graph = Graph(List(Post(1, "title"), Post(11, "title2")), Nil, List(Contains(3, 1, 11)))
      graph.involvedInCycle(1) mustEqual false
    }

    "have transitive parents in cycle" in {
      val graph = Graph(List(1, 2, 3), Nil, List(1 -> 2, 2 -> 3, 3 -> 1))
      graph.transitiveParents(3).toSet mustEqual Set(3, 2, 1).map(PostId(_))
    }

    "have transitive children in cycle" in {
      val graph = Graph(List(1, 2, 3), Nil, List(1 -> 2, 2 -> 3, 3 -> 1))
      graph.transitiveChildren(3).toSet mustEqual Set(3, 2, 1).map(PostId(_))
    }

    "consistent on consistent graph" in {
      val graph = Graph(List(1, 2, 3), List(1 -> 2, 2 -> 3, 3 -> 1), List(1 -> 2, 2 -> 3, 3 -> 1))
      graph.consistent mustEqual graph
    }

    "consistent translates unknown connectable id" in {
      val connects: List[Connects] = List(1 -> 2, 2 -> 3)
      val contains: List[Contains] = List(1 -> 2, 2 -> 3)
      val newConnects = Connects(100, 2, UnknownConnectableId(1))
      val graph = Graph(List(1, 2, 3), connects :+ newConnects, contains :+ Contains(101, 3, 5))
      graph.consistent mustEqual Graph(List(1, 2, 3), connects :+ newConnects.copy(targetId = PostId(1)), contains)
    }

    "consistent on inconsistent graph" in {
      val connects: List[Connects] = List(1 -> 2, 2 -> 3)
      val newConnects = Connects(100, 2, PostId(1))
      val contains: List[Contains] = List(1 -> 2, 2 -> 3)
      val graph = Graph(
        List(1, 2, 3),
        connects ++ Seq(newConnects, Connects(101, 4, newConnects.id)),
        contains :+ Contains(102, 3, 5)
      )
      graph.consistent mustEqual Graph(List(1, 2, 3), connects :+ newConnects, contains)
    }

    "consistent on inconsistent graph (reverse)" in {
      val connects: List[Connects] = List(1 -> 2, 2 -> 3)
      val contains: List[Contains] = List(1 -> 2, 2 -> 3)
      val graph = Graph(
        List(1, 2, 3),
        connects ++ Seq(Connects(100, 4, PostId(1)), Connects(101, 1, ConnectsId(100))),
        contains :+ Contains(102, 3, 5)
      )
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
      val posts: List[Post] = List(1, 2, 3)
      val newPost = Post(99, "hans")
      val graph = Graph(posts, List(1 -> 2, 2 -> 3), List(1 -> 2, 2 -> 3))
      (graph + newPost) mustEqual Graph(posts :+ newPost, graph.connections, graph.containments)
    }

    "add connects" in {
      val connects: List[Connects] = List(1 -> 2, 2 -> 3)
      val newConnects = Connects(99, 3, PostId(1))
      val graph = Graph(List(1, 2, 3), connects, List(1 -> 2, 2 -> 3))
      (graph + newConnects) mustEqual Graph(graph.posts, connects :+ newConnects, graph.containments)
    }

    "add hyper connects" in {
      val connects = Seq(Connects(99, 3, PostId(1)))
      val hyper = Connects(100, 1, ConnectsId(99))
      val graph = Graph(List(1, 2, 3), connects, List(1 -> 2, 2 -> 3))
      (graph + hyper) mustEqual Graph(graph.posts, connects :+ hyper, graph.containments)
    }

    "add contains" in {
      val contains: List[Contains] = List(1 -> 2, 2 -> 3)
      val newContains = Contains(99, 3, 1)
      val graph = Graph(List(1, 2, 3), List(1 -> 2, 2 -> 3), contains)
      (graph + newContains) mustEqual Graph(graph.posts, graph.connections, contains :+ newContains)
    }

    "add multiple atoms" in {
      val newPost = Post(99, "hans")
      val newConnects = Connects(99, 3, PostId(1))
      val newContains = Contains(99, 3, 1)
      val newAtoms = Seq(newPost, newConnects, newContains)
      val graph = Graph(List(1, 2, 3), List(1 -> 2, 2 -> 3), List(1 -> 2, 2 -> 3))
      (graph ++ newAtoms) mustEqual Graph(
        graph.posts.toSeq :+ newPost,
        graph.connections.toSeq :+ newConnects,
        graph.containments.toSeq :+ newContains
      )
    }

    "remove post" in {
      val connects: List[Connects] = List(Connects(10, 1, PostId(2)), Connects(11, 2, PostId(3)))
      val contains: List[Contains] = List(Contains(20, 1, 2), Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph - PostId(1)) mustEqual Graph(List(2, 3), List(Connects(11, 2, PostId(3))), List(Contains(21, 2, 3)))
    }

    "remove non-existing post" in {
      val connects: List[Connects] = List(Connects(10, 1, PostId(2)), Connects(11, 2, PostId(3)))
      val contains: List[Contains] = List(Contains(20, 1, 2), Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph - PostId(4)) mustEqual graph
    }

    "remove connects" in {
      val connects: List[Connects] = List(Connects(10, 1, PostId(2)), Connects(11, 2, PostId(3)))
      val contains: List[Contains] = List(Contains(20, 1, 2), Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph - ConnectsId(11)) mustEqual Graph(graph.posts, List(Connects(10, 1, PostId(2))), graph.containments)
    }

    "remove non-existing connects" in {
      val connects: List[Connects] = List(Connects(10, 1, PostId(2)), Connects(11, 2, PostId(3)))
      val contains: List[Contains] = List(Contains(20, 1, 2), Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph - ConnectsId(30)) mustEqual graph
    }

    "remove contains" in {
      val connects: List[Connects] = List(Connects(10, 1, PostId(2)), Connects(11, 2, PostId(3)))
      val contains: List[Contains] = List(Contains(20, 1, 2), Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph - ContainsId(21)) mustEqual Graph(graph.posts, graph.connections, List(Contains(20, 1, 2)))
    }

    "remove non-existing contains" in {
      val connects: List[Connects] = List(Connects(10, 1, PostId(2)), Connects(11, 2, PostId(3)))
      val contains: List[Contains] = List(Contains(20, 1, 2), Contains(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph - ContainsId(30)) mustEqual graph
    }

    "remove multiple atoms" in {
      val connects: List[Connects] = List(Connects(10, 1, PostId(2)), Connects(11, 2, PostId(3)))
      val contains: List[Contains] = List(Contains(20, 1, 2), Contains(21, 2, 3))
      val delAtoms = Seq(PostId(1), ConnectsId(11), ContainsId(21))
      val graph = Graph(List(1, 2, 3), connects, contains)
      (graph -- delAtoms) mustEqual Graph(List(2, 3))
    }

    "successors of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connects(3, 1, PostId(11)), Connects(4, 11, PostId(12)), Connects(5, 12, PostId(1)), Connects(6, 12, PostId(13))),
        containments = List(Contains(3, 12, 14))
      )

      graph.successors(PostId(12)) mustEqual Set(1, 13).map(PostId(_))
      graph.successors(PostId(13)) mustEqual Set.empty
    }

    "predecessors of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connects(3, 1, PostId(11)), Connects(4, 11, PostId(12)), Connects(5, 12, PostId(1)), Connects(6, 12, PostId(13))),
        containments = List(Contains(3, 12, 14))
      )

      graph.predecessors(PostId(12)) mustEqual Set(11).map(PostId(_))
      graph.predecessors(PostId(13)) mustEqual Set(12).map(PostId(_))
    }

    "neighbours of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connects(3, 1, PostId(11)), Connects(4, 11, PostId(12)), Connects(5, 12, PostId(1)), Connects(6, 12, PostId(13))),
        containments = List(Contains(3, 12, 14))
      )

      graph.neighbours(PostId(12)) mustEqual Set(1, 11, 13).map(PostId(_))
      graph.neighbours(PostId(13)) mustEqual Set(12).map(PostId(_))
    }

    "children of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connects(2, 1, PostId(14))),
        containments = List(Contains(3, 1, 11), Contains(4, 1, 12), Contains(5, 13, 12))
      )

      graph.children(PostId(1)) mustEqual Set(11, 12).map(PostId(_))
      graph.children(PostId(12)) mustEqual Set.empty
    }

    "parents of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connects(2, 1, PostId(14))),
        containments = List(Contains(3, 1, 11), Contains(4, 1, 12), Contains(5, 13, 12))
      )

      graph.parents(PostId(1)) mustEqual Set.empty
      graph.parents(PostId(12)) mustEqual Set(1, 13).map(PostId(_))
    }

    "containment neighbours of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connects(2, 1, PostId(14))),
        containments = List(Contains(3, 1, 11), Contains(4, 1, 12), Contains(5, 13, 12))
      )

      graph.containmentNeighbours(PostId(1)) mustEqual Set(11, 12).map(PostId(_))
      graph.containmentNeighbours(PostId(12)) mustEqual Set(1, 13).map(PostId(_))
    }
  }
}
