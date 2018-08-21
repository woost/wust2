package wust.webApp.views

import fontAwesome._
import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.util._
import wust.util.collection._
import wust.webApp.dragdrop.DragItem
import wust.webApp.jsdom.dateFns
import wust.webApp.outwatchHelpers._
import wust.webApp.parsers.NodeDataParser
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

import scala.collection.breakOut
import scala.scalajs.js

/** hide size behind a semantic */
sealed trait AvatarSize {
  def width: String
  def padding: String
}
object AvatarSize {
  case object Small extends AvatarSize {
    def width = "15px"
    def padding = "2px"
  }
  case object Normal extends AvatarSize {
    def width = "20px"
    def padding = "3px"
  }
  case object Large extends AvatarSize {
    def width = "40px"
    def padding = "3px"
  }
}

/** when to show something */
sealed trait ShowOpts
object ShowOpts {
  case object Never extends ShowOpts
  case object OtherOnly extends ShowOpts
  case object OwnOnly extends ShowOpts
  case object Always extends ShowOpts
}

sealed trait ChatKind {
  def nodeIds: Seq[NodeId]
}
object ChatKind {
  case class Single(nodeId: NodeId) extends ChatKind {def nodeIds = nodeId :: Nil }
  case class Group(nodeIds: Seq[NodeId]) extends ChatKind
}

