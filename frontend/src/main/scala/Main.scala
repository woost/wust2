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

    Client.onConnect { loc =>
      Client.auth.reauthenticate()
    }

    state.graphSelection.foreach { selection =>
      Client.api.getGraph(selection).call().foreach { newGraph =>
        state.rawGraph() = newGraph
      }
    }

    document.getElementById("container").appendChild(
      views.MainView(state).render
    )

    DevOnly {
      import state._
      rawGraph.debug(v => s"rawGraph: ${v.posts.size} posts, ${v.connections.size} connections, ${v.containments.size} containments")
      collapsedPostIds.debug("collapsedPostIds")
      currentView.debug("currentView")
      displayGraph.debug { dg => import dg.graph; s"graph: ${graph.posts.size} posts, ${graph.connections.size} connections, ${graph.containments.size} containments" }
      focusedPostId.debug("focusedPostId")
      editedPostId.debug("editedPostId")
      mode.debug("mode")
      currentGroups.debug("currentGroups")
      selectedGroup.debug("selectedGroup")
      graphSelection.debug("graphSelection")
      viewConfig.debug("viewConfig")

      import scala.meta._
      println("val x = 2".tokenize.get.syntax)
    }
  }
}
