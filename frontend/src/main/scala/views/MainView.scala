package wust.frontend.views

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom._
import org.scalajs.dom.raw.HTMLSelectElement

import rx._
import scalatags.rx.all._
import scalatags.JsDom.TypedTag
import scalatags.JsDom.all._
import autowire._
import boopickle.Default._

import wust.frontend.{GlobalState, Client, DevOnly}
import wust.graph._
import graphview.GraphView
import wust.api.UserGroup

//TODO: let scalatagst-rx accept Rx(div()) instead of only Rx{(..).render}
object MainView {
  def apply(state: GlobalState, disableSimulation: Boolean = false)(implicit ctx: Ctx.Owner) = {
    val router = new ViewPageRouter(state.viewPage)

    div(fontFamily := "sans-serif")(
      button(onclick := { (_: Event) => state.viewPage() = ViewPage.Graph })("graph"),
      button(onclick := { (_: Event) => state.viewPage() = ViewPage.Tree })("tree"),
      button(onclick := { (_: Event) => state.viewPage() = ViewPage.User })("user"),

      //TODO: make scalatagst-rx accept Rx[Option[T]], then getOrElse can be dropped
      span(state.currentUser.map(_.map { user =>
        span(
          s"user: ${user.name}",
          UserView.logoutButton(state.currentUser)
        )
      }.getOrElse(span()).render)),

      // TODO: make scalatags-rx accept primitive datatypes as strings
      span(" selected group: ", state.selectedGroup.map(_.toString)),

      router.map(
        ViewPage.Graph -> GraphView(state, disableSimulation) ::
          ViewPage.Tree -> TreeView(state) ::
          ViewPage.User -> UserView(state) ::
          Nil
      ),

      router.showOn(ViewPage.Graph, ViewPage.Tree)(
        div(position.fixed, width := "100%", bottom := 0, left := 0,
          padding := "5px", background := "#f7f7f7", borderTop := "1px solid #DDD")(
            AddPostForm(state)
          )
      ),

      DevOnly { DevView(state) }
    )
  }
}
