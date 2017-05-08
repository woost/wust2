package wust.frontend

import autowire._
import boopickle.Default._
import org.scalajs.dom._
import rx._
import rxext._

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

object Main extends js.JSApp {

  def main(): Unit = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    import window.location
    val protocol = if (location.protocol == "https:") "wss" else "ws"
    val port = if (location.port == "12345") "8080" else location.port

    val state = new GlobalState

    Client.run(s"$protocol://${location.hostname}:$port/ws")

    Client.onEvent(state.onApiEvent)
    Client.auth.onEvent(state.onAuthEvent)

    Client.onConnect { _ =>
      Client.auth.reauthenticate()
    }

    state.rawGraphSelection.foreach { selection =>
      Client.api.getGraph(selection).call().foreach { newGraph =>
        state.rawGraph() = newGraph
      }
    }

    state.inviteToken.foreach {
      case Some(token) => Client.api.acceptGroupInvite(token).call().foreach {
        _.foreach { groupId =>
          state.selectedGroupId() = Option(groupId)
        }
      }
      case None =>
    }

    document.getElementById("container").appendChild(
      views.MainView(state).render)

    DevOnly {
      import state._
      rawGraph.debug(g => s"rawGraph: ${g.toSummaryString}")
      collapsedPostIds.debug("collapsedPostIds")
      currentView.debug("currentView")
      displayGraph.debug { dg => s"displayGraph: ${dg.graph.toSummaryString}" }
      focusedPostId.debug("focusedPostId")
      editedPostId.debug("editedPostId")
      mode.debug("mode")
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
