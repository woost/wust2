// package wust.graph

// import org.scalatest._
// import wust.ids._
// import java.time.LocalDateTime

// import scala.collection.breakOut

// class CollapseSpec extends FreeSpec with MustMatchers {
//   implicit def intToUuidType(id: Int): UuidType = id.toString
//   implicit def intToPostId(id: Int): PostId = PostId(id.toString)
//   implicit def intToPost(id: Int): Post = Post(id.toString, "title", LocalDateTime.now)
//   def containments(ts: List[(Int, Int)]): List[Connection] = ts.map(Containment)
//   implicit def intSetToSelectorIdSet(set: Set[Int]): Selector.IdSet = Selector.IdSet(set.map(id => PostId(id.toString)))
//   def PostIds(ids: Int*): Set[PostId] = ids.map(id => PostId(id.toString))(breakOut)

//   implicit class RichConnection(con: Connection) {
//     def toLocal = {
//       if (con.label != Label.Parent) throw new Exception("Connection is not containment!")
//       LocalConnection(con.sourceId, con.label, con.targetId)
//     }
//   }
//   def Containment(ts: (Int, Int)): Connection = new Connection(ts._2.toString, Label.Parent, ts._1.toString)
//   def Connection(ts: (Int, Int)): Connection = new Connection(ts._1.toString, "a-test-label", ts._2.toString)

//   "collapse" - {

//     def collapse(collapsing: Selector, graph: Graph): DisplayGraph = Collapse(collapsing)(DisplayGraph(graph))
//     def dg(graph: Graph, redirected: Set[(Int, Int)] = Set.empty, collapsedContainments: Set[LocalConnection] = Set.empty) = {
//       DisplayGraph(
//         graph,
//         redirectedConnections = redirected.map { case (source, target) => LocalConnection(source, Label("redirected"), target) },
//         collapsedContainments = collapsedContainments
//       )
//     }

//     "helpers" - {
//       "getHiddenPosts" - {
//         import Collapse.{getHiddenPosts => hidden}
//         "one parent" in {
//           val graph = Graph(
//             posts = List(1, 11),
//             containments(List(1 -> 11))
//           )
//           hidden(graph, Set()) mustEqual PostIds() // if none are collapsed, none are hidden
//           hidden(graph, Set(1)) mustEqual PostIds(11)
//         }

//         "two parents" in {
//           val graph = Graph(
//             posts = List(1, 2, 11),
//             containments(List(1 -> 11, 2 -> 11))
//           )
//           hidden(graph, Set()) mustEqual PostIds()
//           hidden(graph, Set(1)) mustEqual PostIds()
//           hidden(graph, Set(2)) mustEqual PostIds()
//           hidden(graph, Set(1, 2)) mustEqual PostIds(11)
//         }

//         "one transitive parent" in {
//           val graph = Graph(
//             posts = List(1, 2, 11),
//             containments(List(1 -> 2, 2 -> 11))
//           )
//           hidden(graph, Set()) mustEqual PostIds()
//           hidden(graph, Set(1)) mustEqual PostIds(2, 11)
//           hidden(graph, Set(2)) mustEqual PostIds(11)
//           hidden(graph, Set(1, 2)) mustEqual PostIds(2, 11)
//         }

//         "two transitive parents" in {
//           val graph = Graph(
//             posts = List(1, 2, 3, 4, 11),
//             containments(List(1 -> 2, 2 -> 11, 3 -> 4, 4 -> 11))
//           )
//           hidden(graph, Set()) mustEqual PostIds()
//           hidden(graph, Set(1)) mustEqual PostIds(2)
//           hidden(graph, Set(2)) mustEqual PostIds()
//           hidden(graph, Set(1, 2)) mustEqual PostIds(2)
//           hidden(graph, Set(1, 3)) mustEqual PostIds(2, 4, 11)
//           hidden(graph, Set(1, 4)) mustEqual PostIds(2, 11)
//         }

