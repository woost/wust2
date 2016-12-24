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
      val graph = proxy.value.graph
      <.div(
        <.button("inc websocket", ^.onClick --> Callback.future {
          Client.wireApi.change(1).call().map { newValue =>
            proxy.dispatchCB(SetCounter(newValue))
          }
        }),
        <.button("add post", ^.onClick --> Callback {
          Client.wireApi.addPost("Posti").call()
        }),
        <.button("connect something", ^.onClick --> Callback {
          import scala.util.Random.nextInt
          val n = graph.vertices.size
          val source = graph.vertices.toIndexedSeq(nextInt(n))
          val target = (graph.vertices - source).toIndexedSeq(nextInt(n - 1))
          Client.wireApi.connect(source, target).call()
        }),
        <.br(),
        proxy.value.counter,
        GraphView(proxy.value.graph)
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
