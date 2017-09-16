package wust.frontend.views

import rx._
import rxext._
import wust.frontend._
import wust.graph._

import org.scalajs.dom.{window, document, console}
import scala.scalajs.js

import outwatch.dom._

import wust.util.outwatchHelpers._

object TestView {
  def postItem(state: GlobalState, post: Post)(implicit ctx: Ctx.Owner) = {
    div(
      // minHeight := "12px",
      // border := "solid 1px",
      // cursor.pointer,
      click(GraphSelection.Union(Set(post.id))) --> state.graphSelection,
      post.title
    )
  }

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(
      // padding := "20px",
      children <--
      state.displayGraphWithoutParents.toObservable.map { dg =>
        val graph = dg.graph
        val sortedPosts = HierarchicalTopologicalSort(graph.postIds, successors = graph.successors, children = graph.children)

        sortedPosts.map { postId =>
          val post = graph.postsById(postId)
          postItem(state, post)
        }
      }).render
  }
}
