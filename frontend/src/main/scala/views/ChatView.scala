package wust.frontend.views

import org.scalajs.dom.raw.Element
import outwatch.{Sink}
import outwatch.dom._
import outwatch.dom.dsl._
import wust.frontend._
import wust.frontend.Color._
import wust.frontend.views.Elements._
import monix.execution.Scheduler.Implicits.global
import wust.graph._
import wust.ids.PostId

object ChatView extends View {
  override val key = "chat"
  override val displayName = "Chat"

  override def apply(state: GlobalState) = {
    import state._

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
      onPostPatch --> sideEffect[(Element, Element)] { case (_, elem) => scrollToBottom(elem) }
    )
  }

  def chatMessage(post: Post, page: Sink[Page], ownPosts: PostId => Boolean, graph: Graph) = {
    // TODO: Filter tags of pageId
    val tags:Seq[Post] = if(graph.consistent.hasParents(post.id)) {
      graph.consistent.parents(post.id).map(id => graph.postsById(id)).toSeq //.filter(_.id != pageId)
    } else {
     Seq.empty[Post]
    }
    val isMine = ownPosts(post.id)
    div( // wrapper for floats
      div( // post wrapper
        p(
          post.content,
          onClick(Page.Union(Set(post.id))) --> page,
          maxWidth := "60%",
          padding := "5px 10px",
          margin := "5px 0px",
        ),
        tags.map{ tag =>
          span(
            if(tag.content.length > 20) tag.content.take(20) else tag.content,
            onClick(Page.Union(Set(tag.id))) --> page,
            border := "1px solid grey",
            borderRadius := "3px",
            padding := "2px 3px",
            marginRight := "3px",
            backgroundColor := Color.baseColor(tag.id).toString,
          ),
        },
        display.block,
        width := "100%",
        padding := "5px 10px",
        margin := "5px 0px",
        border := "1px solid gray",
        borderRadius := "7px",
        backgroundColor := (if (isMine) "rgb(192, 232, 255)" else "#EEE"),
        float := (if (isMine) "right" else "left"),
        cursor.pointer, // TODO: What about cursor when selecting text?
      ),
      clear.both,
    )
  }

  def inputField(newPostSink: Sink[String]) = {
    textAreaWithEnter(newPostSink)(
      //TODO: add resize:none to inner textarea. Requires outwatch vnode.apply with path
      Placeholders.newPost,
      flex := "0 0 3em",
      display.flex,
      alignItems.stretch
    )
  }
}
