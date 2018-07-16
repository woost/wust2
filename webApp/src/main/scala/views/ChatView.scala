package wust.webApp.views

import fastparse.core.Parsed
import org.scalajs.dom.raw.Element
import wust.css.Styles
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.webApp._
import fontAwesome._
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

case class PermissionSelection(
    access: NodeAccess,
    value: String,
    name: (NodeId, Graph) => String,
    description: String,
    icon: IconLookup
)
object PermissionSelection {
  val all =
    PermissionSelection(
      access = NodeAccess.Inherited,
      name = { (nodeId, graph) =>
        val canAccess = graph
          .parents(nodeId)
          .exists(nid => graph.nodesById(nid).meta.accessLevel == NodeAccess.ReadWrite)
        console.log(graph.parents(nodeId).map(nid => graph.nodesById(nid)).mkString(", "))
        val inheritedLevel = if (canAccess) "Public" else "Private"
        s"Inherited ($inheritedLevel)"
      },
      value = "Inherited",
      description = "The permissions for this Node are inherited from its parents",
      icon = freeSolid.faArrowUp
    ) ::
      PermissionSelection(
        access = NodeAccess.Level(AccessLevel.ReadWrite),
        name = (_, _) => "Public",
        value = "Public",
        description = "Anyone can access this Node via the URL",
        icon = freeSolid.faUserPlus
      ) ::
      PermissionSelection(
        access = NodeAccess.Level(AccessLevel.Restricted),
        name = (_, _) => "Private",
        value = "Private",
        description = "Only you and explicit members can access this Node",
        icon = freeSolid.faLock
      ) ::
      Nil
}

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
      justifyContent.flexStart,
      alignItems.stretch,
      alignContent.stretch,
      chatHeader(state)(ctx)(flexGrow := 0, flexShrink := 0),
      chatHistory(state)(ctx)(
        height := "100%",
        overflow.auto,
        backgroundColor <-- state.pageStyle.bgLightColor
      ),
      inputField(state)(ctx)(flexGrow := 0, flexShrink := 0)
    )
  }

  private def chatHeader(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    import state._
    div(
      padding := "5px 10px",
      pageParentNodes.map(_.map { parent =>
        div(
          Styles.flex,
          alignItems.center,
          Avatar.node(parent.id)(
            width := "40px",
            height := "40px",
            marginRight := "10px"
          ),
          editableNode(state, parent, renderNodeData(parent.data)(fontSize := "20px")),
          state.user.map { user =>
            if (user.channelNodeId == parent.id) Seq.empty
            else
              Seq[VDomModifier](
                bookMarkControl(state, parent)(ctx)(margin := "0px 10px"),
                joinControl(state, parent)(ctx)(marginLeft := "auto"),
              )
          }
        )
      }),
      overflowX.auto
    )
  }

  private def bookMarkControl(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner): VNode = div(
    Rx {
      (state
        .graph()
        .children(state.user().channelNodeId)
        .contains(node.id) match {
        case true  => freeSolid.faBookmark
        case false => freeRegular.faBookmark
      }): VNode //TODO: implicit for Rx[IconDefinition] ?
    },
    fontSize := "20px",
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

  private def joinControl(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VNode = {
    select(
      cls := "ui dropdown selection",
      onChange.value.map { value =>
        val newAccess = PermissionSelection.all.find(_.value == value).get.access
        val nextNode = channel match {
          case n: Node.Content => n.copy(meta = n.meta.copy(accessLevel = newAccess))
          case _               => ??? //FIXME
        }
        GraphChanges.addNode(nextNode)
      } --> state.eventProcessor.changes,
      PermissionSelection.all.map { selection =>
        option(
          (channel.meta.accessLevel == selection.access).ifTrueOption(selected := true),
          value := selection.value,
          renderFontAwesomeIcon(selection.icon)(cls := "icon"),
          Rx {
            selection.name(channel.id, state.graph()) //TODO: report Scala.Rx bug, where two reactive variables in one function call give a compile error: selection.name(state.user().id, node.id, state.graph())
          }
        )
      }
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

    val dragOverEvent = PublishSubject[DragOverEvent]
    val dragOutEvent = PublishSubject[DragOutEvent]
    val dragEvent = PublishSubject[DragEvent]
    val lastDragOverId = PublishSubject[Option[NodeId]]
    val scrolledToBottom = PublishSubject[Boolean]

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
      padding := "20px 0 20px 20px",
      outline := "none", // hide outline when focused. This element is focusable because of the draggable library; TODO: don't make the whole container draggable?
      Rx {
        val nodes = graphContent().chronologicalNodesAscending.collect {
          case n: Node.Content => n
        }
        if (nodes.isEmpty) Seq(emptyMessage)
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
        nodes.map(chatMessageBody(state, graph, page, _)),
        borderColor := computeColor(graph, currNode.id),
        cls := "chatmsg-inner-frame",
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
  private def chatMessageBody(state: GlobalState, graph: Graph, page: Page, node: Node)(
      implicit ctx: Ctx.Owner
  ) = {
    val isDeleted = graph.isDeletedNow(node.id, page.parentIdSet)
    val content = renderNodeData(node.data)
    // if (graph.children(node).isEmpty)
    //   renderNodeData(node.data)
    // else nodeTag(state, node)(ctx)(fontSize := "14px")

    val msgControls = div(
      cls := "chatmsg-controls",
      isDeleted.ifFalseOption(nodeLink(state, node)),
      isDeleted.ifFalseOption(deleteButton(state, node, graph, page)),
    )

    div(
      Styles.flex,
      cls := "chatmsg-body",
      isDeleted.ifTrueOption(opacity := 0.5),
      div(
        div(
          editableNode(state, node, div(content)),
          attr("woost_nodeid") := node.id.toCuidString,
          cls := "draggable",
          cls := "chatmsg-content",
          isDeleted.ifTrueOption(cls := "chatmsg-deleted")
        ),
        cls := "hard-shadow",
        cls := "chatmsg-card",
      ),
      isDeleted.ifFalseOption(tagsDiv(state, graph, node)),
      msgControls,
    )
  }

  private def tagsDiv(state: GlobalState, graph: Graph, node: Node)(implicit ctx: Ctx.Owner) = {
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
