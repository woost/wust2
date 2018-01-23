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

    val newPostSink = eventProcessor.enriched.changes.redirect { (o: Observable[String]) =>
      o.withLatestFrom(state.currentUser)((msg, user) => GraphChanges(addPosts = Set(Post(PostId.fresh, msg, user.id))))
    }

    component(
      currentUser,
      chronologicalPostsAscending,
      newPostSink,
      page,
      pageStyle,
      displayGraphWithoutParents.map(_.graph)
    )
  }

  def component(
                 currentUser: Observable[User],
                 chronologicalPostsAscending: Observable[Seq[Post]],
                 newPostSink: Sink[String],
                 page: Handler[Page],
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

  def chatHistory(currentUser: Observable[User], chronologicalPosts: Observable[Seq[Post]], page: Sink[Page], graph: Observable[Graph]) = {
    div(
      height := "100%",
      overflow.auto,
      padding := "20px",

      children <-- Observable.combineLatestMap3(chronologicalPosts, graph, currentUser)((posts, graph, currentUser) => posts.map(chatMessage(currentUser, _, page, graph))),
      onPostPatch --> sideEffect[(Element, Element)] { case (_, elem) => scrollToBottom(elem) }
    )
  }

  //Fixme: triggered multiple times
  def chatMessage(currentUser: User, post: Post, page: Sink[Page], graph: Graph) = {
    val postTags: Seq[Post] = graph.ancestors(post.id).map(graph.postsById(_)).toSeq

    val isMine = currentUser.id == post.author
    div( // wrapper for floats
      div( // post wrapper
        div( // post content as markdown
          mdHtml(post.content),
          onClick(Page.Union(Set(post.id))) --> page,
          cls := "chatpost",
          padding := "0px 3px",
          margin := "2px 0px",
        ),
        div( // post tags
          postTags.map{ tag =>
              span(
                if(tag.content.length > 20) tag.content.take(20) else tag.content, // there may be better ways
                onClick(Page.Union(Set(tag.id))) --> page,
                backgroundColor := ColorPost.computeTagColor(graph, tag.id),
                fontSize.small,
                color := "#fefefe",
                borderRadius := "2px",
                padding := "0px 3px",
                marginRight := "3px",
              )
          },
          margin := "0px",
          padding := "0px",
        ),
        borderRadius := (if (isMine) "7px 0px 7px 7px" else "0px 7px 7px"),
        float := (if (isMine) "right" else "left"),
        borderWidth := (if (isMine) "1px 7px 1px 1px" else "1px 1px 1px 7px"),
        borderColor := ColorPost.computeColor(graph, post.id),
        backgroundColor := postDefaultColor,
        display.block,
        maxWidth := "80%",
        padding := "5px 10px",
        margin := "5px 0px",
        borderStyle := "solid",
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
