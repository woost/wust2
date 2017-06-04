package wust.graph

import org.scalatest._
import wust.ids._
import wust.util.collection._

class GraphSpec extends FreeSpec with MustMatchers {
  implicit def intToUuidType(id: Int): UuidType = id.toString
  implicit def intToPostId(id: Int): PostId = PostId(id.toString)
  implicit def intToGroupId(id: Int): GroupId = GroupId(id)
  implicit def idToPost(id: Int): Post = Post(id.toString, "")
  implicit def idToGroup(id: Int): Group = Group(id)
  implicit def postListToMap(posts: List[Int]): List[Post] = posts.map(idToPost)
  implicit def tupleIsContainment(t: (Int, Int)): Containment = Containment(t._1.toString, t._2.toString)
  implicit def containmentListIsMap(containment: List[(Int, Int)]): List[Containment] = containment.map(tupleIsContainment)
  implicit def tupleIsConnection(t: (Int, Int)): Connection = Connection(t._1.toString, t._2.toString)
  implicit def connectionListIsMap(connections: List[(Int, Int)]): List[Connection] = connections.map(tupleIsConnection)

  "graph" - {
    "empty is empty" in {
      Graph.empty.postsById mustBe empty
      Graph.empty.connections mustBe empty
      Graph.empty.containments mustBe empty

      Graph.empty.posts mustBe empty

      Graph().postsById mustBe empty
      Graph().connections mustBe empty
      Graph().containments mustBe empty

      Graph().posts mustBe empty
    }

    "directed cycle" in {
      val graph = Graph(
        posts = List(Post(1, "title"), Post(11, "title2"), Post(12, "test3")),
        connections = Nil,
        containments = List(Containment(1, 11), Containment(11, 12), Containment(12, 1))
      )

      graph.involvedInContainmentCycle(1) mustEqual true
    }

    "one contain" in {
      val graph = Graph(List(Post(1, "title"), Post(11, "title2")), Nil, List(Containment(1, 11)))
      graph.involvedInContainmentCycle(1) mustEqual false
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

    "consistent on inconsistent graph" in {
      val connection: List[Connection] = List(1 -> 2, 2 -> 3)
      val newConnection = Connection(2, 1)
      val containment: List[Containment] = List(1 -> 2, 2 -> 3)
      val graph = Graph(
        List(1, 2, 3),
        connection ++ Seq(newConnection),
        containment :+ Containment(3, 5)
      )
      graph.consistent mustEqual Graph(List(1, 2, 3), connection :+ newConnection, containment)
    }

    "consistent on graph with self-loops" in {
      val connection: List[Connection] = List(1 -> 2, 2 -> 3, 4 -> 4)
      val containment: List[Containment] = List(1 -> 2, 2 -> 3, 5 -> 5)
      val graph = Graph(
        List(1, 2, 3, 4, 5),
        connection, containment
      )
      graph.consistent mustEqual Graph(List(1, 2, 3, 4, 5), List[Connection](1 -> 2, 2 -> 3), List[Containment](1 -> 2, 2 -> 3))
    }

    "consistent on inconsistent graph with ownership" in {
      val graph = Graph(
        List(1, 2, 3),
        groups = List(1),
        ownerships = List(Ownership(postId = 4, groupId = 1), Ownership(postId = 1, groupId = 2))
      )
      graph.consistent mustEqual graph.copy(ownerships = Set.empty)
      (graph + idToPost(4)).consistent mustEqual (graph + idToPost(4)).copy(ownerships = Set(Ownership(postId = 4, groupId = 1)))
      graph.copy(groupsById = Seq[Group](1, 2).by(_.id)).consistent mustEqual graph.copy(groupsById = Seq[Group](1, 2).by(_.id), ownerships = Set(Ownership(postId = 1, groupId = 2)))
    }

    "consistent on inconsistent graph (reverse)" in {
      val connection: List[Connection] = List(1 -> 2, 2 -> 3)
      val containment: List[Containment] = List(1 -> 2, 2 -> 3)
      val graph = Graph(
        List(1, 2, 3),
        connection ++ Seq(Connection(4, 1), Connection(1, 100)),
        containment :+ Containment(3, 5)
      )
      graph.consistent mustEqual Graph(List(1, 2, 3), connection, containment)
    }

    "add post" in {
      val posts: List[Post] = List(1, 2, 3)
      val newPost = Post(99, "hans")
      val graph = Graph(posts, List(1 -> 2, 2 -> 3), List(1 -> 2, 2 -> 3))
      (graph + newPost) mustEqual Graph(posts :+ newPost, graph.connections, graph.containments)
    }

    "add connection" in {
      val connection: List[Connection] = List(1 -> 2, 2 -> 3)
      val newConnection = Connection(3, 1)
      val graph = Graph(List(1, 2, 3), connection, List(1 -> 2, 2 -> 3))
      (graph + newConnection) mustEqual Graph(graph.posts, connection :+ newConnection, graph.containments)
    }

    "add hyper connection" in {
      val connection = Seq(Connection(3, 1))
      val hyper = Connection(1, 99)
      val graph = Graph(List(1, 2, 3), connection, List(1 -> 2, 2 -> 3))
      (graph + hyper) mustEqual Graph(graph.posts, connection :+ hyper, graph.containments)
    }

    "add containment" in {
      val containment: List[Containment] = List(1 -> 2, 2 -> 3)
      val newContainment = Containment(3, 1)
      val graph = Graph(List(1, 2, 3), List(1 -> 2, 2 -> 3), containment)
      (graph + newContainment) mustEqual Graph(graph.posts, graph.connections, containment :+ newContainment)
    }

    "remove post" in {
      val connection: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containment: List[Containment] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connection, containment)
      (graph - PostId(1)) mustEqual Graph(List(2, 3), List(Connection(2, 3)), List(Containment(2, 3)))
    }

    "remove non-existing post" in {
      val connection: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containment: List[Containment] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connection, containment)
      (graph - PostId(4)) mustEqual graph
    }

