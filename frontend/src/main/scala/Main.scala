package wust.frontend

import autowire._
import boopickle.Default._
import org.scalajs.dom._
import rx._
import rxext._
import wust.util.EventTracker.sendEvent
import wust.api.ApiEvent
import wust.ids._
import wust.graph.{ GraphSelection, Graph }
import wust.framework._
import org.scalajs.dom.ext.KeyCode

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation._
import scala.util.Success

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
    val port = Config.wsPort getOrElse location.port

    val state = new GlobalState

    def getNewGraph(selection: GraphSelection) = {
      Client.api.getGraph(selection).call().foreach { newGraph =>
        val oldSelectionGraph = selection match {
          case GraphSelection.Union(ids) => state.rawGraph.now.filter(ids)
          case _                         => Graph.empty
        }

        //TODO problem with concurrent get graph and create post. for now try to partly recover from current graph.
        val newNonEmptyGraph = oldSelectionGraph + newGraph

        val newCollapsedPostIds: Set[PostId] = if (selection == GraphSelection.Root && state.viewPage.now == views.ViewPage.Graph) {
          // on the frontpage all posts are collapsed per default
          state.collapsedPostIds.now ++ newGraph.postsById.keySet.filter(p => newGraph.hasChildren(p) && !newGraph.hasParents(p))
        } else Set.empty

        Var.set(
          VarTuple(state.collapsedPostIds, newCollapsedPostIds),
          // take changes into account, when we get a new graph
          VarTuple(state.rawGraph, newNonEmptyGraph applyChanges state.persistence.currentChanges)
        )
      }
    }

    // The first thing to be sent should be the auth-token
    ClientCache.storedToken.foreach { token =>
      Client.auth.loginToken(token).call()
    }

    Client.run(s"$protocol://${location.hostname}:$port/ws", new ApiIncidentHandler {
      override def onConnect(isReconnect: Boolean): Unit = {
        println(s"Connected to websocket")

        if (isReconnect) {
          ClientCache.currentAuth.foreach { auth =>
            Client.auth.loginToken(auth.token).call()
          }

          getNewGraph(state.rawGraphSelection.now)
        }
      }

      override def onEvents(events: Seq[ApiEvent]): Unit = state.onEvents(events)
    })

    //TODO: better?
    var prevViewConfig: Option[views.ViewConfig] = None
    state.viewConfig.foreach { viewConfig =>
      viewConfig.invite foreach { token =>
        Client.api.acceptGroupInvite(token).call().onComplete {
          case Success(Some(_)) =>
            sendEvent("group", "invitelink", "success")
          case failedResult =>
            println(s"Failed to accept group invite: $failedResult")
            sendEvent("group", "invitelink", "failure")
        }
      }

      prevViewConfig match {
        case Some(prev) if prev.selection == viewConfig.selection =>
        case _ => getNewGraph(viewConfig.selection)
      }

      prevViewConfig = Some(viewConfig)
    }

    state.syncMode.foreach {
      case SyncMode.Live =>
        state.eventCache.flush()
        state.persistence.flush()
      case _ =>
    }

    document.getElementById("container").appendChild(
      views.MainView(state).render
    )

    document.onkeypress = { (e: KeyboardEvent) =>
      if (e.keyCode == KeyCode.Escape) {
        Var.set(
          VarTuple(state.focusedPostId, None),
          VarTuple(state.postCreatorMenus, Nil)
        )
      }
    }

    DevOnly {
      import state._
      rawGraph.debug(g => s"rawGraph: ${g.toSummaryString}")
      collapsedPostIds.debug("collapsedPostIds")
      currentView.debug("currentView")
      displayGraphWithoutParents.debug { dg => s"displayGraph: ${dg.graph.toSummaryString}" }
      focusedPostId.debug("focusedPostId")
      selectedGroupId.debug("selectedGroupId")
      graphSelection.debug("graphSelection")
      viewConfig.debug("viewConfig")
      currentUser.debug("\ncurrentUser")

      window.onerror = { (msg: Event, source: String, line: Int, col: Int) =>
        //TODO: send and log js errors in backend
        state.jsError() = Option(msg.toString)
      }
    }
  }
}
