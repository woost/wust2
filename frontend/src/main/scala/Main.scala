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
import DiodeEvent._

@JSExport
object Main extends js.JSApp {
  Client // websocket connection

  val MainView = ReactComponentB[ModelProxy[RootModel]]("MainView")
    .render_P { m =>
      <.div(
        m.value.counter,
        <.br(),
        <.button(^.onClick --> Callback.future {
          Client[Api].change(1).call().map { newValue =>
            m.dispatchCB(SetCounter(newValue))
          }
        }, "inc websocket")
      )
    }
    .build

  @JSExport
  def main(): Unit = {
    val mainView = AppCircuit.connect(m => m)
    ReactDOM.render(mainView(m => MainView(m)), document.getElementById("container"))
  }
}

case class RootModel(counter: Int = 0)

object AppCircuit extends Circuit[RootModel] with ReactConnector[RootModel] {
  def initialModel = RootModel()

  val counterHandler = new ActionHandler(zoomRW(_.counter)((m, v) => m.copy(counter = v))) {
    override def handle = {
      case SetCounter(newValue) => updated(newValue)
      case _ => updated(value)
    }
  }
  override val actionHandler = composeHandlers(counterHandler)
}
