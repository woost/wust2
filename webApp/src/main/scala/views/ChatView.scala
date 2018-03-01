package wust.webApp.views

import org.scalajs.dom.raw.Element
import outwatch.{ObserverSink, Sink}
import outwatch.dom._
import outwatch.dom.dsl._
import wust.webApp._
import wust.webApp.Color._
import wust.graph._
import wust.ids.PostId
import Elements._
import Rendered._
import wust.util.outwatchHelpers._

object ChatView extends View {
  override val key = "chat"
  override val displayName = "Chat"

  override def apply(state: GlobalState) = {
    import state._


    val newPostSink = ObserverSink(eventProcessor.enriched.changes).redirect { (o: Observable[String]) =>
      o.withLatestFrom(state.currentUser)((msg, user) => GraphChanges.addPost(msg, user.id))
    }

    state.displayGraphWithoutParents.foreach(dg => scribe.info(s"ChatView Graph: ${dg.graph}"))


    component(
      currentUser,
      newPostSink,
      page,
      pageStyle,
      displayGraphWithoutParents.map(_.graph)
    )
  }

  def component(
                 currentUser: Observable[User],
                 newPostSink: Sink[String],
                 page: Handler[Page],
                 pageStyle: Observable[PageStyle],
                 graph: Observable[Graph]
               ): VNode = {
    div(
      // height := "100%",
      backgroundColor <-- pageStyle.map(_.bgColor),

      borderRight := "2px solid",
      borderColor <-- pageStyle.map(_.accentLineColor),

      div(
        p( mdHtml(pageStyle.map(_.title)) ),

        chatHistory(currentUser, page, graph),
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

  def chatHistory(currentUser: Observable[User], page: Sink[Page], graph: Observable[Graph]): VNode = {
    div(
      height := "100%",
      overflow.auto,
      padding := "20px",

      children <-- Observable.combineLatestMap2(graph, currentUser)((graph, currentUser) => graph.chronologicalPostsAscending.map(chatMessage(currentUser, _, page, graph))),
      onPostPatch --> sideEffect[(Element, Element)] { case (_, elem) => scrollToBottom(elem) }
    )
  }

  def chatMessage(currentUser: User, post: Post, page: Sink[Page], graph: Graph): VNode = {
    val postTags: Seq[Post] = graph.ancestors(post.id).map(graph.postsById(_)).toSeq

    val isMine = currentUser.id == post.author
    div( // wrapper for floats
      div( // post wrapper
//        div(
//          span( // author id
//            graph.usersById.get(post.author).map(_.name).getOrElse(s"unknown: ${post.author}").toString,
//            fontSize.small,
//            color := "black",
//            borderRadius := "2px",
//            padding := "0px 3px",
//            marginRight := "3px",
//          ),
//          margin := "0px",
//          padding := "0px",
//        ),
        div( // post content as markdown
          mdHtml(post.content),
          onClick(Page.Union(Set(post.id))) --> page,
          cls := "chatpost",
          padding := "0px 3px",
          margin := "2px 0px",
        ),
        div(
          span( // post id
            post.id.toString,
            fontSize.small,
            color := "black",
            borderRadius := "2px",
            padding := "0px 3px",
            marginRight := "3px",
          ),
          margin := "0px",
          padding := "0px",
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

  def inputField(newPostSink: Sink[String]): VNode = {
    textAreaWithEnter(newPostSink)(
      style("resize") := "vertical", //TODO: outwatch resize?
      Placeholders.newPost,
      flex := "0 0 3em",
      display.flex,
      alignItems.stretch
    )
  }
}