//         "two parents, one has two other parents" in {
//           val graph = Graph(
//             posts = List(1, 2, 3, 4, 11),
//             containments(List(1 -> 11, 3 -> 11, 2 -> 3, 4 -> 3))
//           )
//           hidden(graph, Set()) mustEqual PostIds()
//           hidden(graph, Set(1)) mustEqual PostIds()
//           hidden(graph, Set(2)) mustEqual PostIds()
//           hidden(graph, Set(1, 2)) mustEqual PostIds()
//           hidden(graph, Set(1, 2, 4)) mustEqual PostIds(3, 11)
//           hidden(graph, Set(1, 2, 3)) mustEqual PostIds(11)
//         }

//         "two parents, one has 2 transitive parents" in {
//           val graph = Graph(
//             posts = List(1, 2, 3, 4, 11),
//             containments(List(1 -> 2, 2 -> 3, 3 -> 11, 4 -> 11))
//           )
//           hidden(graph, Set()) mustEqual PostIds()
//           hidden(graph, Set(1)) mustEqual PostIds(2, 3)
//           hidden(graph, Set(2)) mustEqual PostIds(3)
//           hidden(graph, Set(3)) mustEqual PostIds()
//           hidden(graph, Set(1, 2)) mustEqual PostIds(2, 3)
//           hidden(graph, Set(2, 3)) mustEqual PostIds(3)
//           hidden(graph, Set(1, 2, 3)) mustEqual PostIds(2, 3)
//           hidden(graph, Set(1, 4)) mustEqual PostIds(2, 3, 11)
//           hidden(graph, Set(2, 4)) mustEqual PostIds(3, 11)
//           hidden(graph, Set(3, 4)) mustEqual PostIds(11)
//         }

//         "cycle" in {
//           val graph = Graph(
//             posts = List(1, 2, 3),
//             containments(List(1 -> 2, 2 -> 3, 3 -> 1))
//           )
//           hidden(graph, Set()) mustEqual PostIds()
//           hidden(graph, Set(1)) mustEqual PostIds()
//         }

//         "cycle with child" in {
//           val graph = Graph(
//             posts = List(1, 2, 3, 4),
//             containments(List(1 -> 2, 2 -> 3, 3 -> 1, 3 -> 4))
//           )
//           hidden(graph, Set()) mustEqual PostIds()
//           hidden(graph, Set(1)) mustEqual PostIds(4)
//           hidden(graph, Set(2)) mustEqual PostIds(4)
//           hidden(graph, Set(3)) mustEqual PostIds(4)
//         }

//         "cycle with parent" in {
//           val graph = Graph(
//             posts = List(1, 2, 3, 4),
//             containments(List(4 -> 1, 1 -> 2, 2 -> 3, 3 -> 1))
//           )
//           hidden(graph, Set()) mustEqual PostIds()
//           hidden(graph, Set(1)) mustEqual PostIds()
//           hidden(graph, Set(2)) mustEqual PostIds()
//           hidden(graph, Set(3)) mustEqual PostIds()
//           hidden(graph, Set(4)) mustEqual PostIds(1, 2, 3)
//         }

//         "cycle with child and parent" in {
//           val graph = Graph(
//             posts = List(1, 2, 3, 4, 5),
//             containments(List(4 -> 1, 1 -> 2, 2 -> 3, 3 -> 1, 3 -> 1, 3 -> 5)) // 4 -> cycle -> 5
//           )
//           hidden(graph, Set()) mustEqual PostIds()
//           hidden(graph, Set(1)) mustEqual PostIds(5)
//           hidden(graph, Set(2)) mustEqual PostIds(5)
//           hidden(graph, Set(3)) mustEqual PostIds(5)
//           hidden(graph, Set(4)) mustEqual PostIds(1, 2, 3, 5)
//         }

//         "diamond" in {
//           val graph = Graph(
//             posts = List(1, 2, 3, 11),
//             containments(List(1 -> 2, 1 -> 3, 2 -> 11, 3 -> 11))
//           )
//           hidden(graph, Set()) mustEqual PostIds()
//           hidden(graph, Set(1)) mustEqual PostIds(2, 3, 11)
//           hidden(graph, Set(2)) mustEqual PostIds()
//           hidden(graph, Set(1, 2)) mustEqual PostIds(2, 3, 11)
//           hidden(graph, Set(2, 3)) mustEqual PostIds(11)
//         }
//       }