    "remove one post with removePosts" in {
      val connection: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containment: List[Containment] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connection, containment)
      (graph removePosts Seq(PostId(1))) mustEqual Graph(List(2, 3), List(Connection(2, 3)), List(Containment(2, 3)))
    }

    "remove multiple posts" in {
      val connection: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containment: List[Containment] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3, 4), connection, containment)
      (graph removePosts Seq(PostId(1), PostId(4))) mustEqual Graph(List(2, 3), List(Connection(2, 3)), List(Containment(2, 3)))
    }

    "remove connection" in {
      val connection: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containment: List[Containment] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connection, containment)
      (graph - Connection(2, 3)) mustEqual Graph(graph.posts, List(Connection(1, 2)), graph.containments)
    }

    "remove non-existing connection" in {
      val connection: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containment: List[Containment] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connection, containment)
      (graph - Connection(5, 7)) mustEqual graph
    }

    "remove containment" in {
      val connection: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containment: List[Containment] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connection, containment)
      (graph - Containment(2, 3)) mustEqual Graph(graph.posts, graph.connections, List(Containment(1, 2)))
    }

    "remove non-existing containment" in {
      val connection: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containment: List[Containment] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connection, containment)
      (graph - Containment(17, 18)) mustEqual graph
    }

    "successors of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connection(1, 11), Connection(11, 12), Connection(12, 1), Connection(12, 13)),
        containments = List(Containment(12, 14))
      )

      graph.successors(PostId(12)) mustEqual Set(1, 13).map(PostId(_))
      graph.successors(PostId(13)) mustEqual Set.empty
    }

    "predecessors of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connection(1, 11), Connection(11, 12), Connection(12, 1), Connection(12, 13)),
        containments = List(Containment(12, 14))
      )

      graph.predecessors(PostId(12)) mustEqual Set(11).map(PostId(_))
      graph.predecessors(PostId(13)) mustEqual Set(12).map(PostId(_))
    }

    "neighbours of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connection(1, 11), Connection(11, 12), Connection(12, 1), Connection(12, 13)),
        containments = List(Containment(12, 14))
      )

      graph.neighbours(PostId(12)) mustEqual Set(1, 11, 13).map(PostId(_))
      graph.neighbours(PostId(13)) mustEqual Set(12).map(PostId(_))
    }

    "children of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connection(1, 14)),
        containments = List(Containment(1, 11), Containment(1, 12), Containment(13, 12))
      )

      graph.children(PostId(1)) mustEqual Set(11, 12).map(PostId(_))
      graph.children(PostId(12)) mustEqual Set.empty
    }

    "parents of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connection(1, 14)),
        containments = List(Containment(1, 11), Containment(1, 12), Containment(13, 12))
      )

      graph.parents(PostId(1)) mustEqual Set.empty
      graph.parents(PostId(12)) mustEqual Set(1, 13).map(PostId(_))
    }

    "containment neighbours of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connection(1, 14)),
        containments = List(Containment(1, 11), Containment(1, 12), Containment(13, 12))
      )

      graph.containmentNeighbours(PostId(1)) mustEqual Set(11, 12).map(PostId(_))
      graph.containmentNeighbours(PostId(12)) mustEqual Set(1, 13).map(PostId(_))
    }
  }
}
