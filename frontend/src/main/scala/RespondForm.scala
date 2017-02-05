package frontend

import org.scalajs.dom._
import org.scalajs.dom.ext.KeyCode

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import autowire._
import boopickle.Default._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import graph._
import Color._

object RespondForm {
  case class Props(graph: Graph, target: AtomId)

  type State = String
  type Scope = BackendScope[Props, State]

  val field = Ref[raw.HTMLElement]("field")
  class Backend(val $: Scope) {
    def render(p: Props) = {
      import p.graph
      val post = graph.posts(p.target)
      <.div(
        "Responding to",
        <.div(
          post.title,
          //TODO: share style with graphview
          ^.padding := "3px 5px",
          ^.margin := "3px",
          ^.borderRadius := "3px",
          ^.maxWidth := "100px",
          ^.border := "1px solid #444",
          ^.backgroundColor := postDefaultColor.toString
        ),
        <.div(
          ^.display := "flex",
          graph.incidentParentContains(post.id).map { containsId =>

            val contains = graph.containments(containsId)
            val parent = graph.posts(contains.parentId)
            <.div(
              parent.title,
              <.span(
                " \u00D7", // times symbol
                ^.padding := "0 0 0 3px",
                ^.cursor := "pointer",
                ^.onClick ==> ((e: ReactEventI) => Callback { Client.api.deleteContainment(contains.id).call() })
              ),
              ^.backgroundColor := baseColor(parent.id).toString,
              //TODO: share style with graphview
              ^.padding := "3px 5px",
              ^.margin := "3px",
              ^.borderRadius := "3px",
              ^.maxWidth := "100px",
              ^.border := "1px solid #444"
            )
          }
        ),
        <.input(^.`type` := "text", ^.ref := "field", ^.value := $.state.runNow(),
          ^.onChange ==> ((e: ReactEventI) => $.setState(e.target.value)),
          ^.onKeyUp ==> ((e: ReactKeyboardEventI) => if (e.keyCode == KeyCode.Enter) respond(p) else Callback.empty)),
        <.button("respond", ^.onClick --> respond(p))
      )
    }
    def respond(p: Props) = {
      ($.state >>= { text => Callback { Client.api.respond(p.target, text).call() } }) >> $.setState("")
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
