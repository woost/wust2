package frontend

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scala.concurrent.Future
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import autowire._

import org.scalajs.dom._
import boopickle.Default._

import api.{User, Channel}
import graph._
import collection.breakOut

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
    Client.run(s"$protocol://${location.hostname}:$port/ws")
    Client.subscribe(Channel.Graph)
    Client.login(api.PasswordAuth("hans", "***")).foreach { success =>
      val newUser = if (success) Some(User(0, "hans")) else None
      state.currentUser := newUser //TODO should get user from backend
    }

    Client.api.getGraph().call().foreach { graph =>
      state.graph := graph
    }

    document.getElementById("container").appendChild(
      views.MainView(state).render
    )
  }
}
