package wust.frontend

import autowire._
import boopickle.Default._
import org.scalajs.dom._
import rx._
import rxext._
import wust.util.EventTracker.sendEvent

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation._

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

    Client.run(s"$protocol://${location.hostname}:$port/ws")

    Client.onEvent(state.onApiEvent)

    Client.onConnect { location =>
      ClientCache.authToken.foreach { token =>
        Client.auth.loginToken(token).call()
      }
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

      sendEvent("group", "acceptinvite", "collaboration")
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
