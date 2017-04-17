package wust.frontend.views

import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom._

import rx._
import scalatags.rx.all._
import scalatags.JsDom.all._
import autowire._
import boopickle.Default._

import wust.frontend.{GlobalState, Client, DevOnly}
import wust.graph._
import graphview.GraphView

object DevView {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    Rx {
      import scala.util.Random.{nextInt => rInt, nextString => rStr}
      div(
        position.fixed, right := 0, top := 0, display.flex, flexDirection.column,
        {
          val users = List("a", "b", "c", "d")
          div(
            "login: ",
            //button("delete random post", onclick := { () => posts.take(1) foreach deletePost }),
            users.map(u => button(u, onclick := { () => Client.auth.register(u, u).filter(_ == false).foreach { _ => Client.auth.login(u, u) } }))
          )
        },
        {
          def addRandomPost() { Client.api.addPost(rStr(1 + rInt(20)), state.selectedGroup()).call() }
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
      ).render
    }
  }
}