//       "involvedInContainmentCycleWithCollapsedPost" - {
//         import Collapse.{involvedInCycleWithCollapsedPost => inCycle}
//         "involved" in {
//           val graph = Graph(
//             posts = List(11, 12, 13),
//             containments(List(11 -> 12, 12 -> 13, 13 -> 11)) // containment cycle
//           )
//           inCycle(graph, PostId(11), PostIds(12)) mustEqual true
//         }

//         "not involved" in {
//           val graph = Graph(
//             posts = List(11, 12, 13, 14),
//             containments(List(11 -> 12, 12 -> 13, 13 -> 14, 13 -> 11)) // containment cycle
//           )
//           inCycle(graph, PostId(14), PostIds(12)) mustEqual false
//         }
//       }

//       "getLocalContainments" - {
//         import Collapse.{getLocalContainments => local}

//         "partially collapse cycle" in {
//           val containment = Containment(11 -> 12)
//           val graph = Graph(
//             posts = List(11, 12, 13),
//             containment :: containments(List(12 -> 13, 13 -> 11)) // containment cycle
//           )
//           val collapsingPosts = PostIds(11)
//           val hiddenPosts = PostIds()
//           val hiddenContainments = Set(containment)

//           local(graph, hiddenPosts, hiddenContainments, collapsingPosts) mustEqual Set(containment.toLocal)
//         }

//         "two parents, one with other child" in { //TODO
//           val containment1 = Containment(1 -> 11)
//           val containment2 = Containment(2 -> 11)
//           val containment3 = Containment(1 -> 3)
//           val graph = Graph(
//             posts = List(1, 2, 3, 11),
//             List(containment1, containment2, containment3)
//           )
//           val collapsingPosts = PostIds(1)
//           val hiddenPosts = PostIds(3)
//           val hiddenContainments = Set(containment1, containment3)
//           local(graph, hiddenPosts, hiddenContainments, collapsingPosts) mustEqual Set(containment1.toLocal) // containment1 becomes local, because PostId(11) is still visible, because of containment2

//           // collapse(Set(1), graph) mustEqual dg(graph - PostId(3) - containment1, Set(1 -> 20), collapsedContainments = Set(containment1.toLocal))
//           // collapse(Set(2), graph) mustEqual dg(graph - containment2, collapsedContainments = Set(containment2.toLocal))
//           // collapse(Set(1, 2), graph) mustEqual dg(graph removePosts PostIds(11, 3), Set(1 -> 20, 2 -> 20))
//         }
//       }
//       "getAlternativePosts" - {
//         import Collapse.{getAlternativePosts => alt}

//         "two parents, one with two more parents" in {
//           val containment1 = Containment(1 -> 11)
//           val containment2 = Containment(2 -> 3)
//           val containment3 = Containment(3 -> 11)
//           val containment4 = Containment(4 -> 3)
//           val graph = Graph(
//             posts = List(1, 2, 3, 4, 11),
//             List(containment1, containment2, containment3, containment4)
//           )

//           val collapsingPosts = PostIds(1, 2, 4)
//           val hiddenPosts = PostIds(3, 11)

//           alt(graph, hiddenPosts, collapsingPosts) mustEqual Map(PostId(11) -> PostIds(1, 2, 4), PostId(3) -> PostIds(2, 4))
//         }

//         "two parents, one with one more parent" in {
//           val containment1 = Containment(1 -> 11)
//           val containment2 = Containment(2 -> 3)
//           val containment3 = Containment(3 -> 11)
//           val graph = Graph(
//             posts = List(1, 2, 3, 11),
//             List(containment1, containment2, containment3)
//           )

//           val collapsingPosts = PostIds(2)
//           val hiddenPosts = PostIds(3)

