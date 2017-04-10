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

object Main extends js.JSApp {

  def main(): Unit = {
    implicit val ctx: Ctx.Owner = Ctx.Owner.safe()

    import window.location
    val protocol = if (location.protocol == "https:") "wss" else "ws"
    val port = if (location.port == "12345") "8080" else location.port

    val state = new GlobalState

    Client.run(s"$protocol://${location.hostname}:$port/ws") // ist doch wie vorher, oder? ja aber wenn ich dioe seite lade, passiert erstmal gar nix. kein request geht raus. graph leer. wenn ic mich einogge, werden alle requests gefeuert.

    Client.auth.onEvent(state.onAuthEvent)
    Client.onEvent(state.onApiEvent)
    Client.subscribe(Channel.Graph)
    Client.auth.reauthenticate()
    Client.onConnect { loc =>
      //TODO reauth and get graph again and sub
    }

    Client.api.getGraph().call().foreach { newGraph =>
      state.rawGraph() = newGraph
    }

    document.getElementById("container").appendChild(
      views.MainView(state).render
    )
  }
}
