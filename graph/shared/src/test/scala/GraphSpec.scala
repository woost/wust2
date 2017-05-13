package wust.graph

import org.scalatest._
import wust.ids._
import wust.util.collection._

class GraphSpec extends FreeSpec with MustMatchers {
  val edgeId: () => Long = {
    var id = 1000
    () => { id += 1; id } // die kollidieren mit posts. wir brauchen für jeden typ ne eigene range
  }

  implicit def tupleConnection(t: (Long, Connection)): (ConnectionId, Connection) = (ConnectionId(t._1), t._2)
  implicit def tupleContainment(t: (Long, Containment)): (ContainmentId, Containment) = (ContainmentId(t._1), t._2)
  implicit def intToPostId(id: Int): PostId = PostId(id)
  implicit def intToGroupId(id: Int): GroupId = GroupId(id)
  implicit def intToConnectionId(id: Int): ConnectionId = ConnectionId(id)
  implicit def intToContainmentId(id: Int): ContainmentId = ContainmentId(id)
  implicit def idToPost(id: Int): Post = Post(id, "")
  implicit def idToGroup(id: Int): Group = Group(id)
  implicit def postListToMap(posts: List[Int]): List[Post] = posts.map(idToPost)
  implicit def tupleIsContainment(t: (Int, Int)): Containment = Containment(edgeId(), PostId(t._1), PostId(t._2))
  implicit def containmentListIsMap(containment: List[(Int, Int)]): List[Containment] = containment.map(tupleIsContainment)
  implicit def tupleIsConnection(t: (Int, Int)): Connection = Connection(edgeId(), PostId(t._1), PostId(t._2))
  implicit def connectionListIsMap(connections: List[(Int, Int)]): List[Connection] = connections.map(tupleIsConnection)

  "atom id" - {
    "ordering" in {
      val list = Seq(PostId(3), ConnectionId(1), ContainmentId(2), UnknownConnectableId(0))
      list.sorted mustEqual Seq(UnknownConnectableId(0), ConnectionId(1), ContainmentId(2), PostId(3))
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
        containments = List(Containment(3, 1, 11), Containment(4, 11, 12), Containment(5, 12, 1))
      )

      graph.involvedInContainmentCycle(1L) mustEqual true
    }

