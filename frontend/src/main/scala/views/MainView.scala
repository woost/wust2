package frontend.views

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom._

import rx._
import scalatags.rx.all._
import scalatags.JsDom.all._
import autowire._
import boopickle.Default._

import frontend.{GlobalState, Client}
import graph._
import graphview.GraphView

object MainView {
  sealed trait Tab
  object Tab {
    case object Graph extends Tab
    case object List extends Tab
  }

  private val tabMode: Var[Tab] = Var(Tab.Graph)
  val graphDisplay = tabMode.map(m => if (m == Tab.Graph) "block" else "none")
  val listDisplay = tabMode.map(m => if (m == Tab.List) "block" else "none")

  def component(state: GlobalState)(implicit ctx: Ctx.Owner) =
    div(fontFamily := "sans-serif")(
      button(onclick := { (_: Event) => Client.auth.register("hans", "***").call() })("register"),
      button(onclick := { (_: Event) => Client.login(api.PasswordAuth("hans", "***")) })("login"),
      button(onclick := { (_: Event) => Client.logout(); () })("logout"),
      button(onclick := { (_: Event) => tabMode() = Tab.Graph })("graph"),
      button(onclick := { (_: Event) => tabMode() = Tab.List })("list"),
      div(display := graphDisplay)(
        GraphView.component(state)
      ),
      div(display := listDisplay)(
        ListView.component(state)
      ),
      div(position.fixed, width := "100%", bottom := 0, left := 0,
        padding := "5px", background := "#f7f7f7", borderTop := "1px solid #DDD")(
          "AddPostForm"
        )
    )
}
