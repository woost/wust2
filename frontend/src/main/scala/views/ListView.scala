package frontend.views

import org.scalajs.dom._
import org.scalajs.d3v4
import util.collection._
import rx._
import scalatags.rx.all._
import scalatags.JsDom.all._
import collection.breakOut

import graph._
import frontend._
import util.algorithm.{Tree, redundantSpanningTree}

object ListView {
  def postColor(id: AtomId, mode: Rx[InteractionMode])(implicit ctx: Ctx.Owner): Rx[d3v4.Color] = mode.map {
    //TODO scala.reflect.internal.FatalError: unexpected UnApply frontend.FocusMode.unapply(<unapply-selector>) <unapply> (`id`)
    // case FocusMode(`id`) => d3v4.d3.lab("#00ff00")
    // case EditMode(`id`) => d3v4.d3.lab("#0000ff")
    case _ => Color.postDefaultColor
  }

  def postItem(state: GlobalState, post: Post)(implicit ctx: Ctx.Owner): Modifier = {
    import state._
    Views.post(
      post,
      color := postColor(post.id, mode).map(_.toString),
      onclick := { () => focusedPostId.update(_.setOrToggle(post.id)) }
    )(
      div(
        span(onclick := { () => editedPostId.update(_.setOrToggle(post.id)) }, "[edit]"),
        collapsedPostIds.rx.map { collapsed =>
          span(
            onclick := { () => collapsedPostIds.update(_.toggle(post.id)) },
            if (collapsed(post.id)) "+" else "-"
          ).render
        }
      )
    )
  }

  def postTreeItem(tree: Tree[AtomId], showPost: AtomId => Modifier, indent: Int = 0)(implicit ctx: Ctx.Owner): Modifier = div(
    marginLeft := indent * 10,
    showPost(tree.element),
    tree.children.map(postTreeItem(_, showPost, indent + 1))
  )

  def component(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(state.graph.rx.map { graph =>
      div( //TODO: avoid this nesting by passing Rx[Seq[Element]] to the outer div?
        (graph.posts.keys.map { postId =>
          val tree = redundantSpanningTree(postId, graph.children)
          postTreeItem(tree, id => postItem(state, graph.posts(id)))
        }).toList
      ).render
    })
  }
}
