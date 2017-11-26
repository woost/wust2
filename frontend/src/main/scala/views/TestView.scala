package wust.frontend.views

import wust.frontend._
import wust.graph._

import rxscalajs.Observable
import outwatch.dom._
import outwatch.Sink
import wust.util.outwatchHelpers._

object TestView extends View {
  override val key = "test"
  override val displayName = "Test"
  override def apply(state: GlobalState) = {
    import state._
    val graph = displayGraphWithoutParents.map(_.graph)
    graph(g => println(s"TestView: got graph update. If this is shown more than once per graph update, this is a leak."))
    component(graph, page)
  }

  def component(graph:Observable[Graph], graphSelection:Sink[Page]) = {
    div(
      padding := "20px",
      h1("Test View"),
      children <-- sortedPostItems(graph, graphSelection)
    )
  }

  def sortedPostItems(graph:Observable[Graph], graphSelection:Sink[Page]): Observable[Seq[VNode]] = graph.map { graph =>
    val sortedPosts = HierarchicalTopologicalSort(graph.postIds, successors = graph.successors, children = graph.children)

    sortedPosts.map { postId =>
      val post = graph.postsById(postId)
      postItem(post, graphSelection)
    }
  }

  def postItem(post: Post, graphSelection: Sink[Page]) = {
    div(
      post.title,

      minHeight := "12px",
      border := "solid 1px",
      cursor.pointer,
      onClick(Page.Union(Set(post.id))) --> graphSelection
    )
  }
}
