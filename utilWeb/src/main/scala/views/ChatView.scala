package wust.utilWeb.views

import org.scalajs.dom.raw.Element
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.sdk.PostColor._
import wust.utilWeb._
import wust.utilWeb.outwatchHelpers._
import wust.utilWeb.views.Elements._
import wust.utilWeb.views.Rendered._

object ChatView extends View {
  override val key = "chat"
  override val displayName = "Chat"

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    import state._


    val newPostSink = ObserverSink(eventProcessor.enriched.changes).redirect { (o: Observable[String]) =>
      o.withLatestFrom(state.currentUser.toObservable)((msg, user) => GraphChanges.addPost(msg, user.id))
    }

    state.displayGraphWithoutParents.foreach(dg => scribe.info(s"ChatView Graph: ${dg.graph}"))


    component(
      currentUser,
      newPostSink,
      page,
      pageStyle,
      pageParentPosts,
      displayGraphWithoutParents.map(_.graph)
    )
  }

  def component(
                 currentUser: Rx[User],
                 newPostSink: Sink[String],
                 page: Var[Page],
                 pageStyle: Rx[PageStyle],
                 pageParentPosts: Rx[Seq[Post]],
                 graph: Rx[Graph]
               )(implicit ctx: Ctx.Owner): VNode = {
    div(
        div(
          fontSize := "20px",
          padding := "0px 20px",
          pageParentPosts.map(_.map { parent =>
            span(mdHtml(parent.content))
          })
        ),
      // p( mdHtml(pageStyle.map(_.title)) ),

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
  }

  def chatHistory(currentUser: Rx[User], page: Var[Page], graph: Rx[Graph])(implicit ctx: Ctx.Owner): VNode = {
    div(
      height := "100%",
      overflow.auto,
      padding := "20px",

      Rx{
        val posts = graph().chronologicalPostsAscending
        if (posts.isEmpty) Seq(emptyMessage)
        else posts.map(chatMessage(currentUser(), _, page, graph()))
      },
      onPostPatch --> sideEffect[(Element, Element)] { case (_, elem) => scrollToBottom(elem) }
    )
  }

  def emptyMessage: VNode = h3(textAlign.center, "Nothing here yet.", paddingTop := "40%", color := "rgba(0,0,0,0.5)")

  def chatMessage(currentUser: User, post: Post, page: Var[Page], graph: Graph)(implicit ctx: Ctx.Owner): VNode = {
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
          cls := "chatpost",
          padding := "0px 3px",
          margin := "2px 0px"
        ),
//        div(
//          span( // post id
//            post.id.toString,
//            fontSize.small,
//            color := "black",
//            borderRadius := "2px",
//            padding := "0px 3px",
//            marginRight := "3px",
//          ),
//          margin := "0px",
//          padding := "0px",
//        ),
        div( // post tags
          postTags.map{ tag =>
              span(
                if(tag.content.length > 20) tag.content.take(20) else tag.content, // there may be better ways
                onClick --> sideEffect{e => page() = Page(Seq(tag.id)); e.stopPropagation()},
                backgroundColor := computeTagColor(graph, tag.id),
                fontSize.small,
                color := "#fefefe",
                borderRadius := "2px",
                padding := "0px 3px",
                marginRight := "3px"
              )
          },
          margin := "0px",
          padding := "0px"
        ),
        onClick(Page(Seq(post.id))) --> page,
        borderRadius := (if (isMine) "7px 0px 7px 7px" else "0px 7px 7px"),
        float := (if (isMine) "right" else "left"),
        borderWidth := (if (isMine) "1px 7px 1px 1px" else "1px 1px 1px 7px"),
        borderColor := computeColor(graph, post.id),
        backgroundColor := postDefaultColor,
        display.block,
        maxWidth := "80%",
        padding := "5px 10px",
        margin := "5px 0px",
        borderStyle := "solid",
        cursor.pointer // TODO: What about cursor when selecting text?
      ),
      width := "100%",
      clear.both
    )
  }

  def inputField(newPostSink: Sink[String])(implicit ctx: Ctx.Owner): VNode = {
    textAreaWithEnter(newPostSink)(
      style("resize") := "vertical", //TODO: outwatch resize?
      Placeholders.newPost,
      flex := "0 0 3em",
      display.flex,
      alignItems.stretch
    )
  }
}
