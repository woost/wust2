package wust.frontend.views

import org.scalajs.d3v4
import rx._
import rxext._
import wust.frontend._
import wust.ids._
import wust.graph._
import wust.util.algorithm.{Tree, redundantSpanningTree}
import wust.util.collection._
import autowire._
import boopickle.Default._
import wust.api._
import scala.concurrent.ExecutionContext.Implicits.global

import scalatags.JsDom.all._
import scalatags.rx.all._

object TreeView {
  import Elements._

  def bulletPoint(state: GlobalState, post: Post) = span(
    "o ",
    onclick := { () => state.graphSelection() = GraphSelection.Union(Set(post.id)) }
  )

  def deleteButton(post: Post) = span(
    " x",
    onclick := { () => Client.api.deletePost(post.id).call() }
  )

  def insertPostPlaceholder(state: GlobalState, post: Post) = div(
    color := "#AAAAAA",
    "+ ",
    textareaWithEnter(elem => {
      Client.api.addPostInContainment(elem.value, post.id, state.selectedGroupId.now).call().map { success =>
        if (success) elem.value = ""
      }
    })(rows := "1", cols := "80", placeholder := "type something here...")
  )

  def postItem(state: GlobalState, post: Post)(implicit ctx: Ctx.Owner): Frag = {
    //TODO: why need rendered textarea for setting value?
    val area = textareaWithEnter(elem => {
          Client.api.updatePost(post.copy(title = elem.value)).call().map { success =>
            //TODO: indicator?
          }
        })(rows := "1", cols := "80"/*TODO:, value := post.title*/).render
    area.value = post.title
    import state._
    div(
      div(
        bulletPoint(state, post),
        area,
        deleteButton(post)
      ),
      insertPostPlaceholder(state, post)(paddingLeft := "20px")
    )
  }

  def postTreeItem(tree: Tree[PostId], showPost: PostId => Frag, indent: Int = 0)(implicit ctx: Ctx.Owner): Frag = div(
    marginLeft := indent * 10,
    showPost(tree.element),
    tree.children.map(postTreeItem(_, showPost, indent + 1))
  )

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    div(state.displayGraph.map { dg =>
      import dg.graph
      div(
        paddingTop := "100px", paddingLeft := "100px",
        graph.posts.filter(p => graph.parents(p.id).isEmpty).map {
          p =>
            val tree = redundantSpanningTree(p.id, graph.children)
            postTreeItem(tree, id => postItem(state, graph.postsById(id)))
        }.toList
      ).render
    })
  }
}
