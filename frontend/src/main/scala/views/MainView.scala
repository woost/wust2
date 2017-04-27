package wust.frontend.views

import org.scalajs.dom._
import rx._
import rxext._
import wust.frontend.Color._
import wust.frontend.views.graphview.GraphView
import wust.frontend.{DevOnly, GlobalState}
import wust.ids._
import wust.graph._

import scalatags.JsDom.all._
import scalatags.rx.all._

//TODO: let scalatagst-rx accept Rx(div()) instead of only Rx{(..).render}
object MainView {
  def apply(state: GlobalState, disableSimulation: Boolean = false)(implicit ctx: Ctx.Owner) = {
    val router = new ViewPageRouter(state.viewPage)

    div(fontFamily := "sans-serif")(
      div(
        button(onclick := { (_: Event) => state.viewPage() = ViewPage.Graph })("graph"),
        button(onclick := { (_: Event) => state.viewPage() = ViewPage.Tree })("tree"),
        button(onclick := { (_: Event) => state.viewPage() = ViewPage.User })("user"),

        //TODO: make scalatagst-rx accept Rx[Option[T]], then getOrElse can be dropped
        span(state.currentUser.map(_.filterNot(_.isImplicit).map { user =>
          span(s"user: ${user.name}", UserView.logoutButton)
        }.getOrElse(span()).render)),

        div(
          float.right,
          input(placeholder := "your email"),
          button("get notified when we launch")
        ),

        // TODO: make scalatags-rx accept primitive datatypes as strings
        // span(" selected group: ", state.selectedGroup.map(_.toString))
        Rx {
          val selection = state.graphSelection()
          val graph = state.rawGraph()

          val focusedParents: Set[PostId] = selection match {
            case GraphSelection.Root => Set.empty
            case GraphSelection.Union(parentIds) => parentIds
          }

          div(
            width := "10em",
            focusedParents.toSeq.map { parentId =>
              val post = graph.postsById(parentId)
              Views.post(post)(
                backgroundColor := baseColor(post.id).toString,
                span(
                  "×",
                  padding := "0 0 0 3px",
                  cursor.pointer,
                  onclick := { () => state.graphSelection.updatef { _ remove parentId } }
                )
              )
            }
          ).render
        }
      ),

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