//           alt(graph, hiddenPosts, collapsingPosts) mustEqual Map(PostId(3) -> PostIds(2))
//         }
//       }
//     }

//     "base cases" - {
//       "collapse parent" in {
//         val graph = Graph(
//           posts = List(1, 11),
//           containments(List(1 -> 11))
//         )
//         collapse(Set(1), graph) mustEqual dg(graph - PostId(11))
//       }

//       "collapse child" in {
//         val graph = Graph(
//           posts = List(1, 11),
//           containments(List(1 -> 11))
//         )
//         collapse(Set(11), graph) mustEqual dg(graph)
//       }

//       "collapse transitive children" in {
//         val graph = Graph(
//           posts = List(1, 11, 12),
//           containments(List(1 -> 11, 11 -> 12))
//         )
//         collapse(Set(1), graph) mustEqual dg(graph removePosts PostIds(11, 12))
//       }

//       "collapse multiple, transitive parents" in {
//         val graph = Graph(
//           posts = List(1, 2, 3),
//           containments(List(1 -> 2, 2 -> 3))
//         )
//         collapse(Set(1), graph) mustEqual dg(graph removePosts PostIds(2, 3))
//         collapse(Set(2), graph) mustEqual dg(graph - PostId(3))
//         collapse(Set(1, 2), graph) mustEqual dg(graph removePosts PostIds(2, 3))
//       }

//       "collapse children while having parent" in {
//         val graph = Graph(
//           posts = List(1, 11, 12),
//           containments(List(1 -> 11, 11 -> 12))
//         )
//         collapse(Set(11), graph) mustEqual dg(graph - PostId(12))
//       }

//       "collapse two parents" in {
//         val containment1 = Containment(1 -> 11)
//         val containment2 = Containment(2 -> 11)
//         val graph = Graph(
//           posts = List(1, 2, 11),
//           List(containment1, containment2)
//         )
//         collapse(Set(1), graph) mustEqual dg(graph - containment1, collapsedContainments = Set(containment1.toLocal))
//         collapse(Set(2), graph) mustEqual dg(graph - containment2, collapsedContainments = Set(containment2.toLocal))
//         collapse(Set(1, 2), graph) mustEqual dg(graph - PostId(11))
//       }

//       "diamond-shape containment" in {
//         val containment1 = Containment(2 -> 11)
//         val containment2 = Containment(3 -> 11)
//         val graph = Graph(
//           posts = List(1, 2, 3, 11),
//           containments(List(1 -> 2, 1 -> 3)) ++ List(containment1, containment2)
//         )
//         collapse(Set(1), graph) mustEqual dg(graph removePosts PostIds(2, 3, 11))
//         collapse(Set(2), graph) mustEqual dg(graph - containment1, collapsedContainments = Set(containment1.toLocal))
//         collapse(Set(3), graph) mustEqual dg(graph - containment2, collapsedContainments = Set(containment2.toLocal))
//         collapse(Set(1, 2), graph) mustEqual dg(graph removePosts PostIds(2, 3, 11))
//         collapse(Set(1, 2, 3), graph) mustEqual dg(graph removePosts PostIds(2, 3, 11))
//       }
//     }

//     "cycles" - {
//       "partially collapse cycle" in {
//         val containment = Containment(11 -> 12)
//         val graph = Graph(
//           posts = List(11, 12, 13),
//           containment :: containments(List(12 -> 13, 13 -> 11)) // containment cycle
//         )
//         collapse(Set(11), graph) mustEqual dg(graph - containment, collapsedContainments = Set(containment.toLocal)) // nothing to collapse because of cycle
//       }

//       "partially collapse cycle with child" in {
//         val containment = Containment(11 -> 12)
//         val graph = Graph(
//           posts = List(11, 12, 13, 20),
//           containment :: containments(List(12 -> 13, 13 -> 11, 12 -> 20)) // containment cycle -> 20
//         )
//         collapse(Set(11), graph) mustEqual dg(graph - PostId(20) - containment, collapsedContainments = Set(containment.toLocal)) // cycle stays
//       }

