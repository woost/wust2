package wust.frontend

import scalajs.js
import scalajs.js.annotation.JSExport
import concurrent.Future
import collection.breakOut
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import autowire._
import boopickle.Default._

import org.scalajs.dom._

import wust.api._
import wust.graph._
import wust.util.Pipe

import scalatags.JsDom.all._
import scalatags.rx.all._
import rx._, rxext._

object Main extends js.JSApp {

  def main(): Unit = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    import window.location
    val protocol = if (location.protocol == "https:") "wss" else "ws"
    val port = if (location.port == "12345") "8080" else location.port

    val state = new GlobalState

    Client.run(s"$protocol://${location.hostname}:$port/ws")

    Client.auth.onEvent(state.onAuthEvent)
    Client.onEvent(state.onApiEvent)

    Client.onConnect { loc =>
      Client.subscribe(Channel.Graph)
      Client.auth.reauthenticate().foreach { success =>
        if (!success)
          //TODO: public group id from config
          Client.api.getGraph(1).call().foreach { newGraph =>
            state.rawGraph() = newGraph
          }
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

      import scala.meta._
      println("val x = 2".tokenize.get.syntax)
    }
  }
}
