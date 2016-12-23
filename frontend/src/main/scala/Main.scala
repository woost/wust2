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

import api.graph._
import pharg._

@JSExport
object Main extends js.JSApp {
  // s"ws://${window.location.host}"
  Client.run("ws://localhost:8080")

  val MainView = ReactComponentB[ModelProxy[RootModel]]("MainView")
    .render_P { proxy =>
      <.div(
        proxy.value.counter,
        <.br(),
        <.button(^.onClick --> Callback.future {
          Client.wireApi.change(1).call().map { newValue =>
            proxy.dispatchCB(SetCounter(newValue))
          }
        }, "inc websocket"),
        proxy.value.graph.toString,
        <.button(^.onClick --> Callback {
          Client.wireApi.addPost("Posti").call()
        }, "add post"),
        <.button(^.onClick --> Callback {
          val ids = proxy.value.graph.vertices.toSeq.sortBy(-_).take(2)
          if( ids.size == 2) {
            Client.wireApi.connect(ids(1), ids(0)).call()
          }
        }, "connect something")
      )
    }
    .build

  @JSExport
  def main(): Unit = {
    val mainView = AppCircuit.connect(m => m)
    ReactDOM.render(mainView(m => MainView(m)), document.getElementById("container"))
  }
}

case class RootModel(counter: Int = 0, graph: Graph = DirectedGraphData[Id, Post, Connects](Set.empty, Set.empty, Map.empty, Map.empty))

object AppCircuit extends Circuit[RootModel] with ReactConnector[RootModel] {
  def initialModel = RootModel()

  val counterHandler = new ActionHandler(zoomRW(_.counter)((m, v) => m.copy(counter = v))) {
    override def handle = {
      case SetCounter(newValue) => updated(newValue)
    }
  }

  val graphHandler = new ActionHandler(zoomRW(_.graph)((m, v) => m.copy(graph = v))) {
    override def handle = {
      case AddPost(id, post) =>
        updated(value.copy(
          vertices = value.vertices + id,
          vertexData = value.vertexData + (id -> post)
        ))
      case AddConnects(edge, connects) =>
        updated(value.copy(
          edges = value.edges + edge,
          edgeData = value.edgeData + (edge -> connects)
        ))
    }
  }
  override val actionHandler = composeHandlers(counterHandler, graphHandler)
}
