package frontend.views

import org.scalajs.dom._
import org.scalajs.d3v4
import mhtml._
import util.collectionHelpers._

import graph._
import frontend._

object ListView {
  def postColor(id: AtomId): InteractionMode => d3v4.Color = {
    case FocusMode(`id`) => d3v4.d3.lab("#00ff00")
    case EditMode(`id`) => d3v4.d3.lab("#0000ff")
    case _ => Color.postDefaultColor
  }

  def editPostStyle(id: AtomId): InteractionMode => String = {
    case _ => ""
  }

  case class Tree[A](element: A, children: Seq[Tree[A]] = Seq.empty)

  //TODO contain + connections + backlinks
  def spannedTree(graph: Graph, post: Post): Tree[Post] = spannedTree(graph, post, Set(post))
  def spannedTree(graph: Graph, post: Post, seen: Set[Post]): Tree[Post] = {
    Tree(post, children = graph.children(post.id).filterNot(seen).map { child =>
      spannedTree(graph, child, seen ++ Set(child))
    })
  }

  def component(state: GlobalState) = {
    import state._

    def postItem(post: Post) =
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

    def postTreeItem(tree: Tree[Post], indent: Int = 0): xml.Node =
      <div style={ s"margin-left: ${indent * 10}px" }>
        { postItem(tree.element) }
        {
          tree.children.map(postTreeItem(_, indent + 1))
        }
      </div>

    //TODO: performance: https://github.com/OlivierBlanvillain/monadic-html/issues/13
    <div>
      {
        graph.map { graph =>
          graph.posts.values.map { post =>
            postTreeItem(spannedTree(graph, post))
          }
        }
      }
    </div>
  }
}
