package wust.frontend.views

import org.scalajs.dom.raw.Element
import outwatch.{Sink}
import outwatch.dom._
import outwatch.dom.dsl._
import wust.frontend._
import wust.frontend.Color._
import monix.execution.Scheduler.Implicits.global
import wust.graph._
import wust.ids.PostId
import Elements._, Rendered._

object ChatView extends View {
  override val key = "chat"
  override val displayName = "Chat"

  override def apply(state: GlobalState) = {
    import state._

    component(
      currentUser,
      chronologicalPostsAscending,
      eventProcessor.enriched.addPost,
      page,
      pageStyle,
      displayGraphWithParents.map(_.graph)
    )
  }

  def component(
                 currentUser: Observable[Option[User]],
                 chronologicalPostsAscending: Observable[Seq[Post]],
                 newPostSink: Sink[String],
                 page: Sink[Page],
                 pageStyle: Observable[PageStyle],
                 graph: Observable[Graph]
               ) = {
    div(
      // height := "100%",
      backgroundColor <-- pageStyle.map(_.bgColor),

      borderRight := "2px solid",
      borderColor <-- pageStyle.map(_.accentLineColor),

      div(
        p( mdHtml(pageStyle.map(_.title)) ),

        chatHistory(currentUser, chronologicalPostsAscending, page, graph),
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

  def chatHistory(currentUser: Observable[Option[User]], chronologicalPosts: Observable[Seq[Post]], page: Sink[Page], graph: Observable[Graph]) = {
    div(
      height := "100%",
      overflow.auto,
      padding := "20px",

      children <-- chronologicalPosts.combineLatestMap(graph)((posts, graph) => posts.map(chatMessage(currentUser, _, page, graph))),
      onPostPatch --> sideEffect[(Element, Element)] { case (_, elem) => scrollToBottom(elem) }
    )
  }

  def chatMessage(currentUser: Observable[Option[User]], post: Post, page: Sink[Page], graph: Graph) = {
    // TODO: Filter tags of pageId
    val tags:Seq[Post] = if(graph.consistent.hasParents(post.id)) {
      graph.consistent.parents(post.id).map(id => graph.postsById(id)).toSeq //.filter(_.id != pageId)
    } else {
     Seq.empty[Post]
    }

    //Fixme: triggered multiple times
    val isMine = currentUser.map(_.fold(false)(_.id == post.author))
    currentUser.foreach(println(_))
    div( // wrapper for floats
      div( // post wrapper
        p(
          mdHtml(post.content),
          onClick(Page.Union(Set(post.id))) --> page,
          padding := "2px 3px",
          margin := "2px 0px",
        ),
        tags.map{ tag =>
          span(
            if(tag.content.length > 20) tag.content.take(20) else tag.content, // there may be better ways
            onClick(Page.Union(Set(tag.id))) --> page,
            border := "1px solid grey",
            borderRadius := "3px",
            padding := "2px 3px",
            marginRight := "3px",
            backgroundColor := Color.baseColor(tag.id).toString,
          ),
        },
        display.block,
        maxWidth := "80%",
        padding := "5px 10px",
        margin := "5px 0px",
        border := "1px solid gray",
        borderRadius <-- isMine.map(if (_) "7px 0px 7px 7px" else "0px 7px 7px"),
        backgroundColor <-- isMine.map(if (_) "rgb(192, 232, 255)" else "#EEE"),
        float <-- isMine.map(if (_) "right" else "left"),
        cursor.pointer, // TODO: What about cursor when selecting text?
      ),
      width := "100%",
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
