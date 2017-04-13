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

import wust.frontend.{GlobalState, Client, DevOnly, ViewPage}
import wust.graph._
import graphview.GraphView
import wust.api.UserGroup

class ViewPageRouter(page: Rx[ViewPage])(implicit ctx: Ctx.Owner) {
    def toggleDisplay(f: ViewPage => Boolean): Rx[String] =
      page.map(m => if (f(m)) "block" else "none")

    def showOn(predicate: ViewPage => Boolean)(elem: TypedTag[Element]) =
      elem(display := toggleDisplay(predicate))

    def map(mappings: Seq[(ViewPage, TypedTag[Element])]) =
      div(mappings.map { case (page, elem) => showOn(_ == page)(elem) }: _*)
}

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
        ).render
      }.getOrElse(span.render))),

      span(" groups: ", state.currentGroups.map { gs =>
        select { //TODO: use public groupid constant from config
          val groupsIdsWithNames = (1L, "public") +: gs.map(g => (g.id, g.users.map(_.name).mkString(", ")))
          groupsIdsWithNames.map {
            case (groupId, name) => option(name, value := groupId)
          }
        }(
          onchange := { (e: Event) =>
            val groupId = e.target.asInstanceOf[HTMLSelectElement].value.toLong
            state.selectedGroup() = groupId
            //TODO: where to automatically request new graph on group change? Globalstate?
            Client.api.getGraph(groupId).call().foreach { newGraph => state.rawGraph() = newGraph }
          }
        ).render
      }),

      // TODO: make scalatags-rx accept primitive datatypes as strings
      span(" selected group: ", state.selectedGroup.map(_.toString)),

      router.map (
        ViewPage.Graph -> GraphView(state, disableSimulation) ::
        ViewPage.Tree -> TreeView(state) ::
        ViewPage.User -> UserView(state) ::
        Nil
      ),

      router.showOn(_.isEditable)(
        div(position.fixed, width := "100%", bottom := 0, left := 0,
          padding := "5px", background := "#f7f7f7", borderTop := "1px solid #DDD")(
            AddPostForm(state)
          )
      ),

      DevOnly { DevView(state) }
    )
  }
}
