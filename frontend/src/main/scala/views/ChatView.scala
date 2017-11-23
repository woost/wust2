package wust.frontend.views

import org.scalajs.dom.raw.Element
import outwatch.Sink
import outwatch.dom._
import rxscalajs.Observable
import wust.frontend._
import wust.frontend.views.Elements._
import wust.graph._
import wust.ids.PostId
import wust.util.outwatchHelpers._
import Color._

import scala.scalajs.js.timers.setTimeout

object ChatView extends View {
  override val key = "chat"
  override val displayName = "Chat"

  override def apply(state: GlobalState) = {
    import state._

    component(
      chronologicalPostsAscending,
      state.persistence.addPost,
      page,
      ownPosts,
      pageStyle
    )
  }

  def component(
                 chronologicalPostsAscending: Observable[Seq[Post]],
                 newPostSink: Sink[String],
                 page: Sink[Page],
                 ownPosts: PostId => Boolean,
                 pageStyle: Observable[PageStyle]
               ) = {
    div(
      stl("height") := "100%",
      stl("background-color") <-- pageStyle.map(_.bgColor),

      div(
        h1(child <-- pageStyle.map(_.title)),

        chatHistory(chronologicalPostsAscending, page, ownPosts),
        inputField(newPostSink),

        stl("border-left") := "1px solid",
        stl("border-right") := "1px solid",
        stl("border-color") <-- pageStyle.map(_.accentLineColor),

        stl("margin") := "0 auto",
        stl("maxWidth") := "48rem",
        stl("width") := "48rem",
        stl("height") := "100%",

        stl("display") := "flex",
        stl("flexDirection") := "column",
        stl("justifyContent") := "flexStart",
        stl("alignItems") := "stretch",
        stl("alignContent") := "stretch"
      )
    )
  }

  def chatHistory(chronologicalPosts: Observable[Seq[Post]], page: Sink[Page], ownPosts: PostId => Boolean) = {
    div(
      stl("height") := "100%",
      stl("overflow") := "auto",
      stl("padding") := "20px",
      stl("backgroundColor") := "white",

      children <-- chronologicalPosts.map(posts => posts.map(chatMessage(_, page, ownPosts))),

      //TODO: the update hook triggers too early. Try the postpatch-hook from snabbdom instead
      update --> { (e: Element) =>
        println("update hook");
        setTimeout(100) { scrollToBottom(e) }
      }
    )
  }

  def chatMessage(post: Post, page: Sink[Page], ownPosts: PostId => Boolean) = {
    val isMine = ownPosts(post.id)
    div(
      p(
        post.title,
        click(Page.Union(Set(post.id))) --> page,
        stl("maxWidth") := "60%",
        stl("backgroundColor") := (if (isMine) "rgb(192, 232, 255)" else "#EEE"),
        stl("float") := (if (isMine) "right" else "left"),
        stl("clear") := "both",
        stl("padding") := "5px 10px",
        stl("borderRadius") := "7px",
        stl("margin") := "5px 0px",

        stl("cursor") := "pointer" // TODO: What about cursor when selecting text?
      )
    )
  }

  def inputField(newPostSink: Sink[String]) = {
    textAreaWithEnter(newPostSink)(
      //TODO: add resize:none to inner textarea. Requires outwatch vnode.apply with path
      stl("flex") := "0 0 3em",
      stl("display") := "flex",
      stl("alignItems") := "stretch"
    )
  }
}
