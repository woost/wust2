package wust.frontend

import org.scalatest._

import wust.graph._
import wust.util.collection._

class PerspectiveSpec extends FreeSpec with MustMatchers {
  val edgeId: () => Long = {
    var id = 1000
    () => { id += 1; id } // die kollidieren mit posts. wir brauchen für jeden typ ne eigene range
  }

  implicit def intToPost(id: Int): Post = Post(id, "title")
  implicit def intTupleToConnects(ts: (Int, Int)): Connects = Connects(edgeId(), ts._1, PostId(ts._2))
  implicit def intTupleToContains(ts: (Int, Int)): Contains = Contains(edgeId(), ts._1, ts._2)
  implicit def intSetToSelectorIdSet(set: Set[Int]) = Selector.IdSet(set.map(PostId(_)))
  def PostIds(ids: Int*) = ids.map(PostId(_))

  "perspective" - {
    "collapse" - {
      "helpers" - {
        "hasNotCollapsedParents" in {
          val graph = Graph(
            posts = List(1, 2, 3),
            containments = List(1 -> 2, 2 -> 3)
          )
          Collapse.hasUncollapsedParent(graph, 3, collapsing = _.id == 1) must be(false)
          Collapse.hasUncollapsedParent(graph, 3, collapsing = _.id == 2) must be(false)
        }
      }

      "base cases" - {
        "collapse parent" in {
          val graph = Graph(
            posts = List(1, 11),
            containments = List(1 -> 11)
          )
          Collapse(Set(1), graph) mustEqual (graph - PostId(11))
        }

        "collapse child" in {
          val graph = Graph(
            posts = List(1, 11),
            containments = List(1 -> 11)
          )
          Collapse(Set(11), graph) mustEqual graph
        }

        "collapse transitive children" in {
          val graph = Graph(
            posts = List(1, 11, 12),
            containments = List(1 -> 11, 11 -> 12)
          )
          Collapse(Set(1), graph) mustEqual (graph -- PostIds(11, 12))
        }

        "collapse multiple, transitive parents" in {
          val graph = Graph(
            posts = List(1, 2, 3),
            containments = List(1 -> 2, 2 -> 3)
          )
          Collapse(Set(1), graph) mustEqual (graph -- PostIds(2, 3))
          Collapse(Set(2), graph) mustEqual (graph - PostId(3))
          Collapse(Set(1, 2), graph) mustEqual (graph -- PostIds(2, 3))
        }

        "collapse children while having parent" in {
          val graph = Graph(
            posts = List(1, 11, 12),
            containments = List(1 -> 11, 11 -> 12)
          )
          Collapse(Set(11), graph) mustEqual (graph - PostId(12))
        }

        "collapse two parents" in {
          val graph = Graph(
            posts = List(1, 2, 11),
            containments = List(1 -> 11, 2 -> 11)
          )
          Collapse(Set(1), graph) mustEqual graph
          Collapse(Set(2), graph) mustEqual graph
          Collapse(Set(1, 2), graph) mustEqual (graph - PostId(11))
        }

        "diamond-shape containment" in {
          val graph = Graph(
            posts = List(1, 2, 3, 11),
            containments = List(1 -> 2, 1 -> 3, 2 -> 11, 3 -> 11)
          )
          Collapse(Set(1), graph) mustEqual graph -- PostIds(2, 3, 11)
          Collapse(Set(2), graph) mustEqual graph
          Collapse(Set(1, 2), graph) mustEqual graph -- PostIds(2, 3, 11)
          Collapse(Set(1, 2, 3), graph) mustEqual graph -- PostIds(2, 3, 11)
        }
      }

      "cycles" - {
        "not collapse cycle" in {
          val graph = Graph(
            posts = List(11, 12, 13),
            containments = List(11 -> 12, 12 -> 13, 13 -> 11) // containment cycle
          )
          Collapse(Set(11), graph) mustEqual graph // nothing to collapse because of cycle
        }

        "not collapse cycle with child" in {
          val graph = Graph(
            posts = List(11, 12, 13, 20),
            containments = List(11 -> 12, 12 -> 13, 13 -> 11, 12 -> 20) // containment cycle -> 20
          )
          Collapse(Set(11), graph) mustEqual (graph - PostId(20)) // cycle stays
        }

        "collapse parent with child-cycle" in {
          val graph = Graph(
            posts = List(1, 11, 12, 13),
            containments = List(1 -> 11, 11 -> 12, 12 -> 13, 13 -> 11) // 1 -> containment cycle
          )
          Collapse(Set(1), graph) mustEqual (graph -- PostIds(11, 12, 13))
        }
      }

      "connection redirection" - {
        "redirect collapsed connection to source" in {
          val connection = Connects(5, 11, PostId(2))
          val graph = Graph(
            posts = List(1, 11, 2),
            containments = List(1 -> 11),
            connections = List(connection)
          )
          Collapse(Set(1), graph) mustEqual (graph - PostId(11) + connection.copy(id = -1, sourceId = 1))
        }

        "redirect collapsed connection to target" in {
          val connection = Connects(5, 2, PostId(11))
          val graph = Graph(
            posts = List(1, 11, 2),
            containments = List(1 -> 11),
            connections = List(connection)
          )
          Collapse(Set(1), graph) mustEqual (graph - PostId(11) + connection.copy(id = -1, targetId = PostId(1)))
        }

        "redirect edge source to earliest collapsed transitive parent" in {
          val connection = Connects(5, 3, PostId(11))
          val graph = Graph(
            posts = List(1, 2, 3, 11),
            containments = List(1 -> 2, 2 -> 3),
            connections = List(connection)
          )
          Collapse(Set(1), graph) mustEqual (graph -- PostIds(2, 3) + connection.copy(id = -1, sourceId = 1))
          Collapse(Set(2), graph) mustEqual (graph - PostId(3) + connection.copy(id = -1, sourceId = 2))
          Collapse(Set(1, 2), graph) mustEqual (graph -- PostIds(2, 3) + connection.copy(id = -1, sourceId = 1))
        }

        "redirect edge target to earliest collapsed transitive parent" in {
          val connection = Connects(5, 11, PostId(3))
          val graph = Graph(
            posts = List(1, 2, 3, 11),
            containments = List(1 -> 2, 2 -> 3),
            connections = List(connection)
          )
          Collapse(Set(1), graph) mustEqual (graph -- PostIds(2, 3) + connection.copy(id = -1, targetId = PostId(1)))
          Collapse(Set(2), graph) mustEqual (graph - PostId(3) + connection.copy(id = -1, targetId = PostId(2)))
          Collapse(Set(1, 2), graph) mustEqual (graph -- PostIds(2, 3) + connection.copy(id = -1, targetId = PostId(1)))
        }

        "redirect and split outgoing edge while collapsing two parents" in {
          val connection = Connects(5, 11, PostId(20))
          val graph = Graph(
            posts = List(1, 2, 11, 20),
            containments = List(1 -> 11, 2 -> 11),
            connections = List(connection)
          )
          Collapse(Set(1), graph) mustEqual graph
          Collapse(Set(2), graph) mustEqual graph
          Collapse(Set(1, 2), graph) mustEqual (graph - PostId(11) + connection.copy(id = -1, sourceId = 1) + connection.copy(id = -2, sourceId = 2))
        }

        "redirect and split incoming edge while collapsing two parents" in {
          val connection = Connects(5, 20, PostId(11))
          val graph = Graph(
            posts = List(1, 2, 11, 20),
            containments = List(1 -> 11, 2 -> 11),
            connections = List(connection)
          )
          Collapse(Set(1), graph) mustEqual graph
          Collapse(Set(2), graph) mustEqual graph
          Collapse(Set(1, 2), graph) mustEqual (graph - PostId(11) + connection.copy(id = -1, targetId = PostId(1)) + connection.copy(id = -2, targetId = PostId(2)))
        }

        "redirect connection between children while collapsing two parents" in {
          val connection = Connects(5, 11, PostId(12))
          val graph = Graph(
            posts = List(1, 2, 11, 12),
            containments = List(1 -> 11, 2 -> 12),
            connections = List(connection)
          )
          Collapse(Set(1), graph) mustEqual (graph - PostId(11) + connection.copy(id = -1, sourceId = 1))
          Collapse(Set(2), graph) mustEqual (graph - PostId(12) + connection.copy(id = -1, targetId = PostId(2)))
          Collapse(Set(1, 2), graph) mustEqual (graph -- PostIds(11, 12) + connection.copy(id = -1, sourceId = 1, targetId = PostId(2)))
        }

        "redirect and bundle edges to target" in {
          val connection1 = Connects(5, 11, PostId(2))
          val connection2 = Connects(6, 12, PostId(2))
          val graph = Graph(
            posts = List(1, 11, 12, 2),
            containments = List(1 -> 11, 1 -> 12),
            connections = List(connection1, connection2)
          )
          Collapse(Set(1), graph) mustEqual (graph -- PostIds(11, 12) + connection1.copy(id = -1, sourceId = PostId(1)))
        }

        "redirect and bundle edges to source" in {
          val connection1 = Connects(5, 2, PostId(11))
          val connection2 = Connects(6, 2, PostId(12))
          val graph = Graph(
            posts = List(1, 11, 12, 2),
            containments = List(1 -> 11, 1 -> 12),
            connections = List(connection1, connection2)
          )
          Collapse(Set(1), graph) mustEqual (graph -- PostIds(11, 12) + connection1.copy(id = -1, targetId = PostId(1)))
        }

        "redirect mixed edges" in {
          val connection1 = Connects(5, 2, PostId(11))
          val connection2 = Connects(6, 12, PostId(2))
          val graph = Graph(
            posts = List(1, 11, 12, 2),
            containments = List(1 -> 11, 1 -> 12),
            connections = List(connection1, connection2)
          )
          Collapse(Set(1), graph) must (
            equal(graph -- PostIds(11, 12) + connection1.copy(id = -1, targetId = PostId(1)) + connection2.copy(id = -2, sourceId = PostId(1))) or
            equal(graph -- PostIds(11, 12) + connection1.copy(id = -2, targetId = PostId(1)) + connection2.copy(id = -1, sourceId = PostId(1)))
          )
        }

        "redirect in diamond-shape containment" in {
          val connection = Connects(5, 11, PostId(20))
          val graph = Graph(
            posts = List(1, 2, 3, 11, 20),
            containments = List(1 -> 2, 1 -> 3, 2 -> 11, 3 -> 11),
            connections = List(connection)
          )

          Collapse(Set(1), graph) mustEqual graph -- PostIds(2, 3, 11) + connection.copy(id = -1, sourceId = 1)
          Collapse(Set(2), graph) mustEqual graph
          Collapse(Set(1, 2), graph) mustEqual graph -- PostIds(2, 3, 11) + connection.copy(id = -1, sourceId = 1)
          Collapse(Set(2, 3), graph) mustEqual graph -- PostIds(11) + connection.copy(id = -1, sourceId = 2) + connection.copy(id = -2, sourceId = 3)
          Collapse(Set(1, 2, 3), graph) mustEqual graph -- PostIds(2, 3, 11) + connection.copy(id = -1, sourceId = 1)
        }

        "redirect into cycle" in {
          val connection = Connects(5, 11, PostId(20))
          val graph = Graph(
            posts = List(1, 2, 3, 11, 20),
            containments = List(1 -> 2, 2 -> 3, 3 -> 1, 2 -> 11), // containment cycle -> 11
            connections = List(connection) // 11 -> 20
          )
          Collapse(Set(1), graph) mustEqual (graph - PostId(11) + connection.copy(id = -1, sourceId = 2)) // cycle stays
        }

        "redirect out of cycle" in {
          val connection = Connects(5, 13, PostId(20))
          val graph = Graph(
            posts = List(1, 11, 12, 13, 20),
            containments = List(1 -> 11, 11 -> 12, 12 -> 13, 13 -> 11), // 1 -> containment cycle(11,12,13)
            connections = List(connection) // 13 -> 20
          )
          Collapse(Set(1), graph) mustEqual (graph -- PostIds(11, 12, 13) + connection.copy(id = -1, sourceId = 1))
        }
      }
    }
  }
}
