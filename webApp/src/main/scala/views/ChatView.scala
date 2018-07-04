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
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import dom.{Event, console}
import shopify.draggable._
import wust.webApp.outwatchHelpers._
import wust.webApp.parsers.NodeDataParser
import wust.webApp.views.Elements._
import wust.webApp.views.Rendered._
import wust.util._

import scala.scalajs.js
import scala.util.Success

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
  val chatHeaderTextColor = "grey"
  val chatHeaderTextSize = "0.8em"
  val chatMessageDateFormat = "yyyy-MM-dd HH:mm"

  override def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    div(
      display.flex,
      flexDirection.column,
      justifyContent.flexStart,
      alignItems.stretch,
      alignContent.stretch,
      minHeight := "0", // fixes overflow:scroll inside flexbox (https://stackoverflow.com/questions/28636832/firefox-overflow-y-not-working-with-nested-flexbox/28639686#28639686)
      minWidth := "0", // fixes full page scrolling when messages are too long
      chatHeader(state)(ctx)(flexGrow := 0, flexShrink := 0),
      chatHistory(state)(ctx)(height := "100%", overflow.auto),
      inputField(state)(ctx)(flexGrow := 0, flexShrink := 0)
    )
  }

  private def chatHeader(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    import state._
    div(
      padding := "5px 10px",
      pageParentNodes.map(_.map { parent =>
        div(
          display.flex,
          alignItems.center,
          Avatar.node(parent.id)(
            width := "40px",
            height := "40px",
            marginRight := "10px"
          ),
          renderNodeData(parent.data)(fontSize := "20px"),
          state.user.map { user =>
            if (user.channelNodeId == parent.id) Seq.empty
            else
              Seq(
                channelControl(state, parent)(ctx)(marginLeft := "5px"),
                joinControl(state, parent)(ctx)(marginLeft := "5px"),
                deleteButton(state, parent)(marginLeft := "5px")
              )
          }
        )
      }),
      overflowX.auto
    )
  }

  private def channelControl(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner): VNode = div(
    Rx {
      (state
        .graph()
        .children(state.user().channelNodeId)
        .contains(node.id) match {
        case true  => freeSolid.faBookmark
        case false => freeRegular.faBookmark
      }): VNode //TODO: implicit for Rx[IconDefinition] ?
    },
    cursor.pointer,
    onClick --> sideEffect { _ =>
      val changes = state.graph.now
        .children(state.user.now.channelNodeId)
        .contains(node.id) match {
        case true =>
          GraphChanges.disconnectParent(node.id, state.user.now.channelNodeId)
        case false =>
          GraphChanges.connectParent(node.id, state.user.now.channelNodeId)
      }
      state.eventProcessor.changes.onNext(changes)
    }
  )

  private def joinControl(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner): VNode = {
    val text = node.meta.joinDate match {
      case JoinDate.Always =>
        div(
          cls := "ui label",
          title := "Users can join via URL (click to toggle)",
          (freeSolid.faUserPlus: VNode)(cls := "icon"),
          span("Forever")
        )
      case JoinDate.Never =>
        span(freeSolid.faLock: VNode, title := "Private Group (click to toggle)")
        div(
          cls := "ui label",
          title := "Nobody can join (click to toggle)",
          (freeSolid.faLock: VNode)(cls := "icon"),
          span("Private")
        )
      case JoinDate.Until(time) =>
        div(
          cls := "ui label",
          title := "Users can join via URL (click to toggle)",
          (freeSolid.faUserPlus: VNode)(cls := "icon"),
          span(dateFns.formatDistance(new js.Date(time), new js.Date(js.Date.now()))) // js.Date.now() is UTC
        )
    }

    div(
      text,
      cursor.pointer,
      onClick --> sideEffect { _ =>
        val newJoinDate = node.meta.joinDate match {
          case JoinDate.Always => JoinDate.Never
          case JoinDate.Never  => JoinDate.Always
          case _               => JoinDate.Never
        }

        Client.api.setJoinDate(node.id, newJoinDate)
      }
    )
  }

  private def deleteButton(state: GlobalState, node: Node) = div(
    freeRegular.faTrashAlt,
    padding := "5px",
    cursor.pointer,
    onClick.map { e =>
      e.stopPropagation(); GraphChanges.delete(node)
    } --> ObserverSink(state.eventProcessor.changes)
  )

  //TODO: memoize?
  private def getNodeTags(graph: Graph, node: Node, page: Page): Seq[Node] = {
    (graph.ancestors(node.id) diff graph.channelNodeIds.toSeq diff page.parentIds)
      .map(graph.nodesById(_))
  }

  /** returns a Seq of ChatKind instances where similar successive nodes are grouped via ChatGroup */
  private def groupNodes(
      graph: Graph,
      nodes: Seq[Node],
      state: GlobalState,
      currentUserId: UserId
  ) = {
    def shouldGroup(nodes: Node*) = {
      grouping && // grouping enabled
      (nodes
        .map(getNodeTags(graph, _, state.page.now))
        .distinct
        .size == 1 // tags must match
      // all nodes either mine or not mine
      && (nodes.forall(node => graph.authorIds(node).contains(currentUserId))
      || nodes.forall(node => !graph.authorIds(node).contains(currentUserId))))
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

    val dragOverEvent = PublishSubject[DragOverEvent]
    val dragOutEvent = PublishSubject[DragOutEvent]
    val dragEvent = PublishSubject[DragEvent]
    val lastDragOverId = PublishSubject[Option[NodeId]]

    // TODO: Use js properties
    dragOverEvent.foreach { e =>
      //        val parentId: NodeId =  e.over.asInstanceOf[js.Dynamic].selectDynamic("woost_nodeid").asInstanceOf[NodeId]
      val parentId: NodeId =
        NodeId(Cuid.fromCuidString(e.over.attributes.getNamedItem("woost_nodeid").value))
      lastDragOverId.onNext(Some(parentId))
    }

    dragOutEvent.foreach { e =>
      lastDragOverId.onNext(None)
    }

    dragEvent
      .withLatestFrom(lastDragOverId)((e, lastOverId) => (e, lastOverId))
      .foreach {
        case (e, Some(parentId)) =>
          val childId: NodeId =
            NodeId(Cuid.fromCuidString(e.source.attributes.getNamedItem("woost_nodeid").value))
          if (parentId != childId) {
            val changes = GraphChanges.connectParent(childId, parentId)
            state.eventProcessor.enriched.changes.onNext(changes)
            lastDragOverId.onNext(None)
            console.log(s"Added GraphChange after drag: $changes")
          }
        case _ =>
      }

    div(
      padding := "20px",
      cls := "chatmsg-container",
      outline := "none", // hide outline when focused. This element is focusable because of the draggable library; TODO: don't make the whole container draggable?
      Rx {
        val nodes = graphContent().chronologicalNodesAscending.collect {
          case n: Node.Content => n
        }
        if (nodes.isEmpty) Seq(emptyMessage)
        else
          groupNodes(graph(), nodes, state, user().id)
            .map(chatMessage(state, _, graph(), user().id))
      },
      onPostPatch --> sideEffect[(Element, Element)] {
        case (_, elem) => scrollToBottom(elem)
      },
      onInsert.asHtml --> sideEffect { elem =>
        val d = new Draggable(elem, new Options { draggable = ".draggable" })
        d.on[DragOverEvent]("drag:over", e => {
          dragOverEvent.onNext(e)
        })
        d.on[DragOutEvent]("drag:out", dragOutEvent.onNext(_))
        d.on[DragEvent]("drag:stop", dragEvent.onNext(_))

      }
    )
  }

  private def emptyMessage: VNode =
    h3(textAlign.center, "Nothing here yet.", paddingTop := "40%", color := "rgba(0,0,0,0.5)")

  private def nodeTag(state: GlobalState, node: Node): VNode = {
    span(
      node.data.str, //TODO trim! fit for tag usage...
      onClick --> sideEffect { e =>
        state.page() = Page(Seq(node.id)); e.stopPropagation()
      },
      backgroundColor := computeTagColor(node.id),
      fontSize.small,
      color := "#fefefe",
      borderRadius := "2px",
      padding := "0px 3px",
      marginRight := "3px"
    )
  }

  private def nodeLink(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner) =
    state.viewConfig.map { cfg =>
      val newCfg = cfg.copy(page = Page(node.id))
      viewConfigLink(newCfg)(freeSolid.faLink)
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
              margin := "5px",
              width := size.value,
            )
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
        marginLeft := "8px",
        fontSize.smaller,
        color.gray
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
        .fold(span())(author => span(author.name, fontWeight.bold))
    else span()
  }

  private def chatMessage(state: GlobalState, chat: ChatKind, graph: Graph, currentUser: UserId)(
      implicit ctx: Ctx.Owner
  ): VNode = {
    chat match {
      case ChatSingle(node) =>
        chatMessageRenderer(state, Seq(node), graph, currentUser)
      case ChatGroup(node) =>
        chatMessageRenderer(state, node, graph, currentUser)
    }
  }

  private def styles(color: String) = Seq[VDomModifier](
    cls := "chatmsg",
    borderColor := color,
  )

  private def chatMessageRenderer(
      state: GlobalState,
      nodes: Seq[Node],
      graph: Graph,
      currentUser: UserId
  )(implicit ctx: Ctx.Owner): VNode = {

    val currNode = nodes.last
    val headNode = nodes.head
    val isMine = graph.authors(currNode).contains(currentUser)
    val isDeleted = currNode.meta.deleted.timestamp < EpochMilli.now //TODO: Handle group msgs when all msg are deleted

    div( // node wrapper
      avatarDiv(isMine, graph.authorIds(headNode).headOption, avatarSize),
      div(
        chatMessageHeader(isMine, headNode, graph, avatarSize),
        nodes.map(chatMessageBody(state, graph, _)),
        tagsDiv(state, graph, currNode),
        styles(computeColor(graph, currNode.id)),
        isDeleted.ifTrueOption(opacity := 0.5)
      ),
      display.flex,
      flexDirection.row,
      justifyContent.flexStart,
      alignSelf.flexStart,
      alignItems.flexStart,
      alignContent.flexStart
    )

  }

  /// @return a vnode containing a chat header with optional name, date and avatar
  private def chatMessageHeader(
      isMine: Boolean,
      node: Node,
      graph: Graph,
      avatarSize: AvatarSize
  ) = {
    Seq[VDomModifier](
      optAuthorDiv(isMine, node, graph),
      optDateDiv(isMine, node, graph),
      color := chatHeaderTextColor,
      fontSize := chatHeaderTextSize,
    )
  }

  /// @return the actual body of a chat message
  /** Should be styled in such a way as to be repeatable so we can use this in groups */
  private def chatMessageBody(state: GlobalState, graph: Graph, node: Node)(
      implicit ctx: Ctx.Owner
  ) = {
    val isDeleted = node.meta.deleted.timestamp < EpochMilli.now
    val content =
      if (graph.children(node).isEmpty)
        renderNodeData(node.data)
      else nodeTag(state, node)
    div(
      display.flex,
      alignItems.center,
      // FIXME: If the content has long lines, the layout gets broken on devices that can not fit the entire
      // message. Potential fix: add max-width: XXpx & text-overflow: ellipsis. max-width: XX% does not work.
      div(
        content,
        attr("woost_nodeid") := node.id.toCuidString,
        cls := "draggable",
        overflowX.auto, // show scrollbar for very long messages
        flexGrow := 1
      ),
      div(
        display.flex,
        alignItems.center,
        flexGrow := 0,
        marginLeft := "10px",
        isDeleted.ifFalseOption(nodeLink(state, node)),
        isDeleted.ifFalseOption(deleteButton(state, node))
      )
    )
  }

  private def tagsDiv(state: GlobalState, graph: Graph, node: Node)(implicit ctx: Ctx.Owner) = {
    val nodeTags: Seq[Node] = getNodeTags(graph, node, state.page.now)

    div( // node tags
      nodeTags.map { tag =>
        nodeTag(state, tag)
      },
      margin := "2px",
      padding := "4px"
    )
  }

  private def inputField(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val graphIsEmpty = Rx {
      state.graphContent().isEmpty && state.page().mode == PageMode.Default
    }

    textArea(
      valueWithEnter --> sideEffect { str =>
        val graph = state.graphContent.now
        val user = state.user.now
        val changes = NodeDataParser
          .newNode(contextNodes = graph.nodes.toSeq, author = user.id)
          .parse(str) match {
          case Parsed.Success(changes, _) => changes
          case failure: Parsed.Failure[_, _] =>
            scribe.warn(s"Error parsing chat message '$str': ${failure.msg}. Will assume Markdown.")
            GraphChanges.addNode(NodeData.Markdown(str))
        }

        state.eventProcessor.enriched.changes.onNext(changes)
      },
      disabled <-- graphIsEmpty,
      height := "3em",
      style("resize") := "vertical", //TODO: outwatch resize?
      Placeholders.newNode
    )
  }
}
