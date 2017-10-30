package wust.frontend.views

import rx._
import rxext._
import wust.frontend._
import wust.graph._

import org.scalajs.dom.{window, document, console}
import scala.scalajs.js

import outwatch.dom._
import wust.util.outwatchHelpers._
import cats.effect.IO

import cats.instances.list._
import cats.syntax.traverse._

object TestView {
  def postItem(post: Post, graphSelectionHandler:Handler[GraphSelection]) = {
    div(
      Style("minHeight", "12px"),
      Style("border", "solid 1px"),
      Style("cursor", "pointer"),
      onClick(GraphSelection.Union(Set(post.id))) --> graphSelectionHandler,
      post.title
    )
  }

  def sortedPostItems(state: GlobalState, graphSelectionHandler:Handler[GraphSelection])(implicit ctx: Ctx.Owner) = state.displayGraphWithoutParents.toObservable.map { dg =>
    val graph = dg.graph
    val sortedPosts = HierarchicalTopologicalSort(graph.postIds, successors = graph.successors, children = graph.children)

    sortedPosts.map { postId =>
      val post = graph.postsById(postId)
      postItem(post, graphSelectionHandler)
    }
  }

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    (for {
      graphSelectionHandler <- state.graphSelection:IO[Handler[GraphSelection]]
    } yield div(
      Style("padding", "20px"),
      children <-- sortedPostItems(state, graphSelectionHandler)
    ) 
  ).render
  }
}
