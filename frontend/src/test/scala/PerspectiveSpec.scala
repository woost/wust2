package wust.frontend

import org.scalatest._
import collection.breakOut

import wust.graph._
import wust.util.collection._
import wust.util._

class PerspectiveSpec extends FreeSpec with MustMatchers {
  val edgeId = AutoId(-1, delta = -1)

  implicit def intToPost(id: Int): Post = Post(id, "title")
  implicit def intTupleToConnects(ts: (Int, Int)): Connects = Connects(ts)
  implicit def intTupleToContains(ts: (Int, Int)): Contains = Contains(ts)
  implicit def intSetToSelectorIdSet(set: Set[Int]) = Selector.IdSet(set.map(PostId(_)))
  def PostIds(ids: Int*): Set[PostId] = (ids.map(PostId(_)))(breakOut)
  implicit class RichContains(con: Contains) {
    def toLocal = LocalContainment(con.parentId, con.childId)
  }
  def Contains(ts: (Int, Int)): Contains = new Contains(edgeId(), ts._1, ts._2)
  def Connects(ts: (Int, Int)): Connects = new Connects(edgeId(), ts._1, PostId(ts._2))

  "perspective" - {
    "collapse" - {

      def collapse(collapsing: Selector, graph: Graph): DisplayGraph = Collapse(collapsing)(DisplayGraph(graph))
      def dg(graph: Graph, redirected: Set[(Int, Int)] = Set.empty, collapsedContainments: Set[LocalContainment] = Set.empty) = {
        DisplayGraph(
          graph,
          redirectedConnections = redirected.map { case (source, target) => LocalConnection(source, target) },
          collapsedContainments = collapsedContainments
        )
      }

      "helpers" - {
        // "hasUncollapsedTransitiveParent" in {
        //   val graph = Graph(
        //     posts = List(1, 2, 3),
        //     containments = List(1 -> 2, 2 -> 3)
        //   )
        //   Collapse.hasOnlyUncollapsedTransitiveParents(graph, 3, collapsing = _ => false) must be(true)
        //   Collapse.hasOnlyUncollapsedTransitiveParents(graph, 3, collapsing = _.id == 1) must be(false)
        //   Collapse.hasOnlyUncollapsedTransitiveParents(graph, 3, collapsing = _.id == 2) must be(false)
        //   Collapse.hasOnlyUncollapsedTransitiveParents(graph, 3, collapsing = (p => p.id == 1 || p.id == 2)) must be(false)

        //   Collapse.hasOneUncollapsedTransitiveParent(graph, 3, collapsing = _ => false) must be(true)
        //   Collapse.hasOneUncollapsedTransitiveParent(graph, 3, collapsing = _.id == 1) must be(false)
        //   Collapse.hasOneUncollapsedTransitiveParent(graph, 3, collapsing = _.id == 2) must be(true)
        // }

        // "hasUncollapsedTransitiveParent 2" in {
        //   val graph = Graph(
        //     posts = List(1, 2, 3, 4, 11),
        //     containments = List(1 -> 11, 3 -> 11, 2 -> 3, 4 -> 3)
        //   )
        //   Collapse.hasOneUncollapsedTransitiveParent(
        //     graph,
        //     11,
        //     collapsing = (p => p.id == 1 || p.id == 2)
        //   ) must be(true)
        // }
        "getHiddenPosts" - {
          import Collapse.{getHiddenPosts => hidden}
          "one parent" in {
            val graph = Graph(
              posts = List(1, 11),
              containments = List(1 -> 11)
            )
            hidden(graph, Set()) mustEqual PostIds()
            hidden(graph, Set(1)) mustEqual PostIds(11)
          }

          "two parents" in {
            val graph = Graph(
              posts = List(1, 2, 11),
              containments = List(1 -> 11, 2 -> 11)
            )
            hidden(graph, Set()) mustEqual PostIds()
            hidden(graph, Set(1)) mustEqual PostIds()
            hidden(graph, Set(2)) mustEqual PostIds()
            hidden(graph, Set(1, 2)) mustEqual PostIds(11)
          }

          "one transitive parent" in {
            val graph = Graph(
              posts = List(1, 2, 11),
              containments = List(1 -> 2, 2 -> 11)
            )
            hidden(graph, Set()) mustEqual PostIds()
            hidden(graph, Set(1)) mustEqual PostIds(2, 11)
            hidden(graph, Set(2)) mustEqual PostIds(11)
            hidden(graph, Set(1, 2)) mustEqual PostIds(2, 11)
          }

          "two transitive parents" in {
            val graph = Graph(
              posts = List(1, 2, 3, 4, 11),
              containments = List(1 -> 2, 2 -> 11, 3 -> 4, 4 -> 11)
            )
            hidden(graph, Set()) mustEqual PostIds()
            hidden(graph, Set(1)) mustEqual PostIds(2)
            hidden(graph, Set(2)) mustEqual PostIds()
            hidden(graph, Set(1, 2)) mustEqual PostIds(2)
            hidden(graph, Set(1, 3)) mustEqual PostIds(2, 4, 11)
            hidden(graph, Set(1, 4)) mustEqual PostIds(2, 11)
          }

          "two parents, one has two other parents" in {
            val graph = Graph(
              posts = List(1, 2, 3, 4, 11),
              containments = List(1 -> 11, 3 -> 11, 2 -> 3, 4 -> 3)
            )
            hidden(graph, Set()) mustEqual PostIds()
            hidden(graph, Set(1)) mustEqual PostIds()
            hidden(graph, Set(2)) mustEqual PostIds()
            hidden(graph, Set(1, 2)) mustEqual PostIds()
            hidden(graph, Set(1, 2, 4)) mustEqual PostIds(3, 11)
            hidden(graph, Set(1, 2, 3)) mustEqual PostIds(11)
          }

          "two parents, one has 2 transitive parents" in {
            val graph = Graph(
              posts = List(1, 2, 3, 4, 11),
              containments = List(1 -> 2, 2 -> 3, 3 -> 11, 4 -> 11)
            )
            hidden(graph, Set()) mustEqual PostIds()
            hidden(graph, Set(1)) mustEqual PostIds(2, 3)
            hidden(graph, Set(2)) mustEqual PostIds(3)
            hidden(graph, Set(3)) mustEqual PostIds()
            hidden(graph, Set(1, 2)) mustEqual PostIds(2, 3)
            hidden(graph, Set(2, 3)) mustEqual PostIds(3)
            hidden(graph, Set(1, 2, 3)) mustEqual PostIds(2, 3)
            hidden(graph, Set(1, 4)) mustEqual PostIds(2, 3, 11)
            hidden(graph, Set(2, 4)) mustEqual PostIds(3, 11)
            hidden(graph, Set(3, 4)) mustEqual PostIds(11)
          }

          "cycle" in {
            val graph = Graph(
              posts = List(1, 2, 3),
              containments = List(1 -> 2, 2 -> 3, 3 -> 1)
            )
            hidden(graph, Set()) mustEqual PostIds()
            hidden(graph, Set(1)) mustEqual PostIds()
          }

          "cycle with child" in {
            val graph = Graph(
              posts = List(1, 2, 3, 4),
              containments = List(1 -> 2, 2 -> 3, 3 -> 1, 3 -> 4)
            )
            hidden(graph, Set()) mustEqual PostIds()
            hidden(graph, Set(1)) mustEqual PostIds(4)
            hidden(graph, Set(2)) mustEqual PostIds(4)
            hidden(graph, Set(3)) mustEqual PostIds(4)
          }

          "cycle with parent" in {
            val graph = Graph(
              posts = List(1, 2, 3, 4),
              containments = List(4 -> 1, 1 -> 2, 2 -> 3, 3 -> 1)
            )
            hidden(graph, Set()) mustEqual PostIds()
            hidden(graph, Set(1)) mustEqual PostIds()
            hidden(graph, Set(2)) mustEqual PostIds()
            hidden(graph, Set(3)) mustEqual PostIds()
            hidden(graph, Set(4)) mustEqual PostIds(1, 2, 3)
          }

          "cycle with child and parent" in {
            val graph = Graph(
              posts = List(1, 2, 3, 4, 5),
              containments = List(4 -> 1, 1 -> 2, 2 -> 3, 3 -> 1, 3 -> 1, 3 -> 5) // 4 -> cycle -> 5
            )
            hidden(graph, Set()) mustEqual PostIds()
            hidden(graph, Set(1)) mustEqual PostIds(5)
            hidden(graph, Set(2)) mustEqual PostIds(5)
            hidden(graph, Set(3)) mustEqual PostIds(5)
            hidden(graph, Set(4)) mustEqual PostIds(1, 2, 3, 5)
          }

          "diamond" in {
            val graph = Graph(
              posts = List(1, 2, 3, 11),
              containments = List(1 -> 2, 1 -> 3, 2 -> 11, 3 -> 11)
            )
            hidden(graph, Set()) mustEqual PostIds()
            hidden(graph, Set(1)) mustEqual PostIds(2, 3, 11)
            hidden(graph, Set(2)) mustEqual PostIds()
            hidden(graph, Set(1, 2)) mustEqual PostIds(2, 3, 11)
            hidden(graph, Set(2, 3)) mustEqual PostIds(11)
          }
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
          val containment1 = Contains(1 -> 11)
          val containment2 = Contains(2 -> 11)
          val graph = Graph(
            posts = List(1, 2, 11),
            containments = List(containment1, containment2)
          )
          collapse(Set(1), graph) mustEqual dg(graph - containment1.id, collapsedContainments = Set(containment1.toLocal))
          collapse(Set(2), graph) mustEqual dg(graph - containment2.id, collapsedContainments = Set(containment2.toLocal))
          collapse(Set(1, 2), graph) mustEqual dg(graph - PostId(11))
        }

        "diamond-shape containment" in {
          val containment1 = Contains(2 -> 11)
          val containment2 = Contains(3 -> 11)
          val graph = Graph(
            posts = List(1, 2, 3, 11),
            containments = List[Contains](1 -> 2, 1 -> 3) ++ List(containment1, containment2)
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(2, 3, 11))
          collapse(Set(2), graph) mustEqual dg(graph - containment1.id, collapsedContainments = Set(containment1.toLocal))
          collapse(Set(3), graph) mustEqual dg(graph - containment2.id, collapsedContainments = Set(containment2.toLocal))
          collapse(Set(1, 2), graph) mustEqual dg(graph -- PostIds(2, 3, 11))
          collapse(Set(1, 2, 3), graph) mustEqual dg(graph -- PostIds(2, 3, 11))
        }
      }

      "cycles" - {
        "partially collapse cycle" in {
          val containment = Contains(11 -> 12)
          val graph = Graph(
            posts = List(11, 12, 13),
            containments = containment :: List[Contains](12 -> 13, 13 -> 11) // containment cycle
          )
          collapse(Set(11), graph) mustEqual dg(graph - containment.id, collapsedContainments = Set(containment.toLocal)) // nothing to collapse because of cycle
        }

        "partially collapse cycle with child" in {
          val containment = Contains(11 -> 12)
          val graph = Graph(
            posts = List(11, 12, 13, 20),
            containments = containment :: List[Contains](12 -> 13, 13 -> 11, 12 -> 20) // containment cycle -> 20
          )
          collapse(Set(11), graph) mustEqual dg(graph - PostId(20) - containment.id, collapsedContainments = Set(containment.toLocal)) // cycle stays
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
          val connection = Connects(11 -> 2)
          val graph = Graph(
            posts = List(1, 11, 2),
            containments = List(1 -> 11),
            connections = List(connection)
          )
          collapse(Set(1), graph) mustEqual dg(graph - PostId(11), Set(1 -> 2))
        }

        "redirect collapsed connection to target" in {
          val connection = Connects(2 -> 11)
          val graph = Graph(
            posts = List(1, 11, 2),
            containments = List(1 -> 11),
            connections = List(connection)
          )
          collapse(Set(1), graph) mustEqual dg(graph - PostId(11), Set(2 -> 1))
        }

        "redirect edge source to earliest collapsed transitive parent" in {
          val connection = Connects(3 -> 11)
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
          val connection = Connects(11 -> 3)
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
          val containment1 = Contains(1 -> 11)
          val containment2 = Contains(2 -> 11)
          val connection = Connects(11 -> 20)
          val graph = Graph(
            posts = List(1, 2, 11, 20),
            containments = List(containment1, containment2),
            connections = List(connection)
          )
          collapse(Set(1), graph) mustEqual dg(graph - containment1.id, collapsedContainments = Set(containment1.toLocal))
          collapse(Set(2), graph) mustEqual dg(graph - containment2.id, collapsedContainments = Set(containment2.toLocal))
          collapse(Set(1, 2), graph) mustEqual dg(graph - PostId(11), Set(1 -> 20, 2 -> 20))
        }

        "redirect and split incoming edge while collapsing two parents" in {
          val containment1 = Contains(1 -> 11)
          val containment2 = Contains(2 -> 11)
          val connection = Connects(20 -> 11)
          val graph = Graph(
            posts = List(1, 2, 11, 20),
            containments = List(containment1, containment2),
            connections = List(connection)
          )
          collapse(Set(1), graph) mustEqual dg(graph - containment1.id, collapsedContainments = Set(containment1.toLocal))
          collapse(Set(2), graph) mustEqual dg(graph - containment2.id, collapsedContainments = Set(containment2.toLocal))
          collapse(Set(1, 2), graph) mustEqual dg(graph - PostId(11), Set(20 -> 1, 20 -> 2))
        }

        "redirect and split outgoing edge while collapsing two parents with other connected child" in {
          val containment1 = Contains(1 -> 11)
          val containment2 = Contains(2 -> 11)
          val connection1 = Connects(11 -> 20)
          val connection2 = Connects(3 -> 20)
          val graph = Graph(
            posts = List(1, 2, 3, 11, 20),
            containments = List(containment1, containment2) ++ List[Contains](1 -> 3),
            connections = List(connection1, connection2)
          )
          collapse(Set(1), graph) mustEqual dg(graph - PostId(3) - containment1.id, Set(1 -> 20), collapsedContainments = Set(containment1.toLocal))
          collapse(Set(2), graph) mustEqual dg(graph - containment2.id, collapsedContainments = Set(containment2.toLocal))
          collapse(Set(1, 2), graph) mustEqual dg(graph -- PostIds(11, 3), Set(1 -> 20, 2 -> 20))
        }

        "redirect and split incoming edge while collapsing two parents (one transitive)" in {
          val containment1 = Contains(1 -> 11)
          val containment2 = Contains(2 -> 3)
          val containment3 = Contains(3 -> 11)
          val connection = Connects(20 -> 11)
          val graph = Graph(
            posts = List(1, 2, 3, 11, 20),
            containments = List(containment1, containment2, containment3),
            connections = List(connection)
          )
          // collapse(Set(1), graph) mustEqual dg(graph - containment1.id, collapsedContainments = Set(containment1.toLocal))
          collapse(Set(2), graph) mustEqual dg(graph - PostId(3), collapsedContainments = Set(LocalContainment(2, 11)))
          collapse(Set(3), graph) mustEqual dg(graph - containment3.id, collapsedContainments = Set(containment3.toLocal))
          collapse(Set(1, 2), graph) mustEqual dg(graph -- PostIds(3, 11), Set(20 -> 1, 20 -> 2))
          collapse(Set(1, 2, 3), graph) mustEqual dg(graph -- PostIds(3, 11), Set(20 -> 1, 20 -> 2))
        }

        "redirect and split incoming edge while collapsing two parents (one transitive with other parent)" in {
          val containment1 = Contains(1 -> 11)
          val containment2 = Contains(2 -> 3)
          val containment3 = Contains(3 -> 11)
          val containment4 = Contains(4 -> 3)
          val connection = Connects(20 -> 11)
          val graph = Graph(
            posts = List(1, 2, 3, 4, 11, 20),
            containments = List(containment1, containment2, containment3, containment4),
            connections = List(connection)
          )
          "\n0" + collapse(Set(1, 2), graph) mustEqual "\n1" + dg(graph - containment1.id - containment2.id, collapsedContainments = Set(containment1.toLocal, containment2.toLocal))
          collapse(Set(1, 2, 3), graph) mustEqual dg(graph -- PostIds(11) - containment2.id, Set(20 -> 1, 20 -> 3), collapsedContainments = Set(containment2.toLocal))
          collapse(Set(1, 3), graph) mustEqual dg(graph -- PostIds(11), Set(20 -> 1, 20 -> 3))
        }

        "redirect connection between children while collapsing two parents" in {
          val connection = Connects(11 -> 12)
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
          val connection1 = Connects(11 -> 2)
          val connection2 = Connects(12 -> 2)
          val graph = Graph(
            posts = List(1, 11, 12, 2),
            containments = List(1 -> 11, 1 -> 12),
            connections = List(connection1, connection2)
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(11, 12), Set(1 -> 2))
        }

        "redirect and bundle edges to source" in {
          val connection1 = Connects(2 -> 11)
          val connection2 = Connects(2 -> 12)
          val graph = Graph(
            posts = List(1, 11, 12, 2),
            containments = List(1 -> 11, 1 -> 12),
            connections = List(connection1, connection2)
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(11, 12), Set(2 -> 1))
        }

        "drop redirected, because of existing connection" in {
          val connection1 = Connects(1 -> 2)
          val connection2 = Connects(11 -> 2)
          val graph = Graph(
            posts = List(1, 11, 2),
            containments = List(1 -> 11),
            connections = List(connection1, connection2)
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(11))
        }

        "redirect mixed edges" in {
          val connection1 = Connects(2 -> 11)
          val connection2 = Connects(12 -> 2)
          val graph = Graph(
            posts = List(1, 11, 12, 2),
            containments = List(1 -> 11, 1 -> 12),
            connections = List(connection1, connection2)
          )
          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(11, 12), Set(2 -> 1, 1 -> 2))
        }

        "redirect in diamond-shape containment" in {
          val containment1 = Contains(2 -> 11)
          val containment2 = Contains(3 -> 11)
          val connection = Connects(11 -> 20)
          val graph = Graph(
            posts = List(1, 2, 3, 11, 20),
            containments = List[Contains](1 -> 2, 1 -> 3) ++ List(containment1, containment2),
            connections = List(connection)
          )

          collapse(Set(1), graph) mustEqual dg(graph -- PostIds(2, 3, 11), Set(1 -> 20))
          collapse(Set(2), graph) mustEqual dg(graph - containment1.id, collapsedContainments = Set(containment1.toLocal))
          collapse(Set(3), graph) mustEqual dg(graph - containment2.id, collapsedContainments = Set(containment2.toLocal))
          collapse(Set(1, 2), graph) mustEqual dg(graph -- PostIds(2, 3, 11), Set(1 -> 20))
          collapse(Set(2, 3), graph) mustEqual dg(graph -- PostIds(11), Set(2 -> 20, 3 -> 20))
          collapse(Set(1, 2, 3), graph) mustEqual dg(graph -- PostIds(2, 3, 11), Set(1 -> 20))
        }

        "redirect into cycle" in {
          val connection = Connects(11 -> 20)
          val containment = Contains(1 -> 2)
          val graph = Graph(
            posts = List(1, 2, 3, 11, 20),
            containments = containment :: List[Contains](2 -> 3, 3 -> 1, 2 -> 11), // containment cycle -> 11
            connections = List(connection) // 11 -> 20
          )
          collapse(Set(1), graph) mustEqual dg(graph - PostId(11) - containment.id, Set(2 -> 20), collapsedContainments = Set(containment.toLocal)) // cycle stays
        }

        "redirect out of cycle" in {
          val connection = Connects(13 -> 20)
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
