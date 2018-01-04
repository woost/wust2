package wust.frontend.views

import org.scalajs.dom.raw.Element
import outwatch.Sink
import outwatch.dom._
import outwatch.dom.dsl._
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

    state
    component(
      chronologicalPostsAscending,
      eventProcessor.enriched.addPost,
      page,
      ownPosts,
      pageStyle,
      displayGraphWithParents.map(_.graph)
    )
  }

  def component(
                 chronologicalPostsAscending: Observable[Seq[Post]],
                 newPostSink: Sink[String],
                 page: Sink[Page],
                 ownPosts: PostId => Boolean,
                 pageStyle: Observable[PageStyle],
                 graph: Observable[Graph]
               ) = {
    div(
      // height := "100%",
      backgroundColor <-- pageStyle.map(_.bgColor),

      borderRight := "2px solid",
      borderColor <-- pageStyle.map(_.accentLineColor),

      div(
        h1(child <-- pageStyle.map(_.title)),

        chatHistory(chronologicalPostsAscending, page, ownPosts, graph),
        inputField(newPostSink),


        margin := "0 auto",
        maxWidth := "48rem",
        height := "100%",

        display.flex,
        flexDirection.column,
        justifyContent.flexStart,
        alignItems.stretch,
        alignContent.stretch
      )
    )
  }

  def chatHistory(chronologicalPosts: Observable[Seq[Post]], page: Sink[Page], ownPosts: PostId => Boolean, graph: Observable[Graph]) = {
    div(
      height := "100%",
      overflow.auto,
      padding := "20px",

      children <-- chronologicalPosts.combineLatestMap(graph)((posts, graph) => posts.map(chatMessage(_, page, ownPosts, graph))),

      //TODO: the update hook triggers too early. Try the postpatch-hook from snabbdom instead
      onPostpatch --> { (e: Element) =>
        println("postpatch hook");
        scrollToBottom(e)
      }
    )
  }

  def chatMessage(post: Post, page: Sink[Page], ownPosts: PostId => Boolean, graph: Graph) = {
    val isMine = ownPosts(post.id)
    div(
      display.block,
      clear.both,
      width := "100%",
      borderBottom := "1px solid gray",
      padding := "5px 10px",
      margin := "5px 0px",
      p(
        post.content,
        onClick(Page.Union(Set(post.id))) --> page,
        maxWidth := "60%",
        backgroundColor := (if (isMine) "rgb(192, 232, 255)" else "#EEE"),
        float := (if (isMine) "right" else "left"),
        clear.both,
        padding := "5px 10px",
        borderRadius := "7px",
        border := "1px solid gray",
        margin := "5px 0px",

        cursor.pointer // TODO: What about cursor when selecting text?
      ),
      div(
        float := (if (isMine) "right" else "left"),
        h3("Post data"),
        p(
          s"post content: ${post.content}",
          br(),
          s"post author: ${post.author.toString}",
          br(),
          s"get username from graph: ${graph.usersById.get(post.author).toString}",
          br(),
          s"graph user names: ${graph.users.map(_.name).toString}",
          br(),
          s"graph user ids: ${graph.userIds.toString}",
          br(),
          s"creation time: ${post.created.toString}",
          br(),
          s"modification time: ${post.modified.toString}"
        )
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