    "one contain" in {
      val graph = Graph(List(Post(1, "title"), Post(11, "title2")), Nil, List(Containment(3, 1, 11)))
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

    "consistent translates unknown connectable id" in {
      val connection: List[Connection] = List(1 -> 2, 2 -> 3)
      val containment: List[Containment] = List(1 -> 2, 2 -> 3)
      val newConnection = Connection(100, 2, UnknownConnectableId(1))
      val graph = Graph(List(1, 2, 3), connection :+ newConnection, containment :+ Containment(101, 3, 5))
      graph.consistent mustEqual Graph(List(1, 2, 3), connection :+ newConnection.copy(targetId = PostId(1)), containment)
    }

    "consistent on inconsistent graph" in {
      val connection: List[Connection] = List(1 -> 2, 2 -> 3)
      val newConnection = Connection(100, 2, PostId(1))
      val containment: List[Containment] = List(1 -> 2, 2 -> 3)
      val graph = Graph(
        List(1, 2, 3),
        connection ++ Seq(newConnection, Connection(101, 4, newConnection.id)),
        containment :+ Containment(102, 3, 5)
      )
      graph.consistent mustEqual Graph(List(1, 2, 3), connection :+ newConnection, containment)
    }

    "consistent on inconsistent graph with ownership" in {
      val graph = Graph(
        List(1, 2, 3),
        groups = List(1),
        ownerships = List(Ownership(postId = 4, groupId = 1), Ownership(postId = 1, groupId = 2))
      )
      graph.consistent mustEqual graph.copy(ownerships = Set.empty)
      (graph + 4).consistent mustEqual (graph + 4).copy(ownerships = Set(Ownership(postId = 4, groupId = 1)))
      graph.copy(groupsById = Seq[Group](1, 2).by(_.id)).consistent mustEqual graph.copy(groupsById = Seq[Group](1, 2).by(_.id), ownerships = Set(Ownership(postId = 1, groupId = 2)))
    }

    "consistent on inconsistent graph (reverse)" in {
      val connection: List[Connection] = List(1 -> 2, 2 -> 3)
      val containment: List[Containment] = List(1 -> 2, 2 -> 3)
      val graph = Graph(
        List(1, 2, 3),
        connection ++ Seq(Connection(100, 4, PostId(1)), Connection(101, 1, ConnectionId(100))),
        containment :+ Containment(102, 3, 5)
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
      val newConnection = Connection(99, 3, PostId(1))
      val graph = Graph(List(1, 2, 3), connection, List(1 -> 2, 2 -> 3))
      (graph + newConnection) mustEqual Graph(graph.posts, connection :+ newConnection, graph.containments)
    }

    "add hyper connection" in {
      val connection = Seq(Connection(99, 3, PostId(1)))
      val hyper = Connection(100, 1, ConnectionId(99))
      val graph = Graph(List(1, 2, 3), connection, List(1 -> 2, 2 -> 3))
      (graph + hyper) mustEqual Graph(graph.posts, connection :+ hyper, graph.containments)
    }

    "add containment" in {
      val containment: List[Containment] = List(1 -> 2, 2 -> 3)
      val newContainment = Containment(99, 3, 1)
      val graph = Graph(List(1, 2, 3), List(1 -> 2, 2 -> 3), containment)
      (graph + newContainment) mustEqual Graph(graph.posts, graph.connections, containment :+ newContainment)
    }

    "add multiple atoms" in {
      val newPost = Post(99, "hans")
      val newConnection = Connection(99, 3, PostId(1))
      val newContainment = Containment(99, 3, 1)
      val newAtoms = Seq(newPost, newConnection, newContainment)
      val graph = Graph(List(1, 2, 3), List(1 -> 2, 2 -> 3), List(1 -> 2, 2 -> 3))
      (graph ++ newAtoms) mustEqual Graph(
        graph.posts.toSeq :+ newPost,
        graph.connections.toSeq :+ newConnection,
        graph.containments.toSeq :+ newContainment
      )
    }

    "remove post" in {
      val connection: List[Connection] = List(Connection(10, 1, PostId(2)), Connection(11, 2, PostId(3)))
      val containment: List[Containment] = List(Containment(20, 1, 2), Containment(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connection, containment)
      (graph - PostId(1)) mustEqual Graph(List(2, 3), List(Connection(11, 2, PostId(3))), List(Containment(21, 2, 3)))
    }

    "remove non-existing post" in {
      val connection: List[Connection] = List(Connection(10, 1, PostId(2)), Connection(11, 2, PostId(3)))
      val containment: List[Containment] = List(Containment(20, 1, 2), Containment(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connection, containment)
      (graph - PostId(4)) mustEqual graph
    }

    "remove connection" in {
      val connection: List[Connection] = List(Connection(10, 1, PostId(2)), Connection(11, 2, PostId(3)))
      val containment: List[Containment] = List(Containment(20, 1, 2), Containment(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connection, containment)
      (graph - ConnectionId(11)) mustEqual Graph(graph.posts, List(Connection(10, 1, PostId(2))), graph.containments)
    }

    "remove non-existing connection" in {
      val connection: List[Connection] = List(Connection(10, 1, PostId(2)), Connection(11, 2, PostId(3)))
      val containment: List[Containment] = List(Containment(20, 1, 2), Containment(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connection, containment)
      (graph - ConnectionId(30)) mustEqual graph
    }

    "remove containment" in {
      val connection: List[Connection] = List(Connection(10, 1, PostId(2)), Connection(11, 2, PostId(3)))
      val containment: List[Containment] = List(Containment(20, 1, 2), Containment(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connection, containment)
      (graph - ContainmentId(21)) mustEqual Graph(graph.posts, graph.connections, List(Containment(20, 1, 2)))
    }

    "remove non-existing containment" in {
      val connection: List[Connection] = List(Connection(10, 1, PostId(2)), Connection(11, 2, PostId(3)))
      val containment: List[Containment] = List(Containment(20, 1, 2), Containment(21, 2, 3))
      val graph = Graph(List(1, 2, 3), connection, containment)
      (graph - ContainmentId(30)) mustEqual graph
    }

    "remove multiple atoms" in {
      val connection: List[Connection] = List(Connection(10, 1, PostId(2)), Connection(11, 2, PostId(3)))
      val containment: List[Containment] = List(Containment(20, 1, 2), Containment(21, 2, 3))
      val delAtoms = Seq(PostId(1), ConnectionId(11), ContainmentId(21))
      val graph = Graph(List(1, 2, 3), connection, containment)
      (graph -- delAtoms) mustEqual Graph(List(2, 3))
    }

    "successors of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connection(3, 1, PostId(11)), Connection(4, 11, PostId(12)), Connection(5, 12, PostId(1)), Connection(6, 12, PostId(13))),
        containments = List(Containment(3, 12, 14))
      )

      graph.successors(PostId(12)) mustEqual Set(1, 13).map(PostId(_))
      graph.successors(PostId(13)) mustEqual Set.empty
    }

    "predecessors of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connection(3, 1, PostId(11)), Connection(4, 11, PostId(12)), Connection(5, 12, PostId(1)), Connection(6, 12, PostId(13))),
        containments = List(Containment(3, 12, 14))
      )

      graph.predecessors(PostId(12)) mustEqual Set(11).map(PostId(_))
      graph.predecessors(PostId(13)) mustEqual Set(12).map(PostId(_))
    }

    "neighbours of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connection(3, 1, PostId(11)), Connection(4, 11, PostId(12)), Connection(5, 12, PostId(1)), Connection(6, 12, PostId(13))),
        containments = List(Containment(3, 12, 14))
      )

      graph.neighbours(PostId(12)) mustEqual Set(1, 11, 13).map(PostId(_))
      graph.neighbours(PostId(13)) mustEqual Set(12).map(PostId(_))
    }

    "children of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connection(2, 1, PostId(14))),
        containments = List(Containment(3, 1, 11), Containment(4, 1, 12), Containment(5, 13, 12))
      )

      graph.children(PostId(1)) mustEqual Set(11, 12).map(PostId(_))
      graph.children(PostId(12)) mustEqual Set.empty
    }

    "parents of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connection(2, 1, PostId(14))),
        containments = List(Containment(3, 1, 11), Containment(4, 1, 12), Containment(5, 13, 12))
      )

      graph.parents(PostId(1)) mustEqual Set.empty
      graph.parents(PostId(12)) mustEqual Set(1, 13).map(PostId(_))
    }

    "containment neighbours of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier"), Post(11, "wein"), Post(12, "schnaps"), Post(13, "wasser"), Post(14, "nichts")),
        connections = List(Connection(2, 1, PostId(14))),
        containments = List(Containment(3, 1, 11), Containment(4, 1, 12), Containment(5, 13, 12))
      )

      graph.containmentNeighbours(PostId(1)) mustEqual Set(11, 12).map(PostId(_))
      graph.containmentNeighbours(PostId(12)) mustEqual Set(1, 13).map(PostId(_))
    }
  }
}
