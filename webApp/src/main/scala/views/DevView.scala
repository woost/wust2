package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.dsl.styles.extra._
import rx._
import wust.api._
import wust.graph._
import wust.ids._
import wust.webApp.GlobalState
import wust.webApp.outwatchHelpers._

import scala.collection.mutable
import scala.concurrent.duration.{span => _}

object DevView {

  def button = outwatch.dom.dsl.button(fontSize := "14px", padding := "0px 5px", margin := "0px 1px")

  def devPeek(state: GlobalState, additions: Seq[VDomModifier] = Nil)(implicit ctx: Ctx.Owner) = {
    val show = Var(false)
    val activeDisplay = display <-- show.map(if (_) "block" else "none")
    val inactiveDisplay = display <-- show.map(if (_) "none" else "block")

    val baseDiv = div(position.fixed, top := "100px", right := "0", boxSizing.borderBox,
      padding := "5px", backgroundColor <-- state.pageStyle.map(_.bgColor.toHex), border  <-- state.pageStyle.map(c => s"1px solid ${c.accentLineColor.toHex}"), cls := "shadow")


    div(
      baseDiv(
        inactiveDisplay,
        "DevView",
        transform := "rotate(-90deg) translate(0,-100%)",
        transformOrigin := "100% 0",
        cursor.pointer,
        borderBottom := "none", //TODO: this is not recognized. outwatch-bug?
        onClick(true) --> show
      ),
      baseDiv(
        minWidth := "200px",
        activeDisplay,
        div("×", cursor.pointer, float.right, onClick(false) --> show),
        DevView(state, additions),
        borderRight := "none",
      )
    )
  }


  import scala.util.Random.{nextInt => rInt}
  def rWord = scala.util.Random.alphanumeric.take(3 + rInt(6)).mkString
  def rSentence = Array.fill(1 + rInt(5))(rWord).mkString(" ")

  val apiEvents = Var[List[ApiEvent]](Nil)

  //  def jsError(state: GlobalState)(implicit ctx: Ctx.Owner) = {
  //    span(
  //      Rx {
  //        (state.jsError() match {
  //          case Some(error) =>
  //            pre(
  //              position.fixed, right := 0, bottom := 50,
  //              border := "1px solid #FFD7D7", backgroundColor := "#FFF0F0", color := "#C41A16",
  //              width := "90%", margin := 10, padding := 10, whiteSpace := "pre-wrap",
  //              error
  //            )
  //          case None => span()
  //        }).render
  //      }
  //    )
  //  }

