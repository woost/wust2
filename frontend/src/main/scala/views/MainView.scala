package wust.frontend.views

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom._

import rx._
import scalatags.rx.all._
import scalatags.JsDom.all._
import autowire._
import boopickle.Default._

import wust.frontend.{GlobalState, Client, DevOnly, ViewPage}
import wust.graph._
import graphview.GraphView

//TODO: let scalatagst-rx accept Rx(div()) instead of only Rx{(..).render}
object MainView {
  def apply(state: GlobalState, disableSimulation: Boolean = false)(implicit ctx: Ctx.Owner) = {

    def toggleDisplay(f: ViewPage => Boolean)(implicit ctx: Ctx.Owner): Rx[String] =
      state.viewPage.map(m => if (f(m)) "block" else "none")
    val graphDisplay = toggleDisplay(_ == ViewPage.Graph)
    val treeDisplay = toggleDisplay(_ == ViewPage.Tree)
    val userDisplay = toggleDisplay(_ == ViewPage.User)
    val postFormDisplay = Rx { toggleDisplay(m => (m == ViewPage.Graph || m == ViewPage.Tree) && state.currentUser().isDefined) }

    div(fontFamily := "sans-serif")(
      button(onclick := { (_: Event) => state.viewPage() = ViewPage.Graph })("graph"),
      button(onclick := { (_: Event) => state.viewPage() = ViewPage.Tree })("tree"),
      button(onclick := { (_: Event) => state.viewPage() = ViewPage.User })("user"),
      span(" user: ", state.currentUser.map(_.map(_.name).getOrElse(""))), //TODO: make scalatagst-rx accept Rx[Option[T]]
      div(display := graphDisplay)(GraphView(state, disableSimulation)),
      div(display := treeDisplay)(TreeView(state)),
      div(display := userDisplay)(UserView(state)),
      div(position.fixed, width := "100%", bottom := 0, left := 0, display := postFormDisplay,
        padding := "5px", background := "#f7f7f7", borderTop := "1px solid #DDD")(
          AddPostForm(state)
        ),
      DevOnly { DevView(state) }
    )
  }
}
