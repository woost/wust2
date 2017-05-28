package wust.frontend.views

import org.scalajs.d3v4
import rx._
import rxext._
import wust.frontend._
import wust.ids._
import wust.graph._
import wust.util.Pipe
import wust.util.algorithm.{Tree, redundantSpanningTree}
import wust.util.collection._
import autowire._
import boopickle.Default._
import wust.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.Tag

import org.scalajs.dom.console
import org.scalajs.dom.raw.{Element, HTMLElement}
import scalatags.JsDom.all._
import scalatags.rx.all._
import scala.scalajs.js.timers.setTimeout
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.KeyboardEvent

object TreeView {
  import Elements._

  //TODO better?
  private var nextFocusedPost: Option[PostId] = None

  val postOrdering: PostId => IdType = Tag.unwrap _

  def textfield = div(contenteditable := "true", width := "80ex")

  def bulletPoint(state: GlobalState, postId: PostId) = div(
    " o ", color := "#aaaaaa",
    onclick := { () => state.graphSelection() = GraphSelection.Union(Set(postId)) }
  )

  def collapseButton(state: GlobalState, postId: PostId) = div(
    " - ", color := "#aaaaaa",
    onclick := { () => state.collapsedPostIds() = state.collapsedPostIds.now toggle postId }
  )

  def deleteButton(postId: PostId) = div(
    " x ", color := "#aaaaaa",
    onclick := { () => Client.api.deletePost(postId).call() }
  )

  def findNextSibling(elem: HTMLElement, next: HTMLElement => HTMLElement): Option[HTMLElement] = {
    val sibling = Option(next(elem))
    sibling orElse {
      val parent = Option(elem.parentElement)
      parent.flatMap(findNextSibling(_, next))
    }
  }

  def findTextfield(elem: HTMLElement) = Option(elem.querySelector("""div[contenteditable="true"]""").asInstanceOf[HTMLElement])

  def focusUp(elem: HTMLElement) = {
    findNextSibling(elem.parentElement,  _.previousElementSibling.asInstanceOf[HTMLElement]).foreach { next =>
      findTextfield(next).foreach(_.focus)
    }
  }
  def focusDown(elem: HTMLElement) = {
    findNextSibling(elem.parentElement,  _.nextElementSibling.asInstanceOf[HTMLElement]).foreach { next =>
      findTextfield(next).foreach(_.focus)
    }
  }

  def postItem(state: GlobalState, parent: Option[Post], post: Post)(implicit ctx: Ctx.Owner): Frag = {
    //TODO: why need rendered textarea for setting value?
    val area = textfield(
      post.title,
      onfocus := { () =>
        nextFocusedPost = Some(post.id)
      },
      onkeydown := { (event: KeyboardEvent) =>
        val elem = event.target.asInstanceOf[HTMLElement]
        onKey(event) {
          case KeyCode.Enter =>
            if (post.title != elem.innerHTML) {
              nextFocusedPost = Some(post.id)
              Client.api.updatePost(post.copy(title = elem.innerHTML)).call().map { success =>
                //TODO: indicator?
              }
            } else if (elem.innerHTML.nonEmpty) {
              //TODO: do not create empty post, createlater when there is a title
              val postIdFut = parent match {
                case Some(parent) =>
                  Client.api.addPostInContainment("", parent.id, state.selectedGroupId.now).call()
                case None =>
                  Client.api.addPost("", state.graphSelection.now, state.selectedGroupId.now).call()
              }
              postIdFut.map { postId =>
                postId.foreach(id => nextFocusedPost = Some(id))
              }
            }
          case KeyCode.Tab =>
            if (post.title != elem.innerHTML) {
              nextFocusedPost = Some(post.id)
              Client.api.updatePost(post.copy(title = elem.innerHTML)).call().map { success =>
                //TODO: indicator?
              }
            } else if (elem.innerHTML.nonEmpty) {
              //TODO: do not create empty post, createlater when there is a title
              Client.api.addPostInContainment("", post.id, state.selectedGroupId.now).call().map { postId =>
                  postId.foreach(id => nextFocusedPost = Some(id))
              }
            }
          case KeyCode.Up => focusUp(elem)
          case KeyCode.Down => focusDown(elem)
          case KeyCode.Backspace if (elem.innerHTML.isEmpty) =>
            Client.api.deletePost(post.id).call().foreach { success =>
              if (success) focusUp(elem)
            }
        }
      }
    ).render

    //TODO: better?
    if (nextFocusedPost.map(_ == post.id).getOrElse(false)) {
      nextFocusedPost = None
      setTimeout(100) {
        area.focus()
      }
    }

    div(
      display.flex,
      bulletPoint(state, post.id),
      collapseButton(state, post.id),
      deleteButton(post.id),
      area
    )
  }

  def postTreeItem(tree: Tree[PostId], showPost: (Option[PostId], PostId, Seq[Frag]) => Frag, parent: Option[PostId] = None)(implicit ctx: Ctx.Owner): Frag = {
    showPost(parent, tree.element, tree.children
      .sortBy(_.element |> postOrdering)
      .map(postTreeItem(_, showPost, Some(tree.element)))
    )
  }

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(state.displayGraph.map { dg =>
      import dg.graph
      val rootPosts = graph.posts
        .filter(p => graph.parents(p.id).isEmpty)
        .toList
        .sortBy(_.id |> postOrdering)

      div(
        padding := "100px",
        rootPosts.map { p =>
          val tree = redundantSpanningTree(p.id, graph.children)
          postTreeItem(tree, { (parentId, id, inner) =>
            val post = graph.postsById(id)
            val parent = parentId.map(graph.postsById(_))
            div(
              paddingLeft := "10px",
              postItem(state, parent, post),
              inner
            )
          })
        }
      ).render
    })
  }
}
