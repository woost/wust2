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
      def collapse(collapsing: Selector, graph: Graph): DisplayGraph = Collapse(collapsing)(DisplayGraph(graph))
      def dg(graph: Graph, redirected: Set[(Int, Int)] = Set.empty) = {
        DisplayGraph(
          graph,
          redirectedConnections = redirected.map { case (source, target) => LocalConnection(source, target) }
        )
      }

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
          collapse(Set(1), graph) mustEqual dg(graph - PostId(11))
        }

        "collapse child" in {
          val graph = Graph(
            posts = List(1, 11),
            containments = List(1 -> 11)
          )
          collapse(Set(11), graph) mustEqual dg(graph)
        }

        "collapse transitive children" in {
          val graph = Graph(
            posts = List(1, 11, 12),
            containments = List(1 -> 11, 11 -> 12)
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(11, 12))
        }

        "collapse multiple, transitive parents" in {
          val graph = Graph(
            posts = List(1, 2, 3),
            containments = List(1 -> 2, 2 -> 3)
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(2, 3))
          collapse(Set(2), graph) mustEqual dg(graph - PostId(3))
          collapse(Set(1, 2), graph) mustEqual dg(graph -- PostIds(2, 3))
        }

        "collapse children while having parent" in {
          val graph = Graph(
            posts = List(1, 11, 12),
            containments = List(1 -> 11, 11 -> 12)
          )
          collapse(Set(11), graph) mustEqual dg(graph - PostId(12))
        }

        "collapse two parents" in {
          val containment1 = Contains(5, 1, 11)
          val containment2 = Contains(6, 2, 11)
          val graph = Graph(
            posts = List(1, 2, 11),
            containments = List(containment1, containment2)
          )
          collapse(Set(1), graph) mustEqual dg(graph - containment1.id)
          collapse(Set(2), graph) mustEqual dg(graph - containment2.id)
          collapse(Set(1, 2), graph) mustEqual dg(graph - PostId(11))
        }

        "diamond-shape containment" in {
          val containment1 = Contains(5, 2, 11)
          val containment2 = Contains(6, 3, 11)
          val graph = Graph(
            posts = List(1, 2, 3, 11),
            containments = List[Contains](1 -> 2, 1 -> 3) ++ List(containment1, containment2)
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(2, 3, 11))
          collapse(Set(2), graph) mustEqual dg(graph - containment1.id)
          collapse(Set(3), graph) mustEqual dg(graph - containment2.id)
          collapse(Set(1, 2), graph) mustEqual dg(graph -- PostIds(2, 3, 11))
          collapse(Set(1, 2, 3), graph) mustEqual dg(graph -- PostIds(2, 3, 11))
        }
      }

      "cycles" - {
        "partially collapse cycle" in {
          val containment = Contains(5, 11, 12)
          val graph = Graph(
            posts = List(11, 12, 13),
            containments = containment :: List[Contains](12 -> 13, 13 -> 11) // containment cycle
          )
          collapse(Set(11), graph) mustEqual dg(graph - containment.id) // nothing to collapse because of cycle
        }

        "partially collapse cycle with child" in {
          val containment = Contains(5, 11, 12)
          val graph = Graph(
            posts = List(11, 12, 13, 20),
            containments = containment :: List[Contains](12 -> 13, 13 -> 11, 12 -> 20) // containment cycle -> 20
          )
          collapse(Set(11), graph) mustEqual dg(graph - PostId(20) - containment.id) // cycle stays
        }

        "collapse parent with child-cycle" in {
          val graph = Graph(
            posts = List(1, 11, 12, 13),
            containments = List(1 -> 11, 11 -> 12, 12 -> 13, 13 -> 11) // 1 -> containment cycle
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(11, 12, 13))
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
          collapse(Set(1), graph) mustEqual dg(graph - PostId(11), Set(1 -> 2))
        }

        "redirect collapsed connection to target" in {
          val connection = Connects(5, 2, PostId(11))
          val graph = Graph(
            posts = List(1, 11, 2),
            containments = List(1 -> 11),
            connections = List(connection)
          )
          collapse(Set(1), graph) mustEqual dg(graph - PostId(11), Set(2 -> 1))
        }

        "redirect edge source to earliest collapsed transitive parent" in {
          val connection = Connects(5, 3, PostId(11))
          val graph = Graph(
            posts = List(1, 2, 3, 11),
            containments = List(1 -> 2, 2 -> 3),
            connections = List(connection)
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(2, 3), Set(1 -> 11))
          collapse(Set(2), graph) mustEqual dg(graph - PostId(3), Set(2 -> 11))
          collapse(Set(1, 2), graph) mustEqual dg(graph -- PostIds(2, 3), Set(1 -> 11))
        }

        "redirect edge target to earliest collapsed transitive parent" in {
          val connection = Connects(5, 11, PostId(3))
          val graph = Graph(
            posts = List(1, 2, 3, 11),
            containments = List(1 -> 2, 2 -> 3),
            connections = List(connection)
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(2, 3), Set(11 -> 1))
          collapse(Set(2), graph) mustEqual dg(graph - PostId(3), Set(11 -> 2))
          collapse(Set(1, 2), graph) mustEqual dg(graph -- PostIds(2, 3), Set(11 -> 1))
        }

        "redirect and split outgoing edge while collapsing two parents" in {
          val containment1 = Contains(5, 1, 11)
          val containment2 = Contains(6, 2, 11)
          val connection = Connects(5, 11, PostId(20))
          val graph = Graph(
            posts = List(1, 2, 11, 20),
            containments = List(containment1, containment2),
            connections = List(connection)
          )
          collapse(Set(1), graph) mustEqual dg(graph - containment1.id, Set(1 -> 20))
          collapse(Set(2), graph) mustEqual dg(graph - containment2.id, Set(2 -> 20))
          collapse(Set(1, 2), graph) mustEqual dg(graph - PostId(11), Set(1 -> 20, 2 -> 20))
        }

        "redirect and split incoming edge while collapsing two parents" in {
          val containment1 = Contains(5, 1, 11)
          val containment2 = Contains(6, 2, 11)
          val connection = Connects(5, 20, PostId(11))
          val graph = Graph(
            posts = List(1, 2, 11, 20),
            containments = List(containment1, containment2),
            connections = List(connection)
          )
          collapse(Set(1), graph) mustEqual dg(graph - containment1.id, Set(20 -> 1))
          collapse(Set(2), graph) mustEqual dg(graph - containment2.id, Set(20 -> 2))
          collapse(Set(1, 2), graph) mustEqual dg(graph - PostId(11), Set(20 -> 1, 20 -> 2))
        }

        "redirect connection between children while collapsing two parents" in {
          val connection = Connects(5, 11, PostId(12))
          val graph = Graph(
            posts = List(1, 2, 11, 12),
            containments = List(1 -> 11, 2 -> 12),
            connections = List(connection)
          )
          collapse(Set(1), graph) mustEqual dg(graph - PostId(11), Set(1 -> 12))
          collapse(Set(2), graph) mustEqual dg(graph - PostId(12), Set(11 -> 2))
          collapse(Set(1, 2), graph) mustEqual dg(graph -- PostIds(11, 12), Set(1 -> 2))
        }

        "redirect and bundle edges to target" in {
          val connection1 = Connects(5, 11, PostId(2))
          val connection2 = Connects(6, 12, PostId(2))
          val graph = Graph(
            posts = List(1, 11, 12, 2),
            containments = List(1 -> 11, 1 -> 12),
            connections = List(connection1, connection2)
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(11, 12), Set(1 -> 2))
        }

        "redirect and bundle edges to source" in {
          val connection1 = Connects(5, 2, PostId(11))
          val connection2 = Connects(6, 2, PostId(12))
          val graph = Graph(
            posts = List(1, 11, 12, 2),
            containments = List(1 -> 11, 1 -> 12),
            connections = List(connection1, connection2)
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(11, 12), Set(2 -> 1))
        }

        "drop redirected, because of existing connection" in {
          val connection1 = Connects(5, 1, PostId(2))
          val connection2 = Connects(6, 11, PostId(2))
          val graph = Graph(
            posts = List(1, 11, 2),
            containments = List(1 -> 11),
            connections = List(connection1, connection2)
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(11))
        }

        "redirect mixed edges" in {
          val connection1 = Connects(5, 2, PostId(11))
          val connection2 = Connects(6, 12, PostId(2))
          val graph = Graph(
            posts = List(1, 11, 12, 2),
            containments = List(1 -> 11, 1 -> 12),
            connections = List(connection1, connection2)
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(11, 12), Set(2 -> 1, 1 -> 2))
        }

        "redirect in diamond-shape containment" in {
          val containment1 = Contains(5, 2, 11)
          val containment2 = Contains(6, 3, 11)
          val connection = Connects(5, 11, PostId(20))
          val graph = Graph(
            posts = List(1, 2, 3, 11, 20),
            containments = List[Contains](1 -> 2, 1 -> 3) ++ List(containment1, containment2),
            connections = List(connection)
          )

          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(2, 3, 11), Set(1 -> 20))
          collapse(Set(2), graph) mustEqual dg(graph - containment1.id, Set(2 -> 20))
          collapse(Set(3), graph) mustEqual dg(graph - containment2.id, Set(3 -> 20))
          collapse(Set(1, 2), graph) mustEqual dg(graph -- PostIds(2, 3, 11), Set(1 -> 20))
          collapse(Set(2, 3), graph) mustEqual dg(graph -- PostIds(11), Set(2 -> 20, 3 -> 20))
          collapse(Set(1, 2, 3), graph) mustEqual dg(graph -- PostIds(2, 3, 11), Set(1 -> 20))
        }

        "redirect into cycle" in {
          val connection = Connects(5, 11, PostId(20))
          val containment = Contains(6, 1, 2)
          val graph = Graph(
            posts = List(1, 2, 3, 11, 20),
            containments = containment :: List[Contains](2 -> 3, 3 -> 1, 2 -> 11), // containment cycle -> 11
            connections = List(connection) // 11 -> 20
          )
          collapse(Set(1), graph) mustEqual dg(graph - PostId(11) - containment.id, Set(2 -> 20)) // cycle stays
        }

        "redirect out of cycle" in {
          val connection = Connects(5, 13, PostId(20))
          val graph = Graph(
            posts = List(1, 11, 12, 13, 20),
            containments = List(1 -> 11, 11 -> 12, 12 -> 13, 13 -> 11), // 1 -> containment cycle(11,12,13)
            connections = List(connection) // 13 -> 20
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(11, 12, 13), Set(1 -> 20))
        }
      }
    }
  }
}
