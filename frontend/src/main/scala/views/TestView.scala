package wust.frontend.views

import wust.frontend._
import wust.graph._

import org.scalajs.dom.{window, document, console}
import scala.scalajs.js


import cats.instances.list._
import cats.syntax.traverse._

import rxscalajs.Observable
import outwatch.dom._
import outwatch.Sink
import wust.util.outwatchHelpers._

object TestView {
  def apply(state: GlobalState) = {
    import state._
    val graph = displayGraphWithoutParents.map(_.graph)
    graph(g => println(s"TestView: got graph update"))
    component(graph, graphSelection)
  }

  def component(graph:Observable[Graph], graphSelection:Sink[GraphSelection]) = {
    div(
      stl("padding") := "20px",
      children <-- sortedPostItems(graph, graphSelection)
    ).render
  }

  def sortedPostItems(graph:Observable[Graph], graphSelection:Sink[GraphSelection]): Observable[Seq[VNode]] = graph.map { graph =>
    val sortedPosts = HierarchicalTopologicalSort(graph.postIds, successors = graph.successors, children = graph.children)

    sortedPosts.map { postId =>
      val post = graph.postsById(postId)
      postItem(post, graphSelection)
    }
  }

  def postItem(post: Post, graphSelection: Sink[GraphSelection]) = {
    div(
      post.title,
      stl("minHeight") := "12px",
      stl("border") := "solid 1px",
      stl("cursor") := "pointer",
      click(GraphSelection.Union(Set(post.id))) --> graphSelection
    )
  }
}
