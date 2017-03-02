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
  def postColor(id: AtomId): InteractionMode => d3v4.Color = {
    case FocusMode(`id`) => d3v4.d3.lab("#00ff00")
    case EditMode(`id`) => d3v4.d3.lab("#0000ff")
    case _ => Color.postDefaultColor
  }

  def editPostStyle(id: AtomId): InteractionMode => String = {
    case _ => ""
  }

  def postItem(state: GlobalState, post: Post)(implicit ctx: Ctx.Owner) = {
    import state._
    div(
      onclick := { () => focusedPostId.update(_.setOrToggle(post.id)) },
      mode.map { mode =>
        post.toString
        // Views.post(post, color = postColor(post.id)(mode), afterTitle = Some(
        //   <div>
        //     <span onclick={ () => editedPostId.update(_.setOrToggle(post.id)) }>[edit]</span>
        //     {
        //       collapsedPostIds.map { collapsed =>
        //         <span onclick={ () => collapsedPostIds.update(_.toggle(post.id)) }>
        //           { if (collapsed(post.id)) "+" else "-" }
        //         </span>
        //       }
        //     }
        //     )
        // ))
      }
    )
  }

  def postTreeItem(tree: Tree[AtomId], showPost: AtomId => Modifier, indent: Int = 0)(implicit ctx: Ctx.Owner): Modifier = div(
    marginLeft := indent * 10,
    showPost(tree.element),
    tree.children.map(postTreeItem(_, showPost, indent + 1))
  )

  def component(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val a: Rx[Element] = state.graph.map { graph =>
      div( //TODO: avoid this nesting by passing Rx[Seq[Element]] to the outer div?
        (graph.posts.keys.map { postId =>
          val tree = redundantSpanningTree(postId, graph.children)
          postTreeItem(tree, id => postItem(state, graph.posts(id)))
        }).toList
      ).render
    }
    div(a)
  }
}
