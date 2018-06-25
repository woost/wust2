package wust.webApp.views

import fastparse.core.Parsed
import org.scalajs.dom.raw.Element
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.webApp._
import fontAwesome.{freeBrands, freeRegular, freeSolid}
import wust.webApp.outwatchHelpers._
import wust.webApp.parsers.PostDataParser
import wust.webApp.views.Elements._
import wust.webApp.views.Rendered._
import wust.util._

import scala.scalajs.js
import scala.util.Success

object ChatView extends View {
  override val key = "chat"
  override val displayName = "Chat"

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {

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
      pageParentNodes.map(_.map { parent =>
        div(
          display.flex,
          alignItems.center,
          Avatar.post(parent.id)(
            width := "40px",
            height := "40px",
            marginRight := "10px"
          ),
          showPostData(parent.data)(fontSize := "20px"),

          state.user.map { user =>
            if (user.channelNodeId == parent.id) Seq.empty
            else Seq(
              channelControl(state, parent)(ctx)(marginLeft := "5px"),
              joinControl(state, parent)(ctx)(marginLeft := "5px"),
              deleteButton(state, parent)(marginLeft := "5px"))
          }
        )
      })
    )
  }

  def channelControl(state: GlobalState, post: Node)(implicit ctx: Ctx.Owner): VNode = div(
    Rx {
      (state.graph().children(state.user().channelNodeId).contains(post.id) match {
        case true => freeSolid.faBookmark
        case false => freeRegular.faBookmark
      }):VNode //TODO: implicit for Rx[IconDefinition] ?
    },
    cursor.pointer,
    onClick --> sideEffect {_ =>
      val changes = state.graph.now.children(state.user.now.channelNodeId).contains(post.id) match {
        case true => GraphChanges.disconnectParent(post.id, state.user.now.channelNodeId)
        case false => GraphChanges.connectParent(post.id, state.user.now.channelNodeId)
      }
      state.eventProcessor.changes.onNext(changes)
    }
  )

  def joinControl(state:GlobalState, post:Node)(implicit ctx: Ctx.Owner):VNode = {
    val text = post.meta.joinDate match {
      case JoinDate.Always =>
        div(
          cls := "ui label",title := "Users can join via URL (click to toggle)",
          (freeSolid.faUserPlus:VNode)(cls := "icon"),
          span("Forever")
        )
      case JoinDate.Never =>
        span(freeSolid.faLock:VNode, title := "Private Group (click to toggle)")
        div(
          cls := "ui label", title := "Nobody can join (click to toggle)",
          (freeSolid.faLock:VNode)(cls := "icon"),
          span("Private")
        )
      case JoinDate.Until(time) =>
        div(
          cls := "ui label", title := "Users can join via URL (click to toggle)",
          (freeSolid.faUserPlus:VNode)(cls := "icon"),
          span(dateFns.formatDistance(new js.Date(time), new js.Date(js.Date.now()))) // js.Date.now() is UTC
        )
    }

    div(
      text,
      cursor.pointer,
      onClick --> sideEffect{ _ =>
        val newJoinDate = post.meta.joinDate match {
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
    val graph = state.graph

    div(
      padding := "20px",

      Rx{
        val posts = graphContent().chronologicalNodesAscending.collect{ case n:Node.Content => n}
        if (posts.isEmpty) Seq(emptyMessage)
        else posts.map(chatMessage(state, _, graph(), user()))
      },
      onPostPatch --> sideEffect[(Element, Element)] { case (_, elem) => scrollToBottom(elem) }
    )
  }

  def emptyMessage: VNode = h3(textAlign.center, "Nothing here yet.", paddingTop := "40%", color := "rgba(0,0,0,0.5)")

  def postTag(state:GlobalState, post:Node):VNode = {
    span(
      post.data.str, //TODO trim! fit for tag usage...
      onClick --> sideEffect{e => state.page() = Page(Seq(post.id)); e.stopPropagation()},
      backgroundColor := computeTagColor(post.id),
      fontSize.small,
      color := "#fefefe",
      borderRadius := "2px",
      padding := "0px 3px",
      marginRight := "3px"
    )
  }

  def deleteButton(state: GlobalState, post: Node) = div(
    freeRegular.faTrashAlt,
    padding := "5px",
    cursor.pointer,
    onClick.map{e => e.stopPropagation(); GraphChanges.delete(post)} --> ObserverSink(state.eventProcessor.changes)
  )

  def postLink(state: GlobalState, post: Node)(implicit ctx: Ctx.Owner) = state.viewConfig.map { cfg =>
    val newCfg = cfg.copy(page = Page(post.id))
    viewConfigLink(newCfg)(freeSolid.faLink)
  }

  def chatMessage(state:GlobalState, post: Node, graph:Graph, currentUser: UserInfo)(implicit ctx: Ctx.Owner): VNode = {
    val postTags: Seq[Node] = graph.ancestors(post.id).map(graph.nodesById(_)).toSeq

    val isMine = false //currentUser.id == post.author TODO Authorship
    val isDeleted = post.meta.deleted.timestamp < EpochMilli.now

    val content = if (graph.children(post).isEmpty) showPostData(post.data)(paddingRight := "10px") else postTag(state, post)

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
      state.graphContent().isEmpty && state.page().mode == PageMode.Default
    }

    textArea(
      valueWithEnter --> sideEffect { str =>
        val graph = state.graphContent.now
        val user = state.user.now
        val changes = PostDataParser.newPost(contextPosts = graph.nodes.toSeq, author = user.id).parse(str) match {
          case Parsed.Success(changes, _) => changes
          case failure: Parsed.Failure[_,_] =>
            scribe.warn(s"Error parsing chat message '$str': ${failure.msg}. Will assume Markdown.")
            GraphChanges.addNode(NodeData.Markdown(str))
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