  def apply(state: GlobalState, additions: Seq[VDomModifier])(implicit ctx: Ctx.Owner) = {
    span(
      div(
        display.flex, flexDirection.column,
//        Rx {
//          val users = List("a", "b", "c", "d", "e", "f", "g")
//          div(
//            "login: ",
//            users.map(u => button(u, onClick --> sideEffect {
//              Client.auth.register(u, u).call().filter(_ == false).foreach { _ =>
//                Client.auth.logout().call().foreach { _ =>
//                  Client.auth.login(u, u).call()
//                }
//              }
//            }))
//          ).render
//        },
        Rx {
         def addRandomPost(count: Int):Unit = {
            val newPosts = List.fill(count)(Post(PostId.fresh, content = PostContent.Text(rSentence), state.currentUser.now.id)).toSet
            val changes = GraphChanges(addPosts = newPosts)
            state.eventProcessor.enriched.changes.onNext(changes)
          }

          div(
            "create: ",
            button("1", onClick --> sideEffect { addRandomPost(1) }),
            button("10", onClick --> sideEffect { addRandomPost(10) }),
            button("100", onClick --> sideEffect { addRandomPost(100) })
          )
        },
        Rx {
          val posts = scala.util.Random.shuffle(state.displayGraphWithoutParents().graph.postIds.toSeq)

          def deletePost(ids: Seq[PostId]):Unit = {
            state.eventProcessor.changes.onNext(GraphChanges(delPosts = ids.toSet))
          }

          div(
            "delete: ",
            button("1", onClick --> sideEffect { deletePost(posts.take(1)) }),
            button("10", onClick --> sideEffect { deletePost(posts.take(10)) }),
            button("100", onClick --> sideEffect { deletePost(posts.take(100)) })
          )
        },
        Rx {
          val posts = state.displayGraphWithoutParents().graph.postIds.toArray
          def randomConnection = Connection(posts(rInt(posts.length)), Label(rWord), posts(rInt(posts.length)))

          def connect(_count:Int):Unit = {
            if(posts.length > 1) {
              val count = _count min ((posts.length * posts.length - 1) / 2)
              val selected = mutable.HashSet.empty[Connection]
              while (selected.size < count) {
                selected += randomConnection
              }

              state.eventProcessor.changes.onNext(GraphChanges(addConnections = selected.toSet))
            }
          }

          div(
            "connect: ",
            button("1", onClick --> sideEffect { connect(1) }),
            button("10", onClick --> sideEffect {connect(10)}),
            button("100", onClick --> sideEffect {connect(100)})
          )
        },
        Rx {
          val posts = state.displayGraphWithoutParents().graph.postIds.toArray
          def randomConnection = Connection(posts(rInt(posts.length)), Label.parent, posts(rInt(posts.length)))

          def contain(count:Int):Unit = {
            state.eventProcessor.changes.onNext(GraphChanges(addConnections = Array.fill(count)(randomConnection).toSet))
          }

          div(
            "contain: ",
            button("1", onClick --> sideEffect { contain(1) }),
            button("10", onClick --> sideEffect {contain(10)}),
            button("100", onClick --> sideEffect {contain(100)})
          )
        },
        Rx {
          val connections = scala.util.Random.shuffle(state.displayGraphWithoutParents().graph.connectionsWithoutParent.toSeq)

          def disconnect(count:Int):Unit = {
            state.eventProcessor.changes.onNext(GraphChanges(delConnections = connections.take(count).toSet))
          }

          div(
            "disconnect: ",
            button("1", onClick --> sideEffect { disconnect(1) }),
            button("10", onClick --> sideEffect {disconnect(10)}),
            button("100", onClick --> sideEffect {disconnect(100)})
          )
        },
        additions
        //        div(
        //          "Random Events:",
        //          br(),
        //          {
        //            import scalajs.js.timers._
        //            def graph = state.rawGraph.now
        //
        //            def randomPostId: Option[PostId] = if (graph.postsById.size > 0) Option((graph.postIds.toIndexedSeq) (rInt(graph.postsById.size))) else None
        //
        //            def randomConnection: Option[Connection] = if (graph.connections.size > 0) Option((graph.connections.toIndexedSeq) (rInt(graph.connections.size))) else None
        //
        //            def randomContainment: Option[Containment] = if (graph.containments.size > 0) Option((graph.containments.toIndexedSeq) (rInt(graph.containments.size))) else None
        //
        //            val numberOfConcurrentEvents = 3
        //            val events: Array[() => Option[GraphChanges]] = {
        //              val distribution: List[(Int, () => Option[GraphChanges])] = (
        //                (4, () => Option(GraphChanges(addPosts = Set(Post.newId(rStr(1 + rInt(20))))))) ::
        //                  (3, () => randomPostId.map(p => GraphChanges(updatePosts = Set(Post(p, rStr(1 + rInt(20))))))) ::
        //                  (2, () => randomPostId.map(p => GraphChanges(delPosts = Set(p)))) ::
        //                  (3, () => for (p1 <- randomPostId; p2 <- randomPostId) yield GraphChanges(addConnections = Set(Connection(p1, p2)))) ::
        //                  (2, () => randomConnection.map(c => GraphChanges(delConnections = Set(c)))) ::
        //                  (3, () => for (p1 <- randomPostId; p2 <- randomPostId) yield GraphChanges(addContainments = Set(Containment(p1, p2)))) ::
        //                  (2, () => randomContainment.map(c => GraphChanges(delContainments = Set(c)))) ::
        //                  Nil
        //                )
        //              distribution.flatMap { case (count, f) => List.fill(count)(f) }(breakOut)
        //            }
        //
        //            def randomEvent = events(rInt(events.size))()
        //
        //            def emitRandomEvent() {
        //              val changes = List.fill(numberOfConcurrentEvents)(randomEvent)
        //                .flatten
        //                .foldLeft(GraphChanges.empty)(_ merge _)
        //              state.persistence.addChanges(changes)
        //            }
        //
        //            var interval: Option[SetIntervalHandle] = None
        //            val intervals = (
        //              5.seconds ::
        //                2.seconds ::
        //                1.seconds ::
        //                0.5.seconds ::
        //                0.1.seconds ::
        //                Duration.Inf ::
        //                Nil
        //              )
        //            val prefix = "DevViewRandomEventTimer"
        //            for (i <- intervals) yield {
        //              val iid = s"$prefix$i"
        //              i match {
        //                case i: FiniteDuration =>
        //                  span(radio(name := prefix, id := iid), labelfor(iid)(s"${i.toMillis / 1000.0}s"), onclick := { () =>
        //                    interval.foreach(clearInterval)
        //                    interval = Option(setInterval(i)(emitRandomEvent))
        //                  })
        //                case _ =>
        //                  span(radio(name := prefix, id := iid, checked), labelfor(iid)(s"none"), onclick := { () =>
        //                    interval.foreach(clearInterval)
        //                    interval = None
        //                  })
        //              }
        //            }
        //          }
        //        ) // ,Rx {
        //   state.rawGraph().toSummaryString
        // },
//        pre(maxWidth := "400px", maxHeight := "300px", overflow.scroll, fontSize := "11px", Rx {
//          apiEvents().mkString("\n")
//          // pre(apiEvents().mkString("\n")).render
//        }), button("clear", onClick --> sideEffect {
//          apiEvents() = Nil
//        })
      ),
//      Rx {
//        (state.jsError() match {
//          case Some(error) =>
//            pre(
//              position.fixed, right := 0, bottom := 50,
//              border := "1px solid #FFD7D7", backgroundColor := "#FFF0F0", color := "#C41A16",
//              width := "90%", margin := 10, padding := 10, whiteSpace := "pre-wrap",
//              error
//            )
//          case None => span()
//        }).render
//      }

    )
  }
}