//       "collapse parent with child-cycle" in {
//         val graph = Graph(
//           posts = List(1, 11, 12, 13),
//           containments(List(1 -> 11, 11 -> 12, 12 -> 13, 13 -> 11)) // 1 -> containment cycle
//         )
//         collapse(Set(1), graph) mustEqual dg(graph removePosts PostIds(11, 12, 13))
//       }
//     }

//     "connection redirection" - {
//       "redirect collapsed connection to source" in {
//         val connection = Connection(11 -> 2)
//         val graph = Graph(
//           posts = List(1, 11, 2),
//           connections = List(connection) ::: containments(List(1 -> 11))
//         )
//         collapse(Set(1), graph) mustEqual dg(graph - PostId(11), Set(1 -> 2))
//       }

//       "redirect collapsed connection to target" in {
//         val connection = Connection(2 -> 11)
//         val graph = Graph(
//           posts = List(1, 11, 2),
//           List(connection) ::: containments(List(1 -> 11))
//         )
//         collapse(Set(1), graph) mustEqual dg(graph - PostId(11), Set(2 -> 1))
//       }

//       "redirect edge source to earliest collapsed transitive parent" in {
//         val connection = Connection(3 -> 11)
//         val graph = Graph(
//           posts = List(1, 2, 3, 11),
//           containments(List(1 -> 2, 2 -> 3)) ::: List(connection)
//         )
//         collapse(Set(1), graph) mustEqual dg(graph removePosts PostIds(2, 3), Set(1 -> 11))
//         collapse(Set(2), graph) mustEqual dg(graph - PostId(3), Set(2 -> 11))
//         collapse(Set(1, 2), graph) mustEqual dg(graph removePosts PostIds(2, 3), Set(1 -> 11))
//       }

//       "redirect edge target to earliest collapsed transitive parent" in {
//         val connection = Connection(11 -> 3)
//         val graph = Graph(
//           posts = List(1, 2, 3, 11),
//           containments(List(1 -> 2, 2 -> 3)) ::: List(connection)
//         )
//         collapse(Set(1), graph) mustEqual dg(graph removePosts PostIds(2, 3), Set(11 -> 1))
//         collapse(Set(2), graph) mustEqual dg(graph - PostId(3), Set(11 -> 2))
//         collapse(Set(1, 2), graph) mustEqual dg(graph removePosts PostIds(2, 3), Set(11 -> 1))
//       }

//       "redirect and split outgoing edge while collapsing two parents" in {
//         val containment1 = Containment(1 -> 11)
//         val containment2 = Containment(2 -> 11)
//         val connection = Connection(11 -> 20)
//         val graph = Graph(
//           posts = List(1, 2, 11, 20),
//           List(containment1, containment2) ::: List(connection)
//         )
//         collapse(Set(1), graph) mustEqual dg(graph - containment1, collapsedContainments = Set(containment1.toLocal))
//         collapse(Set(2), graph) mustEqual dg(graph - containment2, collapsedContainments = Set(containment2.toLocal))
//         collapse(Set(1, 2), graph) mustEqual dg(graph - PostId(11), Set(1 -> 20, 2 -> 20))
//       }

//       "redirect and split incoming edge while collapsing two parents" in {
//         val containment1 = Containment(1 -> 11)
//         val containment2 = Containment(2 -> 11)
//         val connection = Connection(20 -> 11)
//         val graph = Graph(
//           posts = List(1, 2, 11, 20),
//           List(containment1, containment2) ::: List(connection)
//         )
//         collapse(Set(1), graph) mustEqual dg(graph - containment1, collapsedContainments = Set(containment1.toLocal))
//         collapse(Set(2), graph) mustEqual dg(graph - containment2, collapsedContainments = Set(containment2.toLocal))
//         collapse(Set(1, 2), graph) mustEqual dg(graph - PostId(11), Set(20 -> 1, 20 -> 2))
//       }

