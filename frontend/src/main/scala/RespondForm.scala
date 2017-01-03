package frontend

import org.scalajs.dom._
import org.scalajs.dom.ext.KeyCode

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import autowire._
import boopickle.Default._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import graph._

object RespondForm {
  case class Props(graph: Graph, target: AtomId)

  type State = String
  type Scope = BackendScope[Props, State]

  val field = Ref[raw.HTMLElement]("field")
  class Backend(val $: Scope) {
    def render(p: Props) = {
      val post = p.graph.posts(p.target)
      <.div(
        "Responding to",
        <.div(post.title),
        <.input(^.`type` := "text", ^.ref := "field", ^.value := $.state.runNow(),
          ^.onChange ==> ((e: ReactEventI) => $.setState(e.target.value)),
          ^.onKeyUp ==> ((e: ReactKeyboardEventI) => if (e.keyCode == KeyCode.Enter) respond(p) else Callback.empty)),
        <.button("respond", ^.onClick --> respond(p))
      )
    }
    def respond(p: Props) = {
      ($.state >>= { text => Callback { Client.wireApi.respond(p.target, text).call() } }) >> $.setState("")
    }
  }

  protected val component = ReactComponentB[Props]("RespondForm")
    .initialState("")
    .renderBackend[Backend]
    .componentDidMount(c => field(c.backend.$).tryFocus)
    .componentDidUpdate(c => if (c.prevProps.target != c.currentProps.target) field(c.$).tryFocus else Callback.empty)
    .build

  def apply(graph: Graph, target: AtomId) = component(Props(graph, target))
}
