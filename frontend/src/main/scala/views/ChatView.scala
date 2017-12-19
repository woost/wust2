package wust.frontend.views

import org.scalajs.dom.raw.Element
import outwatch.Sink
import outwatch.dom._
import wust.frontend._
import wust.frontend.views.Elements._
import wust.graph._
import wust.ids.PostId
import wust.util.outwatchHelpers._
import Color._

object ChatView extends View {
  override val key = "chat"
  override val displayName = "Chat"

  override def apply(state: GlobalState) = {
    import state._

    component(
      chronologicalPostsAscending,
      state.eventProcessor.enriched.addPost,
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
      height := "100%",
      backgroundColor <-- pageStyle.map(_.bgColor),

      div(
        h1(child <-- pageStyle.map(_.title)),

        chatHistory(chronologicalPostsAscending, page, ownPosts),
        inputField(newPostSink),

        borderLeft := "1px solid",
        borderRight := "1px solid",
        borderColor <-- pageStyle.map(_.accentLineColor),

        margin := "0 auto",
        maxWidth := "48rem",
        width := "48rem",
        height := "100%",

        display.flex,
        flexDirection.column,
        justifyContent.flexStart,
        alignItems.stretch,
        alignContent.stretch
      )
    )
  }

  def chatHistory(chronologicalPosts: Observable[Seq[Post]], page: Sink[Page], ownPosts: PostId => Boolean) = {
    div(
      height := "100%",
      overflow.auto,
      padding := "20px",
      backgroundColor := "white", //TODO: .white

      children <-- chronologicalPosts.map(posts => posts.map(chatMessage(_, page, ownPosts))),

      //TODO: the update hook triggers too early. Try the postpatch-hook from snabbdom instead
      onPostpatch --> { (e: Element) =>
        println("postpatch hook");
        scrollToBottom(e) 
      }
    )
  }

  def chatMessage(post: Post, page: Sink[Page], ownPosts: PostId => Boolean) = {
    val isMine = ownPosts(post.id)
    div(
      p(
        post.title,
        onClick(Page.Union(Set(post.id))) --> page,
        maxWidth := "60%",
        backgroundColor := (if (isMine) "rgb(192, 232, 255)" else "#EEE"),
        float := (if (isMine) "right" else "left"),
        clear.both,
        padding := "5px 10px",
        borderRadius := "7px",
        margin := "5px 0px",

        cursor.pointer // TODO: What about cursor when selecting text?
      )
    )
  }

  def inputField(newPostSink: Sink[String]) = {
    textAreaWithEnter(newPostSink)(
      //TODO: add resize:none to inner textarea. Requires outwatch vnode.apply with path
      flex := "0 0 3em",
      display.flex,
      alignItems.stretch
    )
  }
}
