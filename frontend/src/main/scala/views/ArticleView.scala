package wust.frontend.views

import org.scalajs.dom.raw.Element
import outwatch.Sink
import outwatch.dom._
import wust.frontend._
import wust.frontend.views.Elements._
import wust.graph._
import wust.ids.PostId
import wust.util.outwatchHelpers._
import Color._

import scala.scalajs.js.timers.setTimeout

object ArticleView extends View {
  override val key = "article"
  override val displayName = "Article"

  override def apply(state: GlobalState) = {
    import state._

    component(displayGraphWithParents, page, pageStyle)
  }

  def component(dgo:Observable[DisplayGraph], graphSelection:Sink[Page], pageStyle: Observable[PageStyle]) = {
    div(
      height := "100%",
      backgroundColor <-- pageStyle.map(_.bgColor),
      div(
        cls := "article",
        children <-- dgo.map {
          dg =>
            val sortedPosts = HierarchicalTopologicalSort(dg.graph.postIds, successors = dg.graph.successors, children = dg.graph.children)

            sortedPosts.map { postId =>
              val post = dg.graph.postsById(postId)
              val depth = dg.graph.parentDepth(postId)
              val tag = if (dg.graph.children(postId).size == 0) p()
                else if (depth == 0) h1()
                else if (depth == 1) h2()
                else if (depth == 2) h3()
                else if (depth == 3) h4()
                else if (depth == 4) h5()
                else if (depth == 5) h6()
                else h6 ()

              tag(
                span(
                  span("#"),
                  cls := "focuslink",
                  onClick(Page.Union(Set(post.id))) --> graphSelection
                ),
                post.title
              )
            }
        }
      )
    )
  }
}