//       "redirect and split outgoing edge while collapsing two parents with other connected child" in {
//         val containment1 = Containment(1 -> 11)
//         val containment2 = Containment(2 -> 11)
//         val connection1 = Connection(11 -> 20)
//         val connection2 = Connection(3 -> 20)
//         val graph = Graph(
//           posts = List(1, 2, 3, 11, 20),
//           List(containment1, containment2) ++ containments(List(1 -> 3)) ::: List(connection1, connection2)
//         )
//         collapse(Set(1), graph) mustEqual dg(graph - PostId(3) - containment1, Set(1 -> 20), collapsedContainments = Set(containment1.toLocal))
//         collapse(Set(2), graph) mustEqual dg(graph - containment2, collapsedContainments = Set(containment2.toLocal))
//         collapse(Set(1, 2), graph) mustEqual dg(graph removePosts PostIds(11, 3), Set(1 -> 20, 2 -> 20))
//       }

//       "redirect and split incoming edge while collapsing two parents (one transitive)" in {
//         val containment1 = Containment(1 ->11)
//         val containment2 = Containment(2 -> 3)
//         val containment3 = Containment(3 -> 11)
//         val connection = Connection(20 -> 11)
//         val graph = Graph(
//           posts = List(1, 2, 3, 11, 20),
//           List(containment1, containment2, containment3) ::: List(connection)
//         )
//         collapse(Set(1), graph) mustEqual dg(graph - containment1, collapsedContainments = Set(containment1.toLocal))
//         collapse(Set(2), graph) mustEqual dg(graph - PostId(3), collapsedContainments = Set(LocalConnection(11, Label.Parent, 2)))
//         collapse(Set(3), graph) mustEqual dg(graph - containment3, collapsedContainments = Set(containment3.toLocal))
//         collapse(Set(1, 2), graph) mustEqual dg(graph removePosts PostIds(3, 11), Set(20 -> 1, 20 -> 2))
//         collapse(Set(1, 2, 3), graph) mustEqual dg(graph removePosts PostIds(3, 11), Set(20 -> 1, 20 -> 2))
//       }

//       "redirect and split incoming edge while collapsing two parents (one transitive with other parent)" in {
//         val containment1 = Containment(1 -> 11)
//         val containment2 = Containment(2 -> 3)
//         val containment3 = Containment(3 -> 11)
//         val containment4 = Containment(4 -> 3)
//         val connection = Connection(20 -> 11)
//         val graph = Graph(
//           posts = List(1, 2, 3, 4, 11, 20),
//           List(containment1, containment2, containment3, containment4) ::: List(connection)
//         )
//         collapse(Set(1, 2), graph) mustEqual dg(graph - containment1- containment2, collapsedContainments = Set(containment1.toLocal, containment2.toLocal, LocalConnection(11, Label.Parent, 2)))
//         collapse(Set(1, 2, 3), graph) mustEqual dg((graph removePosts PostIds(11)) - containment2, Set(20 -> 1, 20 -> 3), collapsedContainments = Set(containment2.toLocal))
//         collapse(Set(1, 3), graph) mustEqual dg(graph removePosts PostIds(11), Set(20 -> 1, 20 -> 3))
//         collapse(Set(1, 2, 4), graph) mustEqual dg(graph removePosts PostIds(3, 11), Set(20 -> 1, 20 -> 4, 20 -> 2))
//       }

//       "redirect connection between children while collapsing two parents" in {
//         val connection = Connection(11 -> 12)
//         val graph = Graph(
//           posts = List(1, 2, 11, 12),
//           containments(List(1 -> 11, 2 -> 12)) ::: List(connection)
//         )
//         collapse(Set(1), graph) mustEqual dg(graph - PostId(11), Set(1 -> 12))
//         collapse(Set(2), graph) mustEqual dg(graph - PostId(12), Set(11 -> 2))
//         collapse(Set(1, 2), graph) mustEqual dg(graph removePosts PostIds(11, 12), Set(1 -> 2))
//       }

