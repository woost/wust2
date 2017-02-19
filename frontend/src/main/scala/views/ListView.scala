package frontend.views

import org.scalajs.dom._
import mhtml._

import graph._
import frontend.{EditMode, FocusMode, InteractionMode, GlobalState}

object ListView {
  def toggleOption(id: AtomId): Option[AtomId] => Option[AtomId] = {
    case Some(`id`) => None
    case _ => Some(id)
  }

  def modeStyle(id: AtomId): InteractionMode => String = {
    case EditMode(`id`) => "background:#0000FF;"
    case FocusMode(`id`) => "background:#00FF00;"
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
      <div style={ mode.map(modeStyle(post.id)) }>
        <span onclick={ () => focusedPostId.update(toggleOption(post.id)) }>{post.toString}</span>
        <span onclick={ () => editedPostId.update(toggleOption(post.id)) }>[edit]</span>
      </div>

    def postTreeItem(tree: Tree[Post], indent: Int = 0): xml.Node =
      <div style={ s"margin-left: ${indent * 10}px" }>
        { postItem(tree.element) }
        { tree.children.map(postTreeItem(_, indent + 1)) }
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
