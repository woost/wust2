package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import Rendered._

object ArticleView extends View {
  override val key = "article"
  override val displayName = "Article"

  override def apply(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
    import state._

    component(graph, page, pageStyle)
  }

  def component(graph: Rx[Graph], graphSelection: Var[Page], pageStyle: Rx[PageStyle])(
      implicit owner: Ctx.Owner
  ): VNode = {
    div(
      backgroundColor <-- pageStyle.map(_.bgColor.toHex),
      div(
        cls := "article",
        graph.map { graph =>
          val sortedPosts = HierarchicalTopologicalSort(
            graph.nodeIds,
            successors = graph.successorsWithoutParent,
            children = graph.children
          )

          sortedPosts.map {
            nodeId =>
              val post = graph.nodesById(nodeId)
              val depth = graph.parentDepth(nodeId)
              val tag =
                if (graph.children(nodeId).isEmpty) p()
                else if (depth == 0) h1()
                else if (depth == 1) h2()
                else if (depth == 2) h3()
                else if (depth == 3) h4()
                else if (depth == 4) h5()
                else if (depth == 5) h6()
                else h6()

              tag(
                span(
                  span("#"),
                  cls := "focuslink",
                  onClick(Page(Seq(post.id))) --> graphSelection
                ),
                renderNodeData(post.data)
              )
          }
        }
      )
    )
  }
}
