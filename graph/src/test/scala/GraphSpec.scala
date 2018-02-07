package wust.graph

import org.scalatest._
import wust.ids._
import wust.util.collection._
import java.time.LocalDateTime

class GraphSpec extends FreeSpec with MustMatchers {
  implicit def intToUuidType(id: Int): UuidType = id.toString
  implicit def intToPostId(id: Int): PostId = PostId(id.toString)
  implicit def intToGroupId(id: Int): GroupId = GroupId(id)
  implicit def idToPost(id: Int): Post = Post(id.toString, "", "", LocalDateTime.of(2018,11,11,11,11), LocalDateTime.of(2018,11,11,11,11))
  implicit def idToGroup(id: Int): Group = Group(id)
  implicit def postListToMap(posts: List[Int]): List[Post] = posts.map(idToPost)
  implicit def tupleIsConnection(t: (Int, Int)): Connection = Connection(t._1.toString, t._2.toString)
  implicit def connectionListIsMap(connections: List[(Int, Int)]): List[Connection] = connections.map(tupleIsConnection)

  implicit class ContainmentBuilder(parentId: Int) {
    def cont(childId: Int) = Containment(parentId, childId);
  }
  def Connection(sourceId: PostId, targetId: PostId) = new Connection(sourceId, "connector", targetId)
  def Containment(parentId: PostId, childId: PostId) = new Connection(childId, Label.parent, parentId)

  "graph" - {
    "empty is empty" in {
      Graph.empty.postsById mustBe empty
      Graph.empty.connectionsWithoutParent mustBe empty

      Graph.empty.posts mustBe empty

      Graph().postsById mustBe empty
      Graph().connectionsWithoutParent mustBe empty

      Graph().posts mustBe empty
    }

    "directed cycle" in {
      val graph = Graph(
        posts = List(Post(1, "title", ""), Post(11, "title2", ""), Post(12, "test3", "")),
        connections = List(Containment(1, 11), Containment(11, 12), Containment(12, 1))
      )

      graph.involvedInContainmentCycle(1) mustEqual true
    }

    "one contain" in {
      val graph = Graph(List(Post(1, "title", ""), Post(11, "title2", "")), List(Containment(1, 11)))
      graph.involvedInContainmentCycle(1) mustEqual false
    }

    "have transitive parents in cycle" in {
      val graph = Graph(List(1, 2, 3), List(1 cont 2, 2 cont 3, 3 cont 1))
      graph.ancestors(3).toSet mustEqual Set(3, 2, 1).map(PostId(_))
    }

    "have transitive children in cycle" in {
      val graph = Graph(List(1, 2, 3), List(1 cont 2, 2 cont 3, 3 cont 1))
      graph.descendants(3).toSet mustEqual Set(3, 2, 1).map(PostId(_))
    }

    "consistent on consistent graph" in {
      val graph = Graph(List(1, 2, 3), List[Connection](1 -> 2, 2 -> 3, 3 -> 1) ++ List[Connection](1 cont 2, 2 cont 3, 3 cont 1))
      graph.consistent mustEqual graph
    }

    "consistent on inconsistent graph" in {
      val connections: List[Connection] = List(1 -> 2, 2 -> 3)
      val newConnection = Connection(2, 1)
      val containments: List[Connection] = List(1 cont 2, 2 cont 3)
      val graph = Graph(
        List(1, 2, 3),
        connections ++ containments :+ newConnection :+ Containment(3, 5)
      )
      graph.consistent mustEqual Graph(List(1, 2, 3), connections ++ containments :+ newConnection)
    }

    "consistent on graph with self-loops" in {
      val connections: List[Connection] = List(1 -> 2, 2 -> 3, 4 -> 4)
      val containments: List[Connection] = List(1 cont 2, 2 cont 3, 5 cont 5)
      val graph = Graph(
        List(1, 2, 3, 4, 5),
        connections ++ containments
      )
      graph.consistent mustEqual Graph(List(1, 2, 3, 4, 5), List[Connection](1 -> 2, 2 -> 3) ++ List[Connection](1 cont 2, 2 cont 3))
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
      val connections: List[Connection] = List(1 -> 2, 2 -> 3)
      val containments: List[Connection] = List(1 cont 2, 2 cont 3)
      val graph = Graph(
        List(1, 2, 3),
        connections ++ Seq(Connection(4, 1), Connection(1, 100)) ++ containments :+ Containment(3, 5)
      )
      graph.consistent mustEqual Graph(List(1, 2, 3), connections ++ containments)
    }

    "add post" in {
      val posts: List[Post] = List(1, 2, 3)
      val newPost = Post(99, "hans", "")
      val graph = Graph(posts, List[Connection](1 -> 2, 2 -> 3) ++ List(1 cont 2, 2 cont 3))
      (graph + newPost) mustEqual Graph(posts :+ newPost, graph.connectionsWithoutParent ++ graph.containments)
    }

    "add connection" in {
      val connections: List[Connection] = List(1 -> 2, 2 -> 3)
      val newConnection = Connection(3, 1)
      val graph = Graph(List(1, 2, 3), connections ++ List[Connection](1 -> 2, 2 -> 3))
      (graph + newConnection) mustEqual Graph(graph.posts, connections ++ graph.containments :+ newConnection)
    }

    "add containment" in {
      val containments: List[Connection] = List(1 -> 2, 2 -> 3)
      val newContainment = Containment(3, 1)
      val graph = Graph(List(1, 2, 3), List[Connection](1 -> 2, 2 -> 3) ++ containments)
      (graph + newContainment) mustEqual Graph(graph.posts, graph.connectionsWithoutParent ++ (containments :+ newContainment))
    }

    "filter" in {
      val connections: List[Connection] = List(1 -> 2, 2 -> 3)
      val containments: List[Connection] = List(1 cont 2, 2 cont 3)
      val graph = Graph(List(1, 2, 3), connections ++ containments)
      (graph filter Set(PostId(1))) mustEqual Graph(List(1))
      (graph filter Set(PostId(1), PostId(2))) mustEqual Graph(List(1, 2), List(Connection(1, 2)) ++ List(Containment(1, 2)))
    }

    "remove post" in {
      val connections: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containments: List[Connection] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connections ++ containments)
      (graph - PostId(1)) mustEqual Graph(List(2, 3), List(Connection(2, 3)) ++ List(Containment(2, 3)))
    }

