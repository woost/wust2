package wust.frontend.views

import rx._
import rxext._
import wust.frontend._
import wust.graph._

import scalatags.JsDom.all._
import scalatags.rx.all._

object ArticleView {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(
      state.displayGraphWithParents.map{ dg =>
        val graph = dg.graph
        if (graph.isEmpty) div().render
        else {
          val sorted = HierarchicalTopologicalSort(graph.postIds, successors = graph.successors, children = graph.children)
          div(
            cls := "article",
            sorted.map { postId =>
              val depth = graph.parentDepth(postId)
              val tag = if (graph.children(postId).size == 0) p()
              else if (depth == 0) h1()
              else if (depth == 1) h2()
              else if (depth == 2) h3()
              else if (depth == 3) h4()
              else if (depth == 4) h5()
              else if (depth == 5) h6()
              else h6 ()
              val focusLink = span(
                span("#"),
                attr("aria-hidden") := "true",
                cls := "focuslink",
                onclick := { () => state.graphSelection() = GraphSelection.Union(Set(postId)) }
              ).render
              tag(
                focusLink,
                graph.postsById(postId).title
              )
            }
          ).render
        }
      }
    )
  }
}
