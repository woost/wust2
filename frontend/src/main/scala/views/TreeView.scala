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
  private var lastSelectedParent: Option[PostId] = None

  val postOrdering: PostId => IdType = Tag.unwrap _

  def textfield = span(contenteditable := "true", width := "80ex")

  def bulletPoint(state: GlobalState, postId: PostId) = span(
    "o ",
    onclick := { () => state.graphSelection() = GraphSelection.Union(Set(postId)) }
  )

  def collapseButton(state: GlobalState, postId: PostId) = span(
    "- ",
    onclick := { () => state.collapsedPostIds() = state.collapsedPostIds.now toggle postId }
  )

  def deleteButton(postId: PostId) = span(
    " x",
    onclick := { () => Client.api.deletePost(postId).call() }
  )

  def insertPostPlaceholder(state: GlobalState, postId: PostId) = {
    val area = textfield(onkeydown := { (event: KeyboardEvent) =>
        val elem = event.target.asInstanceOf[HTMLElement]
        onKey(event) {
          case KeyCode.Enter =>
            lastSelectedParent = Some(postId)
            Client.api.addPostInContainment(elem.innerHTML, postId, state.selectedGroupId.now).call().map { success =>
              if (success) elem.innerHTML = ""
            }
          // case KeyCode.Up
        }
    }).render

    //TODO: better?
    if (lastSelectedParent.map(_ == postId).getOrElse(false)) {
      setTimeout(100) {
        lastSelectedParent = None
        area.focus()
      }
    }

    div(
      display.flex,
      paddingLeft := "10px", color := "#AAAAAA",
      "+ ",
      area
    )
  }

  def nextSiblingElement(elem: HTMLElement, next: HTMLElement => HTMLElement): Option[HTMLElement] = {
    val sibling = Option(next(elem))
    sibling orElse {
      val parent = Option(elem.parentElement)
      parent.flatMap(nextSiblingElement(_, next))
    }
  }

  def postItem(state: GlobalState, post: Post)(implicit ctx: Ctx.Owner): Frag = {
    //TODO: why need rendered textarea for setting value?
    val area = textfield(post.title, onkeydown := { (event: KeyboardEvent) =>
      val elem = event.target.asInstanceOf[HTMLElement]
      onKey(event) {
        case KeyCode.Enter =>
          Client.api.updatePost(post.copy(title = elem.innerHTML)).call().map { success =>
            //TODO: indicator?
          }
        case KeyCode.Up => nextSiblingElement(elem.parentElement.parentElement,  _.previousElementSibling.asInstanceOf[HTMLElement] ).foreach(_.querySelector("""span[contenteditable="true"]""").asInstanceOf[HTMLElement].focus())
        case KeyCode.Down => nextSiblingElement(elem.parentElement.parentElement,_.nextElementSibling.asInstanceOf[HTMLElement]).foreach(_.querySelector("""span[contenteditable="true"]""").asInstanceOf[HTMLElement].focus())
      }
    })

    div(
      div(
        display.flex,
        bulletPoint(state, post.id),
        collapseButton(state, post.id),
        deleteButton(post.id),
        area
      )
    )
  }

  def postTreeItem(tree: Tree[PostId], showPost: (PostId, Seq[Frag]) => Frag)(implicit ctx: Ctx.Owner): Frag = {
    showPost(tree.element, tree.children
      .sortBy(_.element |> postOrdering)
      .map(postTreeItem(_, showPost))
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
          postTreeItem(tree, { (id, inner) =>
            div(
              paddingLeft := "10px",
              postItem(state, graph.postsById(id)),
              inner,
              insertPostPlaceholder(state, id)
            )
          })
        }
      ).render
    })
  }
}
