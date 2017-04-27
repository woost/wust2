package wust.frontend.views

import autowire._
import boopickle.Default._
import rx._
import wust.frontend.{Client, GlobalState}
import wust.ids._
import wust.graph._
import wust.frontend.LoggedOut

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalatags.JsDom.all._

object DevView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    Rx {
      import scala.util.Random.{nextInt => rInt, nextString => rStr}
      div(
        div(
          position.fixed, right := 0, top := 50, display.flex, flexDirection.column,
          {
            val users = List("a", "b", "c", "d", "e", "f", "h")
            div(
              "login: ",
              users.map(u => button(u, onclick := { () =>
                Client.auth.register(u, u).filter(_ == false).foreach { _ =>
                  Client.auth.logout().foreach { _ =>
                    state.onAuthEvent(LoggedOut) //TODO: this is necessary, since logging out an implicit user does not send a logout event
                    Client.auth.login(u, u)
                  }
                }
              }))
            )
          },
          {
            def addRandomPost() { Client.api.addPost(rStr(1 + rInt(20)), state.graphSelection(), state.selectedGroupId()).call() }
            div(
              button("create random post", onclick := { () => addRandomPost() }),
              button("10", onclick := { () => for (_ <- 0 until 10) addRandomPost() }),
              button("100", onclick := { () => for (_ <- 0 until 100) addRandomPost() })
            )
          },
          {
            val posts = scala.util.Random.shuffle(state.displayGraph().graph.postsById.keys.toSeq)
            def deletePost(id: PostId) { Client.api.deletePost(id).call() }
            div(
              button("delete random post", onclick := { () => posts.take(1) foreach deletePost }),
              button("10", onclick := { () => posts.take(10) foreach deletePost }),
              button("100", onclick := { () => posts.take(100) foreach deletePost })
            )
          }
        ),
        state.jsError() match {
          case Some(error) =>
            pre(
              position.fixed, right := 0, bottom := 50,
              border := "1px solid #FFD7D7", backgroundColor := "#FFF0F0", color := "#C41A16",
              width := "90%", margin := 10, padding := 10, whiteSpace := "pre-wrap",
              error
            )
          case None => span()
        }

      ).render
    }
  }
}
