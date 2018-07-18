package wust.webApp.views

import fastparse.core.Parsed
import fontAwesome._
import monix.reactive.subjects.PublishSubject
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.util._
import wust.util.collection._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.parsers.NodeDataParser
import wust.webApp.views.Elements._

import scala.collection.breakOut

/** hide size behind a semantic */
class AvatarSize(val value: String)
object AvatarSize {
  case object Small extends AvatarSize("10px")
  case object Normal extends AvatarSize("20px")
  case object Large extends AvatarSize("40px")
}

/** when to show something */
class ShowOpts
object ShowOpts {
  case object Never extends ShowOpts
  case object OtherOnly extends ShowOpts
  case object OwnOnly extends ShowOpts
  case object Always extends ShowOpts
}

sealed trait ChatKind
case class ChatSingle(node: Node) extends ChatKind
case class ChatGroup(nodes: Seq[Node]) extends ChatKind

object ChatView extends View {
  override val key = "chat"
  override val displayName = "Chat"

  // -- display options --
  val showAvatar: ShowOpts = ShowOpts.OtherOnly
  val showAuthor: ShowOpts = ShowOpts.OtherOnly
  val showDate: ShowOpts = ShowOpts.Always
  val grouping = true
  val avatarSize = AvatarSize.Large
  val avatarBorder = true
  val chatMessageDateFormat = "yyyy-MM-dd HH:mm"

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    div(
      Styles.flex,
      flexDirection.column,
      alignItems.stretch,
      alignContent.stretch,
      chatHistory(state)(ctx)(
        height := "100%",
        overflow.auto,
        backgroundColor <-- state.pageStyle.bgLightColor
      ),
      inputField(state)(ctx)(Styles.flexStatic)
    )
  }

  private def deleteButton(state: GlobalState, node: Node, graph: Graph, page: Page) = div(
    paddingLeft := "3px",
    freeRegular.faTrashAlt,
    cursor.pointer,
    onClick.map { e =>
      e.stopPropagation()
      GraphChanges.delete(node, graph.parents(node).toSet intersect page.parentIdSet)
    } --> ObserverSink(state.eventProcessor.changes)
  )

  /** returns a Seq of ChatKind instances where similar successive nodes are grouped via ChatGroup */
  private def groupNodes(
      graph: Graph,
      nodes: Seq[Node],
      state: GlobalState,
      currentUserId: UserId
  ) = {
    def shouldGroup(nodes: Node*) = {
      grouping && // grouping enabled
      // && (nodes
      //   .map(getNodeTags(graph, _, state.page.now)) // getNodeTags returns a sequence
      //   .distinct
      //   .size == 1) // tags must match
      // (nodes.forall(node => graph.authorIds(node).contains(currentUserId)) || // all nodes either mine or not mine
      // nodes.forall(node => !graph.authorIds(node).contains(currentUserId)))
      graph.authorIds(nodes.head).headOption.fold(false) { authorId =>
        nodes.forall(node => graph.authorIds(node).head == authorId)
      }
      // TODO: within a specific timespan && nodes.last.
    }

    nodes.foldLeft(Seq[ChatKind]()) { (kinds, node) =>
      kinds.lastOption match {
        case Some(ChatSingle(lastNode)) =>
          if (shouldGroup(lastNode, node))
            kinds.dropRight(1) :+ ChatGroup(Seq[Node](lastNode, node))
          else
            kinds :+ ChatSingle(node)

        case Some(ChatGroup(lastNodes)) =>
          if (shouldGroup(lastNodes.last, node))
            kinds.dropRight(1) :+ ChatGroup(nodes = lastNodes :+ node)
          else
            kinds :+ ChatSingle(node)

        case None => kinds :+ ChatSingle(node)
      }
    }
  }

  def chatHistory(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    import state._
    val graph = state.graph

    val scrolledToBottom = PublishSubject[Boolean]

    div(
      cls := "chathistory",
      padding := "20px 0 20px 20px",
      outline := "none", // hide outline when focused. This element is focusable because of the draggable library; TODO: don't make the whole container draggable?
      Rx {
        val nodes = graphContent().chronologicalNodesAscending.collect {
          case n: Node.Content => n
        }
        if (nodes.isEmpty) Seq(emptyChatNotice)
        else
          groupNodes(graph(), nodes, state, user().id)
            .map(chatMessage(state, _, graph(), page(), user().id))
      },
      onUpdate --> sideEffect { (prev, _) =>
        scrolledToBottom
          .onNext(prev.scrollHeight - prev.clientHeight <= prev.scrollTop + 11) // at bottom + 10 px tolerance
      },
      onPostPatch.transform(_.withLatestFrom(scrolledToBottom) {
        case ((_, elem), atBottom) => (elem, atBottom)
      }) --> sideEffect { (elem, atBottom) =>
        if (atBottom) scrollToBottom(elem)
      }
    )
  }

  private def emptyChatNotice: VNode =
    h3(textAlign.center, "Nothing here yet.", paddingTop := "40%", color := "rgba(0,0,0,0.5)")

  private def nodeLink(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner) =
    state.viewConfig.map { cfg =>
      val newCfg = cfg.copy(page = Page(node.id))
      div(onClick(newCfg) --> state.viewConfig, cursor.pointer)(freeSolid.faExternalLinkAlt)
    }

  /// @return an avatar vnode or empty depending on the showAvatar setting
  private def avatarDiv(isOwn: Boolean, user: Option[UserId], size: AvatarSize) = {
    if (showAvatar == ShowOpts.Always
        || (showAvatar == ShowOpts.OtherOnly && !isOwn)
        || (showAvatar == ShowOpts.OwnOnly && isOwn)) {
      user.fold(div())(
        user =>
          div(
            Avatar.user(user)(
              width := size.value,
            ),
            cls := "chatmsg-avatar"
          )
      )
    } else div()
  }

  /// @return a date vnode or empty based on showDate setting
  private def optDateDiv(isOwn: Boolean, node: Node, graph: Graph) = {
    if (showDate == ShowOpts.Always
        || (showDate == ShowOpts.OtherOnly && !isOwn)
        || (showDate == ShowOpts.OwnOnly && isOwn))
      span(
        (new java.text.SimpleDateFormat(chatMessageDateFormat))
          .format(new java.util.Date(graph.nodeCreated(node))),
        cls := "chatmsg-date"
      )
    else
      span()
  }

  /// @return an author vnode or empty based on showAuthor setting
  private def optAuthorDiv(isOwn: Boolean, node: Node, graph: Graph) = {
    if (showAuthor == ShowOpts.Always
        || (showAuthor == ShowOpts.OtherOnly && !isOwn)
        || (showAuthor == ShowOpts.OwnOnly && isOwn))
      graph
        .authors(node)
        .headOption
        .fold(span())(author => span(author.name, cls := "chatmsg-author"))
    else span()
  }

  private def chatMessage(
      state: GlobalState,
      chat: ChatKind,
      graph: Graph,
      page: Page,
      currentUser: UserId
  )(
      implicit ctx: Ctx.Owner
  ): VNode = {
    chat match {
      case ChatSingle(node) =>
        chatMessageRenderer(state, Seq(node), graph, page, currentUser)
      case ChatGroup(nodes) =>
        chatMessageRenderer(state, nodes, graph, page, currentUser)
    }
  }

  private def chatMessageRenderer(
      state: GlobalState,
      nodes: Seq[Node],
      graph: Graph,
      page: Page,
      currentUser: UserId
  )(implicit ctx: Ctx.Owner): VNode = {

    val currNode = nodes.last
    val headNode = nodes.head
    val isMine = graph.authors(currNode).contains(currentUser)

    div( // node wrapper
      avatarDiv(isMine, graph.authorIds(headNode).headOption, avatarSize),
      div(
        chatMessageHeader(isMine, headNode, graph, avatarSize),
        nodes.map(chatMessageLine(state, graph, page, _)),
        borderColor := computeColor(graph, currNode.id),
        cls := "chatmsg-group-inner-frame",
      ),
      Styles.flex,
    )
  }

  /// @return a vnode containing a chat header with optional name, date and avatar
  private def chatMessageHeader(
      isMine: Boolean,
      node: Node,
      graph: Graph,
      avatarSize: AvatarSize
  ) = {
    div(
      optAuthorDiv(isMine, node, graph),
      optDateDiv(isMine, node, graph),
      cls := "chatmsg-header"
    )
  }

  /// @return the actual body of a chat message
  /** Should be styled in such a way as to be repeatable so we can use this in groups */
  private def chatMessageLine(state: GlobalState, graph: Graph, page: Page, node: Node)(
      implicit ctx: Ctx.Owner
  ) = {
    val isDeleted = graph.isDeletedNow(node.id, page.parentIdSet)
    val isSelected = state.selectedNodeIds.map(_ contains node.id)
    // if (graph.children(node).isEmpty)
    //   renderNodeData(node.data)
    // else nodeTag(state, node)(ctx)(fontSize := "14px")

    val checkbox = div(
      cls := "ui checkbox fitted",
      isSelected.map(_.ifTrueOption(visibility.visible)),
      input(
        tpe := "checkbox",
        checked <-- isSelected,
        onChange.checked --> sideEffect { checked =>
          if (checked) state.selectedNodeIds.update(_ + node.id)
          else state.selectedNodeIds.update(_ - node.id)
        }
      ),
      label()
    )

    val msgControls = div(
      cls := "chatmsg-controls",
      isDeleted.ifFalseOption(nodeLink(state, node)),
      isDeleted.ifFalseOption(deleteButton(state, node, graph, page)),
    )


    div(
      isSelected.map(_.ifTrueOption(backgroundColor := "rgba(65,184,255, 0.5)")),
      div(
        cls := "chatmsg-line",
        Styles.flex,
        isDeleted.ifTrueOption(opacity := 0.5),
        onClick --> sideEffect { state.selectedNodeIds.update(_.toggle(node.id)) },

        checkbox(Styles.flexStatic),
        nodeCardCompact(state, node)(ctx)(isDeleted.ifTrueOption(cls := "node-deleted")),
        isDeleted.ifFalseOption(messageTags(state, graph, node)),
        msgControls(Styles.flexStatic)
      )
    )
  }

  private def messageTags(state: GlobalState, graph: Graph, node: Node)(implicit ctx: Ctx.Owner) = {
    val directNodeTags = graph.directNodeTags((node.id, state.page.now))
    val transitiveNodeTags = graph.transitiveNodeTags((node.id, state.page.now))

    div(
      cls := "tags",
      directNodeTags.map { tag =>
        removableNodeTag(state, tag, node.id, graph)
      }(breakOut): Seq[VDomModifier],
      transitiveNodeTags.map { tag =>
        nodeTag(state, tag)(opacity := 0.4)
      }(breakOut): Seq[VDomModifier]
    )
  }

  private def inputField(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val disableUserInput = Rx {
      val graphNotLoaded = (state.graph().nodeIds intersect state.page().parentIds.toSet).isEmpty
      val pageModeOrphans = state.page().mode == PageMode.Orphans
      graphNotLoaded || pageModeOrphans
    }

    div(
      cls := "ui form",
      textArea(
        cls := "field",
        valueWithEnter --> sideEffect { str =>
          val graph = state.graphContent.now
          val user = state.user.now
          val changes = NodeDataParser
            .newNode(contextNodes = graph.nodes.toSeq, author = user.id)
            .parse(str) match {
            case Parsed.Success(changes, _) => changes
            case failure: Parsed.Failure[_, _] =>
              scribe.warn(
                s"Error parsing chat message '$str': ${failure.msg}. Will assume Markdown."
              )
              GraphChanges.addNode(NodeData.Markdown(str))
          }

          state.eventProcessor.enriched.changes.onNext(changes)
        },
        disabled <-- disableUserInput,
        rows := 1, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
        style("resize") := "none", //TODO: add resize style to scala-dom-types
        Placeholders.newNode
      )
    )
  }
}
