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

import scalatags.JsDom.all._
import scalatags.rx.all._
import scala.scalajs.js.timers.setTimeout

object TreeView {
  import Elements._

  //TODO better?
  private var lastSelectedParent: Option[PostId] = None

  val postOrdering: PostId => IdType = Tag.unwrap _

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
    val area = textareaWithEnter(elem => {
      lastSelectedParent = Some(postId)
      Client.api.addPostInContainment(elem.value, postId, state.selectedGroupId.now).call().map { success =>
        if (success) elem.value = ""
      }
    })(rows := "1", cols := "80", placeholder := "type something here...").render
    //TODO: better?
    if (lastSelectedParent.map(_ == postId).getOrElse(false)) {
      setTimeout(100) {
        lastSelectedParent = None
        area.focus()
      }
    }

    div(
      paddingLeft := "10px", color := "#AAAAAA",
      "+ ",
      area
    )
  }

  def postItem(state: GlobalState, post: Post)(implicit ctx: Ctx.Owner): Frag = {
    //TODO: why need rendered textarea for setting value?
    val area = textareaWithEnter(elem => {
      Client.api.updatePost(post.copy(title = elem.value)).call().map { success =>
        //TODO: indicator?
      }
    })(rows := "1", cols := "80"/*TODO:, value := post.title*/).render
    area.value = post.title

    div(
      div(
        bulletPoint(state, post.id),
        collapseButton(state, post.id),
        area,
        deleteButton(post.id)
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
