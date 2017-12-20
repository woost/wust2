package wust.frontend

import autowire._
import boopickle.Default._
import monix.execution.Cancelable
import monix.reactive.OverflowStrategy.Unbounded
import org.scalajs.dom._
import wust.util.Analytics
import wust.api.ApiEvent
import wust.ids._
import wust.graph.{Graph, Page}
import wust.framework._
import org.scalajs.dom.ext.KeyCode
import outwatch.dom._

import monix.execution.Scheduler.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.util.Success
import wust.util.outwatchHelpers._
import rx.Ctx

@js.native
@JSGlobal("wustConfig")
object Config extends js.Object {
  val wsPort: js.UndefOr[Int] = js.native
}

object Main {

  def main(args: Array[String]): Unit = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    import window.location
    val protocol = if (location.protocol == "https:") "wss" else "ws"
    //TODO: proxy with webpack devserver and only configure production port
    val port = Config.wsPort getOrElse location.port.toInt

    val apiEventHandler = Handler.create[Seq[ApiEvent]]().unsafeRunSync()
    val state = new GlobalState(apiEventHandler)

    def getNewGraph(selection: Page) = {
      //TODO ???
      // Client.api.getGraph(selection).call().foreach { newGraph =>
      //   val oldSelectionGraph = selection match {
      //     case GraphSelection.Union(ids) => state.rawGraph.now.filter(ids)
      //     case _                         => Graph.empty
      //   }

      //   //TODO problem with concurrent get graph and create post. for now try to partly recover from current graph.
      //   val newNonEmptyGraph = oldSelectionGraph + newGraph

      //   val newCollapsedPostIds: Set[PostId] = if (selection == GraphSelection.Root && state.viewPage.now == views.ViewPage.Graph) {
      //     // on the frontpage all posts are collapsed per default
      //     state.collapsedPostIds.now ++ newGraph.postsById.keySet.filter(p => newGraph.hasChildren(p) && !newGraph.hasParents(p))
      //   } else Set.empty

      //   Var.set(
      //     VarTuple(state.collapsedPostIds, newCollapsedPostIds),
      //     // take changes into account, when we get a new graph
      //     VarTuple(state.rawGraph, newNonEmptyGraph applyChanges state.persistence.currentChanges)
      //   )
      // }
    }

    // The first thing to be sent should be the auth-token
    ClientCache.storedToken.foreach { token =>
      Client.auth.loginToken(token).call()
    }

    {
      val observable = Observable.create[Seq[ApiEvent]](Unbounded) { observer =>
        Client.run(s"$protocol://${location.hostname}:$port/ws", new ApiIncidentHandler {
          override def onConnect(isReconnect: Boolean): Unit = {
            println(s"Connected to websocket")

            if (isReconnect) {
              ClientCache.currentAuth.foreach { auth =>
                Client.auth.loginToken(auth.token).call()
              }

              //TODO
              // getNewGraph(state.rawGraphSelection.now)
            }
          }

          override def onEvents(events: Seq[ApiEvent]): Unit = observer.onNext(events)//state.onEvents(events)
        })
        Cancelable()
      }

      (apiEventHandler <-- observable).unsafeRunSync()
    }

    state.viewConfig.scan((views.ViewConfig.default, views.ViewConfig.default))((p, c) => (p._2, c)).foreach {
      case (prevViewConfig, viewConfig) =>
        viewConfig.invite foreach { token =>
          Client.api.acceptGroupInvite(token).call().onComplete {
            case Success(Some(_)) =>
              Analytics.sendEvent("group", "invitelink", "success")
            case failedResult =>
              println(s"Failed to accept group invite: $failedResult")
              Analytics.sendEvent("group", "invitelink", "failure")
          }
        }

        if (prevViewConfig.page != viewConfig.page) getNewGraph(viewConfig.page)
    }

    OutWatch.renderInto("#container", views.MainView(state)).unsafeRunSync()

    //TODO: create global keyevent observer (in outwatch?):
    // document.onkeypress = { (e: KeyboardEvent) =>
    //   if (e.keyCode == KeyCode.Escape) {
    //     Var.set(
    //       VarTuple(state.focusedPostId, None),
    //       VarTuple(state.postCreatorMenus, Nil)
    //     )
    //   }
    // }

  }
}
