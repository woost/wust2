package wust.frontend.views

import org.scalajs.d3v4
import rx._
import rxext._
import wust.frontend._
import wust.ids._
import wust.graph._
import wust.util.Pipe
import wust.util.collection._
import autowire._
import boopickle.Default._
import wust.api._

import org.scalajs.dom.{window, document, console}
import scalatags.JsDom.all._
import scala.scalajs.js
import scalatags.rx.all._

object TestView {

  def postItem(state: GlobalState, post: Post)(implicit ctx: Ctx.Owner) = {
    div(
      minHeight := "12px",
      border := "solid 1px",
      cursor.pointer,
      onclick := { () =>
        state.graphSelection() = GraphSelection.Union(Set(post.id))
      },
      post.title
    )
  }

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = div(
    state.displayGraphWithoutParents.map { dg =>
      val graph = dg.graph
      val sortedPosts = HierarchicalTopologicalSort(graph.postIds, successors = graph.successors, children = graph.children)

      div(
        padding := "20px",
        sortedPosts.map { postId =>
          val post = graph.postsById(postId)
          postItem(state, post)
        }
      ).render
    }
  )
}