    "remove non-existing post" in {
      val connections: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containments: List[Connection] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connections ++ containments)
      (graph - PostId(4)) mustEqual graph
    }

    "remove one post with removePosts" in {
      val connections: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containments: List[Connection] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connections ++ containments)
      (graph removePosts Seq(PostId(1))) mustEqual Graph(List(2, 3), List(Connection(2, 3)) ++ List(Containment(2, 3)))
    }

    "remove multiple posts" in {
      val connections: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containments: List[Connection] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3, 4), connections ++ containments)
      (graph removePosts Seq(PostId(1), PostId(4))) mustEqual Graph(List(2, 3), List(Connection(2, 3)) ++  List(Containment(2, 3)))
    }

    "remove connection" in {
      val connections: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containments: List[Connection] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connections ++ containments)
      (graph - Connection(2, 3)) mustEqual Graph(graph.posts, List(Connection(1, 2)) ++ graph.containments)
    }

    "remove non-existing connection" in {
      val connections: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containments: List[Connection] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connections ++ containments)
      (graph - Connection(5, 7)) mustEqual graph
    }

    "remove containment" in {
      val connections: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containments: List[Connection] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connections ++ containments)
      (graph - Containment(2, 3)) mustEqual Graph(graph.posts, graph.connectionsWithoutParent ++ List(Containment(1, 2)))
    }

    "remove non-existing containment" in {
      val connection: List[Connection] = List(Connection(1, 2), Connection(2, 3))
      val containment: List[Connection] = List(Containment(1, 2), Containment(2, 3))
      val graph = Graph(List(1, 2, 3), connection ++ containment)
      (graph - Containment(17, 18)) mustEqual graph
    }

    "successors of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier", ""), Post(11, "wein", ""), Post(12, "schnaps", ""), Post(13, "wasser", ""), Post(14, "nichts", "")),
        connections = List(Connection(1, 11), Connection(11, 12), Connection(12, 1), Connection(12, 13)) ++ List(Containment(12, 14))
      )

      graph.successorsWithoutParent(PostId(12)) mustEqual Set(1, 13).map(PostId(_))
      graph.successorsWithoutParent(PostId(13)) mustEqual Set.empty
    }

    "predecessors of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier", ""), Post(11, "wein", ""), Post(12, "schnaps", ""), Post(13, "wasser", ""), Post(14, "nichts", "")),
        connections = List(Connection(1, 11), Connection(11, 12), Connection(12, 1), Connection(12, 13)) ++ List(Containment(12, 14))
      )

      graph.predecessorsWithoutParent(PostId(12)) mustEqual Set(11).map(PostId(_))
      graph.predecessorsWithoutParent(PostId(13)) mustEqual Set(12).map(PostId(_))
    }

    "neighbours of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier", ""), Post(11, "wein", ""), Post(12, "schnaps", ""), Post(13, "wasser", ""), Post(14, "nichts", "")),
        connections = List(Connection(1, 11), Connection(11, 12), Connection(12, 1), Connection(12, 13)) ++ List(Containment(12, 14))
      )

      graph.neighboursWithoutParent(PostId(12)) mustEqual Set(1, 11, 13).map(PostId(_))
      graph.neighboursWithoutParent(PostId(13)) mustEqual Set(12).map(PostId(_))
    }

    "children of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier", ""), Post(11, "wein", ""), Post(12, "schnaps", ""), Post(13, "wasser", ""), Post(14, "nichts", "")),
        connections = List(Connection(1, 14)) ++ List(Containment(1, 11), Containment(1, 12), Containment(13, 12))
      )

      graph.children(PostId(1)) mustEqual Set(11, 12).map(PostId(_))
      graph.children(PostId(12)) mustEqual Set.empty
    }

    "parents of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier", ""), Post(11, "wein", ""), Post(12, "schnaps", ""), Post(13, "wasser", ""), Post(14, "nichts", "")),
        connections = List(Connection(1, 14)) ++ List(Containment(1, 11), Containment(1, 12), Containment(13, 12))
      )

      graph.parents(PostId(1)) mustEqual Set.empty
      graph.parents(PostId(12)) mustEqual Set(1, 13).map(PostId(_))
    }

    "containment neighbours of post" in {
      val graph = Graph(
        posts = List(Post(1, "bier", ""), Post(11, "wein", ""), Post(12, "schnaps", ""), Post(13, "wasser", ""), Post(14, "nichts", "")),
        connections = List(Connection(1, 14)) ++ List(Containment(1, 11), Containment(1, 12), Containment(13, 12))
      )

      graph.containmentNeighbours(PostId(1)) mustEqual Set(11, 12).map(PostId(_))
      graph.containmentNeighbours(PostId(12)) mustEqual Set(1, 13).map(PostId(_))
    }
  }
}
