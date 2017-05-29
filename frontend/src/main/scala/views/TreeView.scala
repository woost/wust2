package wust.frontend.views

import org.scalajs.d3v4
import rx._
import rxext._
import wust.frontend._
import wust.ids._
import wust.graph._
import wust.util.Pipe
import wust.util.algorithm.{ TreeContext, Tree, redundantSpanningTree }
import wust.util.collection._
import autowire._
import boopickle.Default._
import wust.api._
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.Tag
import scala.math.Ordering

import org.scalajs.dom.{ window, document, console }
import org.scalajs.dom.raw.{ Text, Element, HTMLElement }
import scalatags.JsDom.all._
import scala.scalajs.js
import scalatags.rx.all._
import scala.scalajs.js.timers.setTimeout
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.{ Event, KeyboardEvent }

//TODO proper ordering
object PostOrdering extends Ordering[Post] {
  def compare(a: Post, b: Post) = Tag.unwrap(a.id) compare Tag.unwrap(b.id)
}

object TreeView {
  import Elements._

  //TODO better?
  private var nextFocusedPost: Option[PostId] = None

  implicit val postOrdering = PostOrdering

  // preserves newlines and white spaces: white-space: pre
  def textfield = div(contenteditable := "true", style := "white-space: pre", width := "80ex")

  def bulletPoint(state: GlobalState, postId: PostId) = div(
    "•", paddingLeft := "5px", paddingRight := "5px", cursor := "pointer",
    onclick := { () => state.graphSelection() = GraphSelection.Union(Set(postId)) }
  )

  def collapseButton(state: GlobalState, postId: PostId)(implicit ctx: Ctx.Owner) = button(
    span(state.collapsedPostIds.map(ids => if (ids(postId)) "+" else "-")).render,
    onclick := { () => state.collapsedPostIds() = state.collapsedPostIds.now toggle postId }
  )

  def deleteButton(state: GlobalState, postId: PostId) = buttonClick(
    "delete",
    Client.api.deletePost(postId, state.graphSelection.now).call()
  )

  def nextInParent(elem: HTMLElement, next: HTMLElement => Option[HTMLElement]): Option[HTMLElement] = {
    val sibling = next(elem)
    sibling orElse {
      val parent = Option(elem.parentElement)
      parent.flatMap(nextInParent(_, next))
    }
  }

  def findNextTextfield(elem: HTMLElement, isReversed: Boolean): Option[HTMLElement] = {
    val queried = elem.querySelectorAll("""div[contenteditable="true"]:not([disabled])""")

    if (queried.length <= 1) None
    else {
      var foundIdx: Option[Int] = None;
      for (i <- 0 until queried.length) {
        val e = queried(i).asInstanceOf[HTMLElement]
        if (e == document.activeElement)
          foundIdx = Some(i);
      }

      foundIdx.flatMap { foundIdx =>
        val offset = if (isReversed) -1 else 1
        val nextIdx = (foundIdx + offset) match {
          case x if x < 0              => queried.length - 1
          case x if x > queried.length => 0
          case x                       => x
        }
        queried(nextIdx).asInstanceOf[js.UndefOr[HTMLElement]].toOption
      }
    }
  }

  def focusUp(elem: HTMLElement) = {
    nextInParent(elem.parentElement.parentElement.parentElement, findNextTextfield(_, isReversed = true)).foreach(focusAndSetCursor _)
  }
  def focusDown(elem: HTMLElement) = {
    nextInParent(elem.parentElement.parentElement.parentElement, findNextTextfield(_, isReversed = false)).foreach(focusAndSetCursor _)
  }

  def textAroundCursorSelectionElement(elem: HTMLElement) = {
    val cursorRange = window.getSelection.getRangeAt(0)
    val lhs = document.createRange()
    val rhs = document.createRange()
    lhs.setStartBefore(elem)
    lhs.setEnd(cursorRange.startContainer, cursorRange.startOffset)
    rhs.setStart(cursorRange.endContainer, cursorRange.endOffset)
    rhs.setEndAfter(elem)
    (lhs.toString, rhs.toString)
  }

  def focusAndSetCursor(elem: HTMLElement) {
    elem.focus()
    val s = window.getSelection()
    val r = document.createRange()
    r.selectNodeContents(Option(elem.firstChild).getOrElse(elem))
    r.collapse(false) // false: collapse to end, true: collapse to start
    s.removeAllRanges()
    s.addRange(r)
  }

