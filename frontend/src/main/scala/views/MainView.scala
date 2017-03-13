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

object MainView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {

    def toggleDisplay(f: ViewPage => Boolean)(implicit ctx: Ctx.Owner) =
      state.viewPage.map(m => if (f(m)) "block" else "none")
    val graphDisplay = toggleDisplay(_ == ViewPage.Graph)
    val treeDisplay = toggleDisplay(_ == ViewPage.Tree)
    val userDisplay = toggleDisplay(_ == ViewPage.User)
    val postFormDisplay = toggleDisplay(m => m == ViewPage.Graph || m == ViewPage.Tree)

    div(fontFamily := "sans-serif")(
      button(onclick := { (_: Event) => state.viewPage() = ViewPage.Graph })("graph"),
      button(onclick := { (_: Event) => state.viewPage() = ViewPage.Tree })("tree"),
      button(onclick := { (_: Event) => state.viewPage() = ViewPage.User })("user"),
      div(display := graphDisplay)(GraphView(state)),
      div(display := treeDisplay)(TreeView(state)),
      div(display := userDisplay)(UserView(state)),
      div(position.fixed, width := "100%", bottom := 0, left := 0, display := postFormDisplay,
        padding := "5px", background := "#f7f7f7", borderTop := "1px solid #DDD")(
          AddPostForm(state)
        ),
      DevOnly {
        Rx {
          import scala.util.Random.{nextInt => rInt, nextString => rStr}
          div(position.fixed, right := 0, top := 0, display.flex, flexDirection.column,
            {
              def addRandomPost() { Client.api.addPost(rStr(1 + rInt(20))).call() }
              div(
                button("create random post", onclick := { () => addRandomPost() }),
                button("10", onclick := { () => for (_ <- 0 until 10) addRandomPost() }),
                button("100", onclick := { () => for (_ <- 0 until 100) addRandomPost() })
              )
            },
            {
              val posts = scala.util.Random.shuffle(state.graph().posts.keys.toSeq)
              def deletePost(id: PostId) { Client.api.deletePost(id).call() }
              div(
                button("delete random post", onclick := { () => posts.take(1) foreach deletePost }),
                button("10", onclick := { () => posts.take(10) foreach deletePost }),
                button("100", onclick := { () => posts.take(100) foreach deletePost })
              )
            }).render
        }
      }
    )
  }
}
