package frontend

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scala.concurrent.Future
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import autowire._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

import diode._
import diode.react._

import org.scalajs.dom._
import boopickle.Default._

import api._
import graph._
import collection.breakOut

@JSExport
object Main extends js.JSApp {
  @JSExport
  def main(): Unit = {
    Client.run(s"ws://${window.location.host}")
    Client.login(PasswordAuth("hans", "***"))
    Client.subscribe(Channel.Graph)

    Client.wireApi.getGraph().call().foreach { graph =>
      AppCircuit.dispatch(SetGraph(graph))
    }

    val mainView = AppCircuit.connect(m => m)
    ReactDOM.render(mainView(m => MainView(m)), document.getElementById("container"))
  }
}
