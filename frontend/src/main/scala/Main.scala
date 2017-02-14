package frontend

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scala.concurrent.Future
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import autowire._

import org.scalajs.dom._
import boopickle.Default._

import api.Channel
import graph._
import collection.breakOut

@JSExport
object Main extends js.JSApp {
  @JSExport
  def main(): Unit = {
    import window.location
    val protocol = if (location.protocol == "https:") "wss" else "ws"
    val port = if (location.port == "12345") "8080" else location.port

    val state = new GlobalState

    Client.listen(state.onApiEvent)
    Client.run(s"$protocol://${location.hostname}:$port/ws")
    Client.subscribe(Channel.Graph)
    Client.login(api.PasswordAuth("hans", "***"))

    Client.api.getGraph().call().foreach { graph =>
      state.graph := graph
    }

    mhtml.mount(document.getElementById("container"), MainView.component(state))
    graphview.GraphView.init(state)
    //TODO: mhtml-onattached
  }
}

// object Helper {
//   implicit class Pipe[T](val v: T) extends AnyVal {
//     def |>[U] (f: T => U) = f(v)
//     def ||>[U](f: T => U): T = {f(v); v}
//     // def #!(str: String = ""): T = {println(str + v); v}
//   }
// }
// import Helper._
