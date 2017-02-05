package frontend

import org.scalajs.dom._
import org.scalajs.dom.ext.KeyCode

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import autowire._
import boopickle.Default._
import scalajs.concurrent.JSExecutionContext.Implicits.queue

import graph._
import org.scalajs.d3v4
import Color._

object AddPostForm {
  case class Props(graph: Graph, target: Option[AtomId])

  type State = String
  type Scope = BackendScope[Props, State]

  val field = Ref[raw.HTMLElement]("field")
  class Backend(val $: Scope) {
    def submit() = {
      ($.state >>= {
        case text if text.trim.nonEmpty =>
          $.props >>= {
            case Props(_, target) =>
              Callback {
                target match {
                  case Some(postId) => Client.api.respond(postId, text).call()
                  case None => Client.api.addPost(text).call()
                }
              }
          }
        case _ => Callback.empty
      }) >> $.setState("")
    }
    def render(p: Props) = {
      import p.graph
      <.div(
        p.target match {
          case Some(postId) =>
            val post = graph.posts(postId)
            <.div(
              views.parents((graph.incidentParentContains(post.id).toSeq, graph)),
              views.post(post)
            )
          case None => <.div("New Post:")
        },
        <.input(^.`type` := "text", ^.ref := "field", ^.value := $.state.runNow(),
          ^.onChange ==> ((e: ReactEventI) => $.setState(e.target.value)),
          ^.onKeyUp ==> ((e: ReactKeyboardEventI) => if (e.keyCode == KeyCode.Enter) submit() else Callback.empty)),
        <.button(if (p.target.isDefined) "respond" else "create", ^.onClick --> submit())
      )
    }
  }

  protected val component = ReactComponentB[Props]("AddPostForm")
    .initialState("")
    .renderBackend[Backend]
    .componentDidMount(c => field(c.backend.$).tryFocus)
    .componentDidUpdate(c => field(c.$).tryFocus)
    .build

  def apply(graph: Graph, target: Option[AtomId]) = component(Props(graph, target))
}
