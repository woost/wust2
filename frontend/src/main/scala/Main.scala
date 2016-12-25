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
import collection.breakOut

@JSExport
object Main extends js.JSApp {
  // s"ws://${window.location.host}"
  Client.run("ws://localhost:8080")
  Client.login(api.PasswordAuth("hans", "***"))

  Client.wireApi.getGraph().call().foreach { graph =>
    AppCircuit.dispatch(SetGraph(graph))
  }

  val MainView = ReactComponentB[ModelProxy[RootModel]]("MainView")
    .render_P { proxy =>
      val graph = proxy.value.graph
      <.div(
        <.button("add post", ^.onClick --> Callback {
          Client.wireApi.addPost("Posti").call()
        }),
        <.button("connect something", ^.onClick --> Callback {
          import scala.util.Random.nextInt
          val posts: IndexedSeq[Post] = graph.posts.values.toIndexedSeq
          val n = posts.size
          val source = posts(nextInt(n))
          val target = (posts diff List(source))(nextInt(n - 1))
          Client.wireApi.connect(source.id, target.id).call()
        }),
        <.button(^.onClick --> Callback { Client.logout() }, "logout"),
        GraphView(graph)
      )
    }
    .build

  @JSExport
  def main(): Unit = {
    val mainView = AppCircuit.connect(m => m)
    ReactDOM.render(mainView(m => MainView(m)), document.getElementById("container"))
  }
}

case class RootModel(graph: Graph = Graph.empty)

object AppCircuit extends Circuit[RootModel] with ReactConnector[RootModel] {
  def initialModel = RootModel()

  val graphHandler = new ActionHandler(zoomRW(_.graph)((m, v) => m.copy(graph = v))) {
    override def handle = {
      case SetGraph(graph) => updated(graph)
      case AddPost(post) =>
        updated(value.copy(
          posts = value.posts + (post.id -> post)
        ))
      case AddRespondsTo(respondsTo) =>
        updated(value.copy(
          respondsTos = value.respondsTos + (respondsTo.id -> respondsTo)
        ))
    }
  }
  override val actionHandler = composeHandlers(graphHandler)
}