  def handleKey(state: GlobalState, c: TreeContext[Post], tree: Tree[Post], event: KeyboardEvent) = {
    val post = tree.element
    val elem = event.target.asInstanceOf[HTMLElement]
    onKey(event) {
      case KeyCode.Enter if !event.shiftKey =>
        val (currPostText, newPostText) = textAroundCursorSelectionElement(elem)
        Client.api.updatePost(post.copy(title = currPostText)).call()
        //TODO: do not create empty post, create later when there is a title
        val postIdFut = c.parentMap.get(tree) match {
          case Some(parentTree) =>
            Client.api.addPostInContainment(newPostText, parentTree.element.id, state.selectedGroupId.now).call()
          case None =>
            Client.api.addPost(newPostText, state.graphSelection.now, state.selectedGroupId.now).call()
        }

        postIdFut.map { postId =>
          postId.foreach(id => nextFocusedPost = Some(id))
        }
        false
      case KeyCode.Tab => event.shiftKey match {
        case false =>
          c.previousMap.get(tree).foreach { previousTree =>
            Client.api.createContainment(previousTree.element.id, post.id).call()
            c.parentMap.get(tree).foreach { parentTree =>
              Client.api.deleteContainment(Containment(parentTree.element.id, post.id)).call()
            }
          }
          false
        case true =>
          for {
            parent <- c.parentMap.get(tree)
            grandParent = c.parentMap.get(parent)
          } {
            grandParent match {
              case Some(grandParent) =>
                Client.api.createContainment(grandParent.element.id, post.id).call()
              case None =>
                Client.api.createSelection(post.id, state.graphSelection.now).call()
            }
            Client.api.deleteContainment(Containment(parent.element.id, post.id)).call()
          }
          false
      }
      case KeyCode.Up if !event.shiftKey =>
        val sel = window.getSelection.getRangeAt(0)
        if (sel.startOffset == sel.endOffset && !elem.textContent.take(sel.startOffset).contains('\n')) {
          focusUp(elem)
          false
        } else true
      case KeyCode.Down if !event.shiftKey =>
        val sel = window.getSelection.getRangeAt(0)
        if (sel.startOffset == sel.endOffset && !elem.textContent.drop(sel.endOffset).contains('\n')) {
          focusDown(elem)
          false
        } else true
      case KeyCode.Delete if !event.shiftKey =>
        val sel = window.getSelection.getRangeAt(0)
        val textElem = elem.firstChild.asInstanceOf[Text]
        if (sel.startOffset == textElem.length && sel.endOffset == textElem.length) {
          c.nextMap.get(tree).map { nextTree =>
            val nextPost = nextTree.element
            Client.api.updatePost(post.copy(title = post.title + " " + nextPost.title)).call()
            Client.api.deletePost(nextPost.id, state.graphSelection.now).call()
            false
          }.getOrElse(true)
        } else true
      case KeyCode.Backspace if !event.shiftKey =>
        val sel = window.getSelection.getRangeAt(0)
        if (sel.startOffset == 0 && sel.endOffset == 0) {
          c.previousMap.get(tree).map { previousTree =>
            val prevPost = previousTree.element
            val (_, remainingText) = textAroundCursorSelectionElement(elem)
            Client.api.updatePost(prevPost.copy(title = prevPost.title + " " + remainingText)).call()
            Client.api.deletePost(post.id, state.graphSelection.now).call()
            focusUp(elem)
            false
          }.getOrElse(true)
        } else true
    }
  }

  def postItem(state: GlobalState, c: TreeContext[Post], tree: Tree[Post])(implicit ctx: Ctx.Owner): Frag = {
    //TODO: why need rendered textarea for setting value?
    val post = tree.element
    val area = textfield(
      post.title,
      onfocus := { () =>
        nextFocusedPost = Some(post.id)
      },
      onblur := { (event: Event) =>
        val elem = event.target.asInstanceOf[HTMLElement]
        // val elemWithText = event.target.asInstanceOf[}]
        if (post.title != elem.textContent)
          Client.api.updatePost(post.copy(title = elem.textContent)).call()
      },
      onkeydown := { (e: KeyboardEvent) => handleKey(state, c, tree, e) }
    ).render

    //TODO: better?
    if (nextFocusedPost.map(_ == post.id).getOrElse(false)) {
      setTimeout(200) {
        focusAndSetCursor(area)
      }
    }

    div(
      display.flex,
      bulletPoint(state, post.id),
      area,
      collapseButton(state, post.id),
      deleteButton(state, post.id)
    )
  }

  def postTreeItem(state: GlobalState, c: TreeContext[Post], tree: Tree[Post])(implicit ctx: Ctx.Owner): Frag = {
    val childNodes = tree.children
      .sortBy(_.element)
      .map(postTreeItem(state, c, _))
      .toList

    div(
      paddingLeft := "10px",
      postItem(state, c, tree),
      childNodes
    )
  }

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(state.displayGraph.map { dg =>
      import dg.graph
      val rootPosts = graph.posts
        .filter(p => graph.parents(p.id).isEmpty)
        .toList
        .sorted

      def postChildren(post: Post): Iterable[Post] = {
        graph.children(post.id).map(graph.postsById(_))
      }

      val trees = rootPosts.map(redundantSpanningTree[Post](_, postChildren _))
      val context = new TreeContext(trees: _*)

      div(
        padding := "100px",
        trees.map(postTreeItem(state, context, _))
      ).render
    })
  }
}
