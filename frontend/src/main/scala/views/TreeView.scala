package wust.frontend.views

import org.scalajs.dom._
import org.scalajs.d3v4
import rx._, rxext._
import scalatags.rx.all._
import scalatags.JsDom.all._
import collection.breakOut

import wust.graph._
import wust.frontend._
import wust.util.algorithm.{Tree, redundantSpanningTree}
import wust.util.collection._

object TreeView {
  //TODO: report bug about pattern matching in rx.map:
  //scala.reflect.internal.FatalError: unexpected UnApply frontend.FocusMode.unapply(<unapply-selector>) <unapply> (_)
  def modeToColor(id: PostId): InteractionMode => d3v4.Color = {
    case FocusMode(`id`) => d3v4.d3.lab("#AAFFAA")
    case EditMode(`id`) => d3v4.d3.lab("#AAAAFF")
    case _ => Color.postDefaultColor
  }
  def postColor(id: PostId, mode: Rx[InteractionMode])(implicit ctx: Ctx.Owner): Rx[d3v4.Color] = mode.map(modeToColor(id))

  def postItem(state: GlobalState, post: Post)(implicit ctx: Ctx.Owner): Frag = {
    import state._
    Views.post(
      post
    )(
      backgroundColor := postColor(post.id, mode).map(_.toString),
      onclick := { () => focusedPostId.updatef(_.setOrToggle(post.id)) },
      div(
        span(onclick := { () => editedPostId.updatef(_.setOrToggle(post.id)) }, "[edit]"),
        collapsedPostIds.rx.map { collapsed =>
          span(
            onclick := { () => collapsedPostIds.updatef(_.toggle(post.id)) },
            if (collapsed(post.id)) "+" else "-"
          ).render
        }
      )
    )
  }

  def postTreeItem(tree: Tree[PostId], showPost: PostId => Frag, indent: Int = 0)(implicit ctx: Ctx.Owner): Frag = div(
    marginLeft := indent * 10,
    showPost(tree.element),
    tree.children.map(postTreeItem(_, showPost, indent + 1))
  )

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(state.graph.rx.map { graph =>
      div( //TODO: avoid this nesting by passing Rx[Seq[Element]] to the outer div?
        (graph.posts.keys.filter(p => graph.parents(p).isEmpty).map { postId =>
          val tree = redundantSpanningTree(postId, graph.children)
          postTreeItem(tree, id => postItem(state, graph.posts(id)))
        }).toList
      ).render
    })
  }
}
