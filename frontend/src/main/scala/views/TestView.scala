package wust.frontend.views

import rx._
import rxext._
import wust.frontend._
import wust.graph._

import org.scalajs.dom.{window, document, console}
import scala.scalajs.js

import outwatch.dom._
import wust.util.outwatchHelpers._

import cats.instances.list._
import cats.syntax.traverse._
import rxscalajs.Observable

object TestView {
  def postItem(post: Post, graphSelectionHandler: Handler[GraphSelection]) = {
    div(
      stl("minHeight") := "12px",
      stl("border") := "solid 1px",
      stl("cursor") := "pointer",
      click(GraphSelection.Union(Set(post.id))) --> graphSelectionHandler,
      post.title
    )
  }

  def sortedPostItems(state: GlobalState, graphSelectionHandler: Handler[GraphSelection])(implicit ctx: Ctx.Owner): Observable[Seq[VNode]] = state.displayGraphWithoutParents.toObservable.map { dg =>
    val graph = dg.graph
    val sortedPosts = HierarchicalTopologicalSort(graph.postIds, successors = graph.successors, children = graph.children)

    sortedPosts.map { postId =>
      val post = graph.postsById(postId)
      postItem(post, graphSelectionHandler)
    }
  }

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val ints = createHandler[Int](): Handler[Int]
    val incs = ints.isomorphic[Int](_ + 1, _ - 1)
    ints(i => println(s"ints: $i"))
    incs(i => println(s"incs: $i"))
    div(
      input(placeholder := "ints", value := 2, value <-- ints, inputString(_.toInt) --> ints),
      input(placeholder := "incs", value := 3, value <-- incs, inputString(_.toInt) --> incs),
      stl("padding") := "20px",
      children <-- sortedPostItems(state, state.graphSelection)
    ).render
  }
}
