package wust.webApp.views

import org.scalajs.dom.raw.Element
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.ids.JoinDate
import wust.sdk.PostColor._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Elements._
import wust.webApp.views.Rendered._

object ChatView extends View {
  override val key = "chat"
  override val displayName = "Chat"

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {

    state.displayGraphWithoutParents.foreach(dg => scribe.info(s"ChatView Graph: ${dg.graph}"))

    div(
      display.flex,
      flexDirection.column,
      justifyContent.flexStart,
      alignItems.stretch,
      alignContent.stretch,

      chatHeader(state)(ctx)(flexGrow := 0, flexShrink := 0),
      chatHistory(state)(ctx)(height := "100%", overflow.auto),
      inputField(state)(ctx)(flexGrow := 0, flexShrink := 0)
    )
  }

  def chatHeader(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    import state._
    div(
      padding := "0px 10px",
      pageParentPosts.map(_.map { parent =>
        div(
          display.flex,
          alignItems.center,
          Avatar.post(parent.id)(
            width := "40px",
            height := "40px",
            marginRight := "10px"
          ),
          mdHtml(parent.content)(fontSize := "20px"),
          joinControl(state, parent)(ctx)(marginLeft := "5px")
        )
      })
    )
  }

  def joinControl(state:GlobalState, post:Post)(implicit  ctx: Ctx.Owner):VNode = {
    div(
      "(", post.joinDate.toString, ")",
      cursor.pointer,
      onClick --> sideEffect{ _ =>
        val newJoinDate = post.joinDate match {
          case JoinDate.Always => JoinDate.Never
          case JoinDate.Never => JoinDate.Always
          case _ => JoinDate.Never
        }

        Client.api.setJoinDate(post.id, newJoinDate).foreach{ success =>
          //TODO: this should be automatic, highlevel posts should be in graph
          if(success)
            state.highLevelPosts() = state.highLevelPosts.now.map{p =>
              if(p.id == post.id) p.copy(joinDate = newJoinDate) else p
            }
        }
      }
    )
  }

  def chatHistory(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    import state._
    val graph = displayGraphWithoutParents.map(_.graph)

    div(
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
        div( // post content as markdown
          mdHtml(post.content),
          cls := "chatpost",
          padding := "0px 3px",
          margin := "2px 0px"
        ),
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

  def inputField(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    import state._

    val newPostSink = ObserverSink(eventProcessor.enriched.changes).redirect { (o: Observable[String]) =>
      o.withLatestFrom(state.currentUser.toObservable)((msg, user) => GraphChanges.addPost(msg, user.id))
    }

    val graphIsEmpty = displayGraphWithParents.map(_.graph.isEmpty)

    textArea(
      valueWithEnter --> newPostSink,
      disabled <-- graphIsEmpty,
      height := "3em",
      style("resize") := "vertical", //TODO: outwatch resize?
      Placeholders.newPost
    )
  }
}
