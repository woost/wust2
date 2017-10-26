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
  def postItem(state: GlobalState, post: Post) = {
    for {
      graphSelectionHandler <- (state.graphSelection: Handler[GraphSelection])
      div <- div(
        Style("minHeight", "12px"),
        Style("border", "solid 1px"),
        Style("cursor", "pointer"),
        onClick(GraphSelection.Union(Set(post.id))) --> graphSelectionHandler,
        post.title
      )
    } yield div
  }

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(
      Style("padding", "20px"),
      children <--
        state.displayGraphWithoutParents.toObservable.map { dg =>
          val graph = dg.graph
          val sortedPosts = HierarchicalTopologicalSort(graph.postIds, successors = graph.successors, children = graph.children)

          sortedPosts.map { postId =>
            val post = graph.postsById(postId)
            postItem(state, post)
          }
        }
    ).render
  }
}
