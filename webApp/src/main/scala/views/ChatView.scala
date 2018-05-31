package wust.webApp.views

import fastparse.core.Parsed
import org.scalajs.dom.raw.Element
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.ids._
import wust.sdk.PostColor._
import wust.webApp._
import fontAwesome.{freeBrands, freeRegular, freeSolid}
import wust.webApp.outwatchHelpers._
import wust.webApp.parsers.PostContentParser
import wust.webApp.views.Elements._
import wust.webApp.views.Rendered._
import wust.util._

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
      padding := "5px 10px",
      pageParentPosts.map(_.map { parent =>
        div(
          display.flex,
          alignItems.center,
          Avatar.post(parent.id)(
            width := "40px",
            height := "40px",
            marginRight := "10px"
          ),
          showPostContent(parent.content)(fontSize := "20px"),

          state.user.map { user =>
            if (user.channelPostId == parent.id) Seq.empty
            else Seq(
              channelControl(state, parent)(ctx)(marginLeft := "5px"),
              joinControl(state, parent)(ctx)(marginLeft := "5px"),
              deleteButton(state, parent)(marginLeft := "5px"))
          }
        )
      })
    )
  }

  def channelControl(state: GlobalState, post: Post)(implicit ctx: Ctx.Owner): VNode = div(
    Rx {
      (state.rawGraph().children(state.user().channelPostId).contains(post.id) match {
        case true => freeSolid.faBookmark
        case false => freeRegular.faBookmark
      }):VNode //TODO: implicit for Rx[IconDefinition] ?
    },
    cursor.pointer,
    onClick --> sideEffect {_ =>
      val changes = state.rawGraph.now.children(state.user.now.channelPostId).contains(post.id) match {
        case true => GraphChanges.disconnect(post.id, ConnectionContent.Parent, state.user.now.channelPostId)
        case false => GraphChanges.connect(post.id, ConnectionContent.Parent, state.user.now.channelPostId)
      }
      state.eventProcessor.changes.onNext(changes)
    }
  )

  def joinControl(state:GlobalState, post:Post)(implicit  ctx: Ctx.Owner):VNode = {
    val text = post.joinDate match {
      case JoinDate.Always => span(freeSolid.faUserPlus:VNode)(title := "Users can join via URL (click to toggle)")
      case JoinDate.Never => span(freeSolid.faUserSlash:VNode)(title := "Private Group (click to toggle)")
      case JoinDate.Until(time) => span(s"Users can join via URL until $time") //TODO: epochmilli format
    }

    div(
      text,
      cursor.pointer,
      onClick --> sideEffect{ _ =>
        val newJoinDate = post.joinDate match {
          case JoinDate.Always => JoinDate.Never
          case JoinDate.Never => JoinDate.Always
          case _ => JoinDate.Never
        }

        Client.api.setJoinDate(post.id, newJoinDate)
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
        else posts.map(chatMessage(state, _, graph(), user()))
      },
      onPostPatch --> sideEffect[(Element, Element)] { case (_, elem) => scrollToBottom(elem) }
    )
  }

  def emptyMessage: VNode = h3(textAlign.center, "Nothing here yet.", paddingTop := "40%", color := "rgba(0,0,0,0.5)")

  def postTag(state:GlobalState, post:Post):VNode = {
    span(
      post.content.str, //TODO trim! fit for tag usage...
      onClick --> sideEffect{e => state.page() = Page(Seq(post.id)); e.stopPropagation()},
      backgroundColor := computeTagColor(post.id),
      fontSize.small,
      color := "#fefefe",
      borderRadius := "2px",
      padding := "0px 3px",
      marginRight := "3px"
    )
  }

  def deleteButton(state: GlobalState, post: Post) = div(
    freeRegular.faTrashAlt,
    padding := "5px",
    cursor.pointer,
    onClick.map{e => e.stopPropagation(); GraphChanges.delete(post)} --> ObserverSink(state.eventProcessor.changes)
  )

  def postLink(state: GlobalState, post: Post)(implicit ctx: Ctx.Owner) = state.viewConfig.map { cfg =>
    val newCfg = cfg.copy(page = Page(post.id))
    viewConfigLink(newCfg)(freeSolid.faLink)
  }

  def chatMessage(state:GlobalState, post: Post, graph:Graph, currentUser:User)(implicit ctx: Ctx.Owner): VNode = {
    val postTags: Seq[Post] = graph.ancestors(post.id).map(graph.postsById(_)).toSeq

    val isMine = currentUser.id == post.author
    val isDeleted = post.deleted < EpochMilli.now

    val content = showPostContent(post.content)(paddingRight := "10px")

    val tags = div( // post tags
      postTags.map{ tag => postTag(state, tag) },
      margin := "0px",
      padding := "0px"
    )

    div( // wrapper for floats
      div( // post wrapper
        div(
          display.flex,
          alignItems.center,
          content,
          isDeleted.ifFalseOption(postLink(state, post)),
          isDeleted.ifFalseOption(deleteButton(state, post))
        ),
        tags,

        isDeleted.ifTrueOption(opacity := 0.5),

        borderRadius := (if (isMine) "7px 0px 7px 7px" else "0px 7px 7px"),
        float := (if (isMine) "right" else "left"),
        borderWidth := (if (isMine) "1px 7px 1px 1px" else "1px 1px 1px 7px"),
        borderColor := computeColor(graph, post.id),
        backgroundColor := postDefaultColor,
        display.block,
        maxWidth := "80%",
        padding := "0px 10px",
        margin := "5px 0px",
        borderStyle := "solid",
        cursor.pointer // TODO: What about cursor when selecting text?
      ),
      width := "100%",
      clear.both
    )
  }

  def inputField(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val graphIsEmpty = Rx {
      state.displayGraphWithParents().graph.isEmpty && state.page().mode == PageMode.Default
    }

    textArea(
      valueWithEnter --> sideEffect { str =>
        val graph = state.displayGraphWithParents.now
        val user = state.user.now
        val changes = PostContentParser.newPost(graph.graph.posts.toSeq, user.id).parse(str) match {
          case Parsed.Success(changes, _) => changes
          case failure: Parsed.Failure[_,_] =>
            scribe.warn(s"Error parsing chat message '$str': ${failure.msg}. Will assume Markdown.")
            GraphChanges.addPost(Post(PostContent.Markdown(str), user.id))
        }

        state.eventProcessor.enriched.changes.onNext(changes)
      },
      disabled <-- graphIsEmpty,
      height := "3em",
      style("resize") := "vertical", //TODO: outwatch resize?
      Placeholders.newPost
    )
  }
}