//       "redirect and bundle edges to target" in {
//         val connection1 = Connection(11 -> 2)
//         val connection2 = Connection(12 -> 2)
//         val graph = Graph(
//           posts = List(1, 11, 12, 2),
//           containments(List(1 -> 11, 1 -> 12)) ::: List(connection1, connection2)
//         )
//         collapse(Set(1), graph) mustEqual dg(graph removePosts PostIds(11, 12), Set(1 -> 2))
//       }

//       "redirect and bundle edges to source" in {
//         val connection1 = Connection(2 -> 11)
//         val connection2 = Connection(2 -> 12)
//         val graph = Graph(
//           posts = List(1, 11, 12, 2),
//           containments(List(1 -> 11, 1 -> 12)) ::: List(connection1, connection2)
//         )
//         collapse(Set(1), graph) mustEqual dg(graph removePosts PostIds(11, 12), Set(2 -> 1))
//       }

//       "drop redirected, because of existing connection" in {
//         val connection1 = Connection(1 -> 2)
//         val connection2 = Connection(11 -> 2)
//         val graph = Graph(
//           posts = List(1, 11, 2),
//           containments(List(1 -> 11)) ::: List(connection1, connection2)
//         )
//         collapse(Set(1), graph) mustEqual dg(graph removePosts PostIds(11))
//       }

//       "redirect mixed edges" in {
//         val connection1 = Connection(2 -> 11)
//         val connection2 = Connection(12 -> 2)
//         val graph = Graph(
//           posts = List(1, 11, 12, 2),
//           containments(List(1 -> 11, 1 -> 12)) ::: List(connection1, connection2)
//         )
//         collapse(Set(1), graph) mustEqual dg(graph removePosts PostIds(11, 12), Set(2 -> 1, 1 -> 2))
//       }

//       "redirect in diamond-shape containment" in {
//         val containment1 = Containment(2 -> 11)
//         val containment2 = Containment(3 -> 11)
//         val connection = Connection(11 -> 20)
//         val graph = Graph(
//           posts = List(1, 2, 3, 11, 20),
//           containments(List(1 -> 2, 1 -> 3)) ++ List(containment1, containment2) ::: List(connection)
//         )

//         collapse(Set(1), graph) mustEqual dg(graph removePosts PostIds(2, 3, 11), Set(1 -> 20))
//         collapse(Set(2), graph) mustEqual dg(graph - containment1, collapsedContainments = Set(containment1.toLocal))
//         collapse(Set(3), graph) mustEqual dg(graph - containment2, collapsedContainments = Set(containment2.toLocal))
//         collapse(Set(1, 2), graph) mustEqual dg(graph removePosts PostIds(2, 3, 11), Set(1 -> 20))
//         collapse(Set(2, 3), graph) mustEqual dg(graph removePosts PostIds(11), Set(2 -> 20, 3 -> 20))
//         collapse(Set(1, 2, 3), graph) mustEqual dg(graph removePosts PostIds(2, 3, 11), Set(1 -> 20))
//       }

//       "redirect into cycle" in {
//         val connection = Connection(11 -> 20)
//         val containment = Containment(1 -> 2)
//         val graph = Graph(
//           posts = List(1, 2, 3, 11, 20),
//           containment :: containments(List(2 -> 3, 3 -> 1, 2 -> 11)) // containment cycle -> 11
//           ::: List(connection) // 11 -> 20
//         )
//         collapse(Set(1), graph) mustEqual dg(graph - PostId(11) - containment, Set(2 -> 20), collapsedContainments = Set(containment.toLocal)) // cycle stays
//       }

//       "redirect out of cycle" in {
//         val connection = Connection(13 -> 20)
//         val graph = Graph(
//           posts = List(1, 11, 12, 13, 20),
//           containments(List(1 -> 11, 11 -> 12, 12 -> 13, 13 -> 11)) // 1 -> containment cycle(11,12,13 :::
//           ::: List(connection) // 13 -> 20
//         )
//         collapse(Set(1), graph) mustEqual dg(graph removePosts PostIds(11, 12, 13), Set(1 -> 20))
//       }
//     }
//   }
// }
