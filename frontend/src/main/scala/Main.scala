package wust.frontend

import scalajs.js
import scalajs.js.annotation.JSExport
import concurrent.Future
import collection.breakOut
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import autowire._

import org.scalajs.dom._
import boopickle.Default._

import wust.api._
import wust.graph._
import wust.util.Pipe

import scalatags.JsDom.all._
import scalatags.rx.all._
import rx._

@JSExport
object Main extends js.JSApp {
  @JSExport
  def main(): Unit = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    import window.location
    val protocol = if (location.protocol == "https:") "wss" else "ws"
    val port = if (location.port == "12345") "8080" else location.port

    val state = new GlobalState

    Client.listen(state.onApiEvent)
    Client.auth.listen(state.onAuthEvent)

    Client.run(s"$protocol://${location.hostname}:$port/ws")
    Client.subscribe(Channel.Graph)

    DevOnly {
      Client.auth.login("hans", "***")
    }

    Client.api.getGraph().call().foreach { graph =>
      state.graph := graph
    }

    document.getElementById("container").appendChild(
      views.MainView(state).render
    )
  }
}
