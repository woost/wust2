package wust.frontend.views

import autowire._
import boopickle.Default._
import org.scalajs.dom.document
import org.scalajs.dom.raw.{HTMLInputElement, HTMLElement}
import rx._
import wust.api._
import wust.frontend.{Client, GlobalState, RichPostFactory}
import wust.graph._
import wust.ids._
import wust.util.tags._

import scala.collection.breakOut
import scala.concurrent.duration.{span => _, _}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalatags.JsDom.all._
import org.scalajs.dom.{Event, console}
import scalatags.rx.all._

// object DevView {
//   import scala.util.Random.{nextInt => rInt, nextString => rStr}
//   val apiEvents = RxVar[List[ApiEvent]](Nil)

//   def jsError(state: GlobalState)(implicit ctx: Ctx.Owner) = {
//     span(
//       Rx {
//         (state.jsError() match {
//           case Some(error) =>
//             pre(
//               position.fixed, right := 0, bottom := 50,
//               border := "1px solid #FFD7D7", backgroundColor := "#FFF0F0", color := "#C41A16",
//               width := "90%", margin := 10, padding := 10, whiteSpace := "pre-wrap",
//               error
//             )
//           case None => span()
//         }).render
//       }
//     )
//   }

//   def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
//     span(
//       div(
//         display.flex, flexDirection.column,
//         Rx {
//           val users = List("a", "b", "c", "d", "e", "f", "g")
//           div(
//             "login: ",
//             users.map(u => button(u, onclick := { () =>
//               Client.auth.register(u, u).call().filter(_ == false).foreach { _ =>
//                 Client.auth.logout().call().foreach { _ =>
//                   Client.auth.login(u, u).call()
//                 }
//               }
//             }))
//           ).render
//         },
//         Rx {
//           def addRandomPost(count: Int) {
//             val newPosts = List.fill(count)(Post.newId(rStr(1 + rInt(20))))
//             val containments = newPosts.flatMap(p => GraphSelection.toContainments(state.graphSelection.now, p.id))
//             state.persistence.addChanges(addPosts = newPosts, addContainments = containments)
//           }
//           div(
//             button("create random post", onclick := { () => addRandomPost(1) }),
//             button("10", onclick := { () => addRandomPost(10) }),
//             button("100", onclick := { () => addRandomPost(100) })
//           ).render
//         },
//         Rx {
//           val posts = scala.util.Random.shuffle(state.displayGraphWithoutParents().graph.postIds.toSeq)
//           def deletePost(ids: Seq[PostId]) { state.persistence.addChanges(delPosts = ids) }
//           div(
//             button("delete random post", onclick := { () => deletePost(posts.take(1)) }),
//             button("10", onclick := { () => deletePost(posts.take(10)) }),
//             button("100", onclick := { () => deletePost(posts.take(100)) })
//           ).render
//         },
//         div(
//           "Random Events:",
//           br(),
//           {
//             import scalajs.js.timers._
//             def graph = state.rawGraph.now

//             def randomPostId: Option[PostId] = if (graph.postsById.size > 0) Option((graph.postIds.toIndexedSeq)(rInt(graph.postsById.size))) else None
//             def randomConnection: Option[Connection] = if (graph.connections.size > 0) Option((graph.connections.toIndexedSeq)(rInt(graph.connections.size))) else None
//             def randomContainment: Option[Containment] = if (graph.containments.size > 0) Option((graph.containments.toIndexedSeq)(rInt(graph.containments.size))) else None
//             val numberOfConcurrentEvents = 3
//             val events: Array[() => Option[GraphChanges]] = {
//               val distribution: List[(Int, () => Option[GraphChanges])] = (
//                 (4, () => Option(GraphChanges(addPosts = Set(Post.newId(rStr(1 + rInt(20))))))) ::
//                 (3, () => randomPostId.map(p => GraphChanges(updatePosts = Set(Post(p, rStr(1 + rInt(20))))))) ::
//                 (2, () => randomPostId.map(p => GraphChanges(delPosts = Set(p)))) ::
//                 (3, () => for (p1 <- randomPostId; p2 <- randomPostId) yield GraphChanges(addConnections = Set(Connection(p1, p2)))) ::
//                 (2, () => randomConnection.map(c => GraphChanges(delConnections = Set(c)))) ::
//                 (3, () => for (p1 <- randomPostId; p2 <- randomPostId) yield GraphChanges(addContainments = Set(Containment(p1, p2)))) ::
//                 (2, () => randomContainment.map(c => GraphChanges(delContainments = Set(c)))) ::
//                 Nil
//               )
//               distribution.flatMap { case (count, f) => List.fill(count)(f) }(breakOut)
//             }
//             def randomEvent = events(rInt(events.size))()

//             def emitRandomEvent() {
//               val changes = List.fill(numberOfConcurrentEvents)(randomEvent)
//                 .flatten
//                 .foldLeft(GraphChanges.empty)(_ merge _)
//               state.persistence.addChanges(changes)
//             }
//             var interval: Option[SetIntervalHandle] = None
//             val intervals = (
//               5.seconds ::
//               2.seconds ::
//               1.seconds ::
//               0.5.seconds ::
//               0.1.seconds ::
//               Duration.Inf ::
//               Nil
//             )
//             val prefix = "DevViewRandomEventTimer"
//             for (i <- intervals) yield {
//               val iid = s"$prefix$i"
//               i match {
//                 case i: FiniteDuration =>
//                   span(radio(name := prefix, id := iid), labelfor(iid)(s"${i.toMillis / 1000.0}s"), onclick := { () =>
//                     interval.foreach(clearInterval)
//                     interval = Option(setInterval(i)(emitRandomEvent))
//                   })
//                 case _ =>
//                   span(radio(name := prefix, id := iid, checked), labelfor(iid)(s"none"), onclick := { () =>
//                     interval.foreach(clearInterval)
//                     interval = None
//                   })
//               }
//             }
//           }
//         ) // ,Rx {
//         //   state.rawGraph().toSummaryString
//         // }
//         , pre(maxWidth := "400px", maxHeight := "300px", overflow.scroll, fontSize := "11px", Rx {
//           apiEvents().mkString("\n")
//           // pre(apiEvents().mkString("\n")).render
//         }), button("clear", onclick := { () => apiEvents() = Nil })
//       ),
//       Rx {
//         (state.jsError() match {
//           case Some(error) =>
//             pre(
//               position.fixed, right := 0, bottom := 50,
//               border := "1px solid #FFD7D7", backgroundColor := "#FFF0F0", color := "#C41A16",
//               width := "90%", margin := 10, padding := 10, whiteSpace := "pre-wrap",
//               error
//             )
//           case None => span()
//         }).render
//       }
//     )
//   }
// }