object ChatView {
  // -- display options --
  val showAvatar: ShowOpts = ShowOpts.OtherOnly
  val showAuthor: ShowOpts = ShowOpts.OtherOnly
  val showDate: ShowOpts = ShowOpts.Always
  val grouping = true
  val avatarSizeThread = AvatarSize.Small
  val avatarBorder = true
  val chatMessageDateFormat = "yyyy-MM-dd HH:mm"

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Styles.flex,
      flexDirection.column,
      alignItems.stretch,
      alignContent.stretch,
      height := "100%",
      div(
        Styles.flex,
        flexDirection.row,
        height := "100%",
        position.relative,
        chatHistory(state).apply(
          height := "100%",
          width := "100%",
          overflow.auto,
          backgroundColor <-- state.pageStyle.map(_.bgLightColor),
        ),
        //        TagsList(state).apply(Styles.flexStatic)
      ),
      Rx { inputField(state, state.page().parentIdSet).apply(keyed, Styles.flexStatic, padding := "3px") },
      registerDraggableContainer(state),
    )
  }

  def chatHistory(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val scrolledToBottom = PublishSubject[Boolean]
    val activeReplyFields = Var(Set.empty[List[NodeId]])
    val avatarSizeToplevel:Rx[AvatarSize] = Rx { if(state.screenSize() == ScreenSize.Small) AvatarSize.Small else AvatarSize.Large }


    div(
      // this wrapping of chat history is currently needed,
      // to allow dragging the scrollbar without triggering a drag event.
      // see https://github.com/Shopify/draggable/issues/262
      div(
        cls := "chat-history",
        padding := "20px 0 20px 20px",
        Rx {
          val page = state.page()
          val fullGraph = state.graph()
          val graph = state.graphContent()
          val user = state.user()
          val nodes = graph.chronologicalNodesAscending.collect {
            case n: Node.Content if fullGraph.isChildOfAny(n.id, page.parentIds) || fullGraph.isDeletedChildOfAny(n.id, page.parentIds) => n.id
          }
          if(nodes.isEmpty) VDomModifier(emptyChatNotice)
          else
            VDomModifier(
              groupNodes(graph, nodes, state, user.id)
                .map(kind => renderGroupedMessages(state, kind.nodeIds, graph, page.parentIdSet, Nil, page.parentIdSet, user.id, avatarSizeToplevel, activeReplyFields)),


              draggableAs(state, DragItem.DisableDrag),
              cursor.default, // draggable sets cursor.move, but drag is disabled on page background
              dragTarget(DragItem.Chat.Page(page.parentIds)),
              keyed
            )
        },
        onUpdate --> sideEffect { (prev, _) =>
          scrolledToBottom
            .onNext(prev.scrollHeight - prev.clientHeight <= prev.scrollTop + 11) // at bottom + 10 px tolerance
        },
        onPostPatch.transform(_.withLatestFrom(scrolledToBottom) {
          case ((_, elem), atBottom) => (elem, atBottom)
        }) --> sideEffect { (elem, atBottom) =>
          if(atBottom) scrollToBottom(elem)
        }
      )
    )
  }

  private def emptyChatNotice: VNode =
    h3(textAlign.center, "Nothing here yet.", paddingTop := "40%", color := "rgba(0,0,0,0.5)")

  /** returns a Seq of ChatKind instances where similar successive nodes are grouped via ChatKind.Group */
  private def groupNodes(
    graph: Graph,
    nodes: Seq[NodeId],
    state: GlobalState,
    currentUserId: UserId
  ) = {
    def shouldGroup(nodes: NodeId*) = {
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
        case Some(ChatKind.Single(lastNode)) =>
          if(shouldGroup(lastNode, node))
            kinds.dropRight(1) :+ ChatKind.Group(Seq(lastNode, node))
          else
            kinds :+ ChatKind.Single(node)

        case Some(ChatKind.Group(lastNodes)) =>
          if(shouldGroup(lastNodes.last, node))
            kinds.dropRight(1) :+ ChatKind.Group(nodeIds = lastNodes :+ node)
          else
            kinds :+ ChatKind.Single(node)

        case None => kinds :+ ChatKind.Single(node)
      }
    }
  }

  /// @return an avatar vnode or empty depending on the showAvatar setting
  private def avatarDiv(isOwn: Boolean, user: Option[UserId], avatarSize: AvatarSize) = {
    if(showAvatar == ShowOpts.Always
      || (showAvatar == ShowOpts.OtherOnly && !isOwn)
      || (showAvatar == ShowOpts.OwnOnly && isOwn)) {
      user.fold(div())(
        user =>
          div(
            Avatar.user(user)(
              cls := "avatar",
              padding := avatarSize.padding,
              width := avatarSize.width,
            ),
          )
      )
    } else div()
  }

  /// @return a date vnode or empty based on showDate setting
  private def optDateDiv(isOwn: Boolean, nodeId: NodeId, graph: Graph) = {
    if(showDate == ShowOpts.Always
      || (showDate == ShowOpts.OtherOnly && !isOwn)
      || (showDate == ShowOpts.OwnOnly && isOwn))
      div(
        dateFns.formatDistance(new js.Date(graph.nodeCreated(nodeId)), new js.Date), " ago",
        cls := "chatmsg-date"
      )
    else
      div()
  }

  /// @return an author vnode or empty based on showAuthor setting
  private def optAuthorDiv(isOwn: Boolean, nodeId: NodeId, graph: Graph) = {
    if(showAuthor == ShowOpts.Always
      || (showAuthor == ShowOpts.OtherOnly && !isOwn)
      || (showAuthor == ShowOpts.OwnOnly && isOwn))
      graph
        .authors(nodeId)
        .headOption
        .fold(div())(author => div(author.name, cls := "chatmsg-author"))
    else div()
  }

  private def renderGroupedMessages(
    state: GlobalState,
    nodeIds: Seq[NodeId],
    graph: Graph,
    alreadyVisualizedParentIds: Set[NodeId],
    path: List[NodeId],
    directParentIds: Set[NodeId],
    currentUserId: UserId,
    avatarSize: Rx[AvatarSize],
    activeReplyFields: Var[Set[List[NodeId]]]
  )(
    implicit ctx: Ctx.Owner
  ): VNode = {
    val currNode = nodeIds.last
    val headNode = nodeIds.head
    val isMine = graph.authors(currNode).contains(currentUserId)

    div(
      cls := "chatmsg-group-outer-frame",
      keyed(headNode), // if the head-node is moved/removed, all reply-fields in this Group close. We didn't find a better key yet.
      Rx{(avatarSize() != AvatarSize.Small).ifTrue[VDomModifier](avatarDiv(isMine, graph.authorIds(headNode).headOption, avatarSize())(marginRight := "5px"))},
      div(
        keyed,
        cls := "chatmsg-group-inner-frame",
        chatMessageHeader(isMine, headNode, graph, avatarSize),
        nodeIds.map(nid => renderThread(state, graph, alreadyVisualizedParentIds = alreadyVisualizedParentIds, path = path, directParentIds = directParentIds, nid, currentUserId, activeReplyFields)
        ),
      ),
    )
  }

  private def renderThread(state: GlobalState, graph: Graph, alreadyVisualizedParentIds: Set[NodeId], path: List[NodeId], directParentIds: Set[NodeId], nodeId: NodeId, currentUserId: UserId, activeReplyFields: Var[Set[List[NodeId]]])(implicit ctx: Ctx.Owner): VNode = {
    val inCycle = alreadyVisualizedParentIds.contains(nodeId)
    if(!graph.isDeletedNow(nodeId, directParentIds) && (graph.hasChildren(nodeId) || graph.hasDeletedChildren(nodeId)) && !inCycle) {
      val children = (graph.children(nodeId) ++ graph.deletedChildren(nodeId)).toSeq.sortBy(nid => graph.nodeCreated(nid): Long)
      div(
        keyed(nodeId),
        chatMessageLine(state, graph, alreadyVisualizedParentIds, directParentIds, nodeId, messageCardInjected = VDomModifier(
          boxShadow := s"0px 1px 0px 1px ${ tagColor(nodeId).toHex }",
        )),
        div(
          cls := "chat-thread",
          borderLeft := s"3px solid ${ tagColor(nodeId).toHex }",

          groupNodes(graph, children, state, currentUserId)
            .map(kind => renderGroupedMessages(state, kind.nodeIds, graph, alreadyVisualizedParentIds + nodeId, nodeId :: path, Set(nodeId), currentUserId, Rx(avatarSizeThread), activeReplyFields)),

          replyField(state, nodeId, directParentIds, path, activeReplyFields),

          draggableAs(state, DragItem.DisableDrag),
          dragTarget(DragItem.Chat.Thread(nodeId)),
          cursor.default, // draggable sets cursor.move, but drag is disabled on thread background
          keyed(nodeId)
        )
      )
    }
    else if(inCycle)
           chatMessageLine(state, graph, alreadyVisualizedParentIds, directParentIds, nodeId, messageCardInjected = VDomModifier(
             Styles.flex,
             alignItems.center,
             freeSolid.faSyncAlt,
             paddingRight := "3px",
             backgroundColor := "#CCC",
             color := "#666",
             boxShadow := "0px 1px 0px 1px rgb(102, 102, 102, 0.45)",
           ))
    else
      chatMessageLine(state, graph, alreadyVisualizedParentIds, directParentIds, nodeId)
  }

  def replyField(state: GlobalState, nodeId: NodeId, directParentIds: Set[NodeId], path: List[NodeId], activeReplyFields: Var[Set[List[NodeId]]])(implicit ctx: Ctx.Owner) = {
    val fullPath = nodeId :: path

    div(
      keyed(nodeId),
      Rx {
        val active = activeReplyFields() contains fullPath
        if(active)
          div(
            keyed(nodeId),
            Styles.flex,
            alignItems.center,
            inputField(state, directParentIds = Set(nodeId),
              blurAction = { value => if(value.isEmpty) activeReplyFields.update(_ - fullPath) }
            )(ctx)(
              keyed(nodeId),
              padding := "3px",
              width := "100%"
            ),
            div(
              keyed,
              freeSolid.faTimes,
              padding := "10px",
              Styles.flexStatic,
              cursor.pointer,
              onClick --> sideEffect { activeReplyFields.update(_ - fullPath) },
            )
          )
        else
          div(
            cls := "chat-replybutton",
            freeSolid.faReply,
            " reply",
            marginTop := "3px",
            marginLeft := "8px",
            // not onClick, because if another reply-field is already open, the click first triggers the blur-event of
            // the active field. If the field was empty it disappears, and shifts the reply-field away from the cursor
            // before the click was finished. This does not happen with onMouseDown.
            onMouseDown.stopPropagation --> sideEffect { activeReplyFields.update(_ + fullPath) }
          )
      }
    )
  }

  /// @return a vnode containing a chat header with optional name, date and avatar
  private def chatMessageHeader(
    isMine: Boolean,
    nodeId: NodeId,
    graph: Graph,
    avatarSize: Rx[AvatarSize]
  )(implicit ctx: Ctx.Owner) = {
    val authorIdOpt = graph.authors(nodeId).headOption.map(_.id)
    div(
      cls := "chatmsg-header",
      keyed(nodeId),
      Rx { (avatarSize() == AvatarSize.Small).ifTrue[VDomModifier](avatarDiv(isMine, authorIdOpt, avatarSize())(marginRight := "3px")) },
      optAuthorDiv(isMine, nodeId, graph),
      optDateDiv(isMine, nodeId, graph),
    )
  }

  /// @return the actual body of a chat message
  /** Should be styled in such a way as to be repeatable so we can use this in groups */
  private def chatMessageLine(state: GlobalState, graph: Graph, alreadyVisualizedParentIds: Set[NodeId], directParentIds: Set[NodeId], nodeId: NodeId, messageCardInjected: VDomModifier = VDomModifier.empty)(
    implicit ctx: Ctx.Owner
  ) = {
    val isDeleted = graph.isDeletedNow(nodeId, directParentIds)
    val isSelected = state.selectedNodeIds.map(_ contains nodeId)
    val node = graph.nodesById(nodeId)

    val editable = Var(false)

    val checkbox = div(
      cls := "ui checkbox fitted",
      isSelected.map(_.ifTrueOption(visibility.visible)),
      input(
        tpe := "checkbox",
        checked <-- isSelected,
        onChange.checked --> sideEffect { checked =>
          if(checked) state.selectedNodeIds.update(_ + nodeId)
          else state.selectedNodeIds.update(_ - nodeId)
        }
      ),
      label()
    )

    val msgControls = div(
      cls := "chatmsg-controls",
      if(isDeleted) undeleteButton(state, nodeId, directParentIds)
      else VDomModifier(
        editButton(state, editable),
        deleteButton(state, nodeId, directParentIds)
      ),
      zoomButton(state, nodeId)
    )


    val messageCard = nodeCardEditable(state, node, editable = editable, state.eventProcessor.changes, newTagParentIds = directParentIds)(ctx)(
      isDeleted.ifTrueOption(cls := "node-deleted"), // TODO: outwatch: switch classes on and off via Boolean or Rx[Boolean]
      cls := "drag-feedback",
      messageCardInjected,
      onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
    )


    div(
      keyed(nodeId),
      isSelected.map(_.ifTrueOption(backgroundColor := "rgba(65,184,255, 0.5)")),
      div( // this nesting is needed to get a :hover effect on the selected background
        cls := "chatmsg-line",
        Styles.flex,
        onClick --> sideEffect { state.selectedNodeIds.update(_.toggle(nodeId)) },

        editable.map { editable =>
          if(editable)
            draggableAs(state, DragItem.DisableDrag) // prevents dragging when selecting text
          else {
            val payload = () => {
              val selection = state.selectedNodeIds.now
              if(selection contains nodeId)
                DragItem.Chat.Messages(selection.toSeq)
              else
                DragItem.Chat.Message(nodeId)
            }
            // payload is call by name, so it's always the current selectedNodeIds
            draggableAs(state, payload())
          }
        },

        dragTarget(DragItem.Chat.Message(nodeId)),

        // checkbox(Styles.flexStatic),
        messageCard,
        messageTags(state, graph, nodeId, alreadyVisualizedParentIds),
        Rx { (state.screenSize() != ScreenSize.Small).ifTrue[VDomModifier](msgControls(Styles.flexStatic)) }
      )
    )
  }

  private def editButton(state: GlobalState, editable: Var[Boolean])(implicit ctx: Ctx.Owner) =
    div(
      div(cls := "fa-fw", freeRegular.faEdit),
      onClick.stopPropagation(!editable.now) --> editable,
      cursor.pointer,
    )

  private def deleteButton(state: GlobalState, nodeId: NodeId, directParentIds: Set[NodeId])(implicit ctx: Ctx.Owner) =
    div(
      div(cls := "fa-fw", freeRegular.faTrashAlt),
      onClick.stopPropagation --> sideEffect {
        state.eventProcessor.changes.onNext(GraphChanges.delete(nodeId, directParentIds))
        state.selectedNodeIds.update(_ - nodeId)
      },
      cursor.pointer,
    )

  private def undeleteButton(state: GlobalState, nodeId: NodeId, directParentIds: Set[NodeId])(implicit ctx: Ctx.Owner) =
    div(
      div(cls := "fa-fw", fontawesome.layered(
        fontawesome.icon(freeRegular.faTrashAlt),
        fontawesome.icon(freeSolid.faMinus, new Params {
          transform = new Transform {
            rotate = 45.0
          }

        })
      )),
      onClick.stopPropagation(GraphChanges.undelete(nodeId, directParentIds)) --> state.eventProcessor.changes,
      cursor.pointer,
    )

  private def zoomButton(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner) =
    div(
      div(cls := "fa-fw", freeRegular.faArrowAltCircleRight),
      onClick.stopPropagation(state.viewConfig.now.copy(page = Page(nodeId))) --> state.viewConfig,
      cursor.pointer,
    )

  private def messageTags(state: GlobalState, graph: Graph, nodeId: NodeId, alreadyVisualizedParentIds: Set[NodeId])(implicit ctx: Ctx.Owner) = {
    val directNodeTags = graph.directNodeTags((nodeId, alreadyVisualizedParentIds))
    val transitiveNodeTags = graph.transitiveNodeTags((nodeId, alreadyVisualizedParentIds))

    Rx {
      state.screenSize() match {
        case ScreenSize.Small =>
          div(
            cls := "tags",
            directNodeTags.map { tag =>
              nodeTagDot(state, tag)(Styles.flexStatic)
            }(breakOut): Seq[VDomModifier],
            transitiveNodeTags.map { tag =>
              nodeTagDot(state, tag)(Styles.flexStatic, cls := "transitivetag", opacity := 0.4)
            }(breakOut): Seq[VDomModifier]
          )
        case _                =>
          div(
            cls := "tags",
            directNodeTags.map { tag =>
              removableNodeTag(state, tag, nodeId, graph)(Styles.flexStatic)
            }(breakOut): Seq[VDomModifier],
            transitiveNodeTags.map { tag =>
              nodeTag(state, tag)(Styles.flexStatic, cls := "transitivetag", opacity := 0.4)
            }(breakOut): Seq[VDomModifier]
          )
      }
    }

  }

  private def inputField(state: GlobalState, directParentIds: Set[NodeId], blurAction: String => Unit = _ => ())(implicit ctx: Ctx.Owner): VNode = {
    val disableUserInput = Rx {
      val graphNotLoaded = (state.graph().nodeIds intersect state.page().parentIds.toSet).isEmpty
      val pageModeOrphans = state.page().mode == PageMode.Orphans
      graphNotLoaded || pageModeOrphans
    }

    val initialValue = Rx {
      state.viewConfig().shareOptions.map { share =>
        val elements = List(share.title, share.text, share.url).filter(_.nonEmpty)
        elements.mkString(" - ")
      }
    }

    div(
      cls := "ui form",
      keyed(directParentIds),
      textArea(
        keyed,
        cls := "field",
        valueWithEnterWithInitial(initialValue.toObservable.collect { case Some(s) => s }) --> sideEffect { str =>
          val graph = state.graphContent.now
          val selectedNodeIds = state.selectedNodeIds.now
          val changes = {
            val newNode = Node.Content.empty
            val nodeChanges = NodeDataParser.addNode(str, contextNodes = graph.nodes, directParentIds ++ selectedNodeIds, baseNode = newNode)
            val newNodeParentships = GraphChanges.connect(Edge.Parent)(newNode.id, directParentIds)
            nodeChanges merge newNodeParentships
          }

          state.eventProcessor.changes.onNext(changes)
        },
        onInsert.asHtml --> sideEffect { e => e.focus() },
        onBlur.value --> sideEffect { value => blurAction(value) },
        disabled <-- disableUserInput,
        rows := 1, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
        style("resize") := "none", //TODO: add resize style to scala-dom-types
        Placeholders.newNode
      )
    )
  }
}
