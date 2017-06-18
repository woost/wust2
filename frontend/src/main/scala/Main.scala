package wust.frontend

import autowire._
import boopickle.Default._
import org.scalajs.dom._
import rx._
import rxext._
import wust.util.EventTracker.sendEvent
import wust.ids._
import wust.graph.{ GraphSelection, Graph }
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

object Main extends js.JSApp {

  def main(): Unit = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    import window.location
    val protocol = if (location.protocol == "https:") "wss" else "ws"
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

        // take changes into account, when we get a new graph
        state.persistence.applyChangesToState(newNonEmptyGraph)
        if (selection == GraphSelection.Root) {
          // on the frontpage all posts are collapsed per default
          state.collapsedPostIds.updatef(_ ++ newGraph.postsById.keySet.filter(p => newGraph.hasChildren(p) && !newGraph.hasParents(p)))
        }
      }
    }

    Client.onEvents(state.onEvents)

    // The first thing to be sent should be the auth-token
    ClientCache.storedToken.foreach { token =>
      Client.auth.loginToken(token).call()
    }

    Client.onConnect { (location, isReconnect) =>
      println(s"Connected to server: $location")

      if (isReconnect) {
        ClientCache.currentAuth.foreach { auth =>
          Client.auth.loginToken(auth.token).call()
        }

        getNewGraph(state.rawGraphSelection.now)
      }
    }

    Client.run(s"$protocol://${location.hostname}:$port/ws")

    state.viewConfig.foreach { viewConfig =>
      viewConfig.invite match {
        case Some(token) =>
          Client.api.acceptGroupInvite(token).call().onComplete {
            case Success(Some(_)) =>
              getNewGraph(viewConfig.selection)
              sendEvent("group", "invitelink", "success")
            case failedResult =>
              println(s"Failed to accept group invite: $failedResult")
              sendEvent("group", "invitelink", "failure")
          }

        case None =>
          getNewGraph(viewConfig.selection)
      }

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
        state.focusedPostId() = None
        state.postCreatorMenus() = Nil
      }
    }

    DevOnly {
      import state._
      rawGraph.debug(g => s"rawGraph: ${g.toSummaryString}")
      collapsedPostIds.debug("collapsedPostIds")
      currentView.debug("currentView")
      displayGraph.debug { dg => s"displayGraph: ${dg.graph.toSummaryString}" }
      focusedPostId.debug("focusedPostId")
      selectedGroupId.debug("selectedGroupId")
      graphSelection.debug("graphSelection")
      viewConfig.debug("viewConfig")
      currentUser.debug("\ncurrentUser")

      import scala.meta._
      println("scala meta: val x = 2".tokenize.get.syntax)

      window.onerror = { (msg: Event, source: String, line: Int, col: Int) =>
        //TODO: send and log js errors in backend
        state.jsError() = Option(msg.toString)
      }
    }
  }
}
