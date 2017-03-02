package frontend.views

import org.scalajs.dom._
import org.scalajs.d3v4
import mhtml._
import util.collection._
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

  def postItem(state: GlobalState, post: Post) = {
    import state._
    <div onclick={ () => focusedPostId.update(_.setOrToggle(post.id)) }>
      {
        mode.map { mode =>
          Views.post(post, color = postColor(post.id)(mode), afterTitle = Some(
            <div>
              <span onclick={ () => editedPostId.update(_.setOrToggle(post.id)) }>[edit]</span>
              {
                collapsedPostIds.map { collapsed =>
                  <span onclick={ () => collapsedPostIds.update(_.toggle(post.id)) }>
                    { if (collapsed(post.id)) "+" else "-" }
                  </span>
                }
              }
            </div>
          ))
        }
      }
    </div>
  }

  def postTreeItem(tree: Tree[AtomId], showPost: AtomId => xml.Node, indent: Int = 0): xml.Node =
    <div style={ s"margin-left: ${indent * 10}px" }>
      { showPost(tree.element) }
      {
        tree.children.map(postTreeItem(_, showPost, indent + 1))
      }
    </div>

  def component(state: GlobalState) = {
    //TODO: performance: https://github.com/OlivierBlanvillain/monadic-html/issues/13
    <div>
      {
        state.graph.map { graph =>
          graph.posts.keys.map { postId =>
            val tree = redundantSpanningTree(postId, graph.children)
            postTreeItem(tree, id => postItem(state, graph.posts(id)))
          }(breakOut)
        }
      }
    </div>
  }
}
