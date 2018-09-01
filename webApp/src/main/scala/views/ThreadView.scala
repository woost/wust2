package wust.webApp.views

import fontAwesome._
import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.{BaseColors, NodeColor}
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
import org.scalajs.dom.raw.HTMLElement
import rx.opmacros.Utils.Id

import scala.collection.breakOut
import scala.scalajs.js

object ThreadView {
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

  case class MessageMeta(
    state: GlobalState,
    graph: Graph,
    alreadyVisualizedParentIds: Set[NodeId],
    path: List[NodeId],
    directParentIds: Set[NodeId],
    currentUserId: UserId,
    renderMessage: (NodeId, MessageMeta) => VDomModifier,
  )

  type MsgControls = (NodeId, MessageMeta, Boolean, Var[Boolean]) => Seq[VNode]


  // -- display options --
  val showAvatar: ShowOpts = ShowOpts.OtherOnly
  val showAuthor: ShowOpts = ShowOpts.OtherOnly
  val showDate: ShowOpts = ShowOpts.Always
  val grouping = true
  val avatarSizeThread = AvatarSize.Small
  val avatarBorder = true
  val chatMessageDateFormat = "yyyy-MM-dd HH:mm"

  def localEditableVar(currentlyEditable:Var[Option[NodeId]], nodeId: NodeId)(implicit ctx:Ctx.Owner): Var[Boolean] = {
    currentlyEditable.zoom(_.fold(false)(_ == nodeId)){
      case (_, true) => Some(nodeId)
      case (Some(`nodeId`), false) => None
      case (current, false) => current // should never happen
    }
  }

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    val nodeIds: Rx[Seq[NodeId]] = Rx {
      val page = state.page()
      val fullGraph = state.graph()
      val graph = state.graphContent()
      graph.nodes.collect {
        case n: Node.Content if fullGraph.isChildOfAny(n.id, page.parentIds) || fullGraph.isDeletedChildOfAny(n.id, page.parentIds) => n.id
      }.toSeq.sortBy(nid => graph.nodeCreated(nid): Long)
    }

    val activeReplyFields = Var(Set.empty[List[NodeId]])
    val currentlyEditable = Var(Option.empty[NodeId])

    def msgControls(nodeId: NodeId, meta: MessageMeta, isDeleted: Boolean, editable: Var[Boolean]): Seq[VNode] = {
      import meta._
      val state = meta.state
      List(
        if(isDeleted) List(undeleteButton(state, nodeId, directParentIds))
        else List(
          replyButton(action = { () => activeReplyFields.update(_ + (nodeId :: meta.path)) }),
          editButton(state, editable),
          deleteButton(state, nodeId, directParentIds)
        ),
        List(zoomButton(state, nodeId :: Nil))
      ).flatten
    }

    def renderMessage(nodeId: NodeId, meta: MessageMeta): VDomModifier = renderThread(nodeId, meta, msgControls, activeReplyFields, currentlyEditable)

    val submittedNewMessage = Handler.create[Unit].unsafeRunSync()

    val selectedSingleNodeActions:NodeId => List[VNode] = nodeId => List(
      editButton(state, localEditableVar(currentlyEditable, nodeId)).apply(onClick(Set.empty[NodeId]) --> state.selectedNodeIds),
      // replyButton(nodeId, )
    )
    val selectedNodeActions:List[NodeId] => List[VNode] =  nodeIds => List(
      zoomButton(state, nodeIds).apply(onClick --> sideEffect{state.selectedNodeIds.update(_ -- nodeIds)}),
      SelectedNodes.deleteAllButton(state, nodeIds),
    )

    div(
      Styles.flex,
      flexDirection.column,
      alignItems.stretch,
      alignContent.stretch,
      height := "100%",
      div(
        Styles.flex,
        flexDirection.column,
        height := "100%",
        position.relative,
        SelectedNodes(state, nodeActions = selectedNodeActions, singleNodeActions = selectedSingleNodeActions).apply(Styles.flexStatic, position.absolute, width := "100%"),
        chatHistory(state, nodeIds, submittedNewMessage, renderMessage = renderMessage).apply(
          height := "100%",
          width := "100%",
          backgroundColor <-- state.pageStyle.map(_.bgLightColor),
        ),
        //        TagsList(state).apply(Styles.flexStatic)
      ),
      Rx { inputField(state, state.page().parentIdSet, submittedNewMessage, focusOnInsert = state.screenSize.now != ScreenSize.Small).apply(Styles.flexStatic, padding := "3px") },
      registerDraggableContainer(state),
    )
  }

  def chatHistory(
    state: GlobalState,
    nodeIds: Rx[Seq[NodeId]],
    submittedNewMessage: Handler[Unit],
    renderMessage: (NodeId, MessageMeta) => VDomModifier,
  )(implicit ctx: Ctx.Owner): VNode = {
    val avatarSizeToplevel: Rx[AvatarSize] = Rx { if(state.screenSize() == ScreenSize.Small) AvatarSize.Small else AvatarSize.Large }

    val isScrolledToBottom = Var(true)
    val scrollableHistoryElem = Var(None: Option[HTMLElement])
    submittedNewMessage.foreach { _ =>
      scrollableHistoryElem.now.foreach { elem =>
        scrollToBottom(elem)
      }
    }

    div(
      // this wrapping of chat history is currently needed,
      // to allow dragging the scrollbar without triggering a drag event.
      // see https://github.com/Shopify/draggable/issues/262
      div(
        cls := "chat-history",
        padding := "20px 0 20px 20px",
        Rx {
          val page = state.page()
          val graph = state.graphContent()
          val user = state.user()
          if(nodeIds().isEmpty) VDomModifier(emptyChatNotice)
          else
            VDomModifier(
              groupNodes(graph, nodeIds(), state, user.id)
                .map(kind => renderGroupedMessages(
                  kind.nodeIds,
                  MessageMeta(state, graph, page.parentIdSet, Nil, page.parentIdSet, user.id, renderMessage), avatarSizeToplevel)
                ),


              draggableAs(state, DragItem.DisableDrag),
              cursor.default, // draggable sets cursor.move, but drag is disabled on page background
              dragTarget(DragItem.Chat.Page(page.parentIds)),
              keyed
            )
        },
        onPrePatch --> sideEffect {
          scrollableHistoryElem.now.foreach { prev =>
            val wasScrolledToBottom = prev.scrollHeight - prev.clientHeight <= prev.scrollTop + 11 // at bottom + 10 px tolerance
            isScrolledToBottom() = wasScrolledToBottom
          }
        },
        onPostPatch --> sideEffect {
          scrollableHistoryElem.now.foreach { elem =>
            if(isScrolledToBottom.now)
              defer { scrollToBottom(elem) }
          }
        }
      ),
      overflow.auto,
      onDomElementChange.asHtml --> sideEffect { elem =>
        if(isScrolledToBottom.now)
          defer { scrollToBottom(elem) }
        scrollableHistoryElem() = Some(elem)
      },
    )
  }

  private def emptyChatNotice: VNode =
    h3(textAlign.center, "Nothing here yet.", paddingTop := "40%", color := "rgba(0,0,0,0.5)")

  /** returns a Seq of ChatKind instances where similar successive nodes are grouped via ChatKind.Group */
  def groupNodes(
    graph: Graph,
    nodes: Seq[NodeId],
    state: GlobalState,
    currentUserId: UserId
  ): Seq[ChatKind] = {
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
  def avatarDiv(isOwn: Boolean, user: Option[UserId], avatarSize: AvatarSize): VNode = {
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
    nodeIds: Seq[NodeId],
    meta: MessageMeta,
    avatarSize: Rx[AvatarSize],
  )(
    implicit ctx: Ctx.Owner
  ): VNode = {
    import meta._

    val currNode = nodeIds.last
    val headNode = nodeIds.head
    val isMine = graph.authors(currNode).contains(currentUserId)

    div(
      cls := "chatmsg-group-outer-frame",
      keyed(headNode), // if the head-node is moved/removed, all reply-fields in this Group close. We didn't find a better key yet.
      Rx { (avatarSize() != AvatarSize.Small).ifTrue[VDomModifier](avatarDiv(isMine, graph.authorIds(headNode).headOption, avatarSize())(marginRight := "5px")) },
      div(
        keyed,
        cls := "chatmsg-group-inner-frame",
        chatMessageHeader(isMine, headNode, graph, avatarSize),
        nodeIds.map(nid => renderMessage(nid, meta)),
      ),
    )
  }

  private def renderThread(nodeId: NodeId, meta: MessageMeta, msgControls: MsgControls, activeReplyFields: Var[Set[List[NodeId]]], currentlyEditing: Var[Option[NodeId]])(implicit ctx: Ctx.Owner): VDomModifier = {
    import meta._
    val inCycle = alreadyVisualizedParentIds.contains(nodeId)
    val isThread = !graph.isDeletedNow(nodeId, directParentIds) && (graph.hasChildren(nodeId) || graph.hasDeletedChildren(nodeId)) && !inCycle
    val showThread = Rx {
      // this is a separate Rx, to prevent rerendering of the whole thread, when only replyFieldActive changes.
      val replyFieldActive = activeReplyFields() contains (nodeId :: path)
      isThread || replyFieldActive
    }
    Rx {
      if(showThread()) {
        val children = (graph.children(nodeId) ++ graph.deletedChildren(nodeId)).toSeq.sortBy(nid => graph.nodeCreated(nid): Long)
        div(
          backgroundColor := BaseColors.pageBgLight.copy(h = NodeColor.hue(nodeId)).toHex,
          keyed(nodeId),
          chatMessageLine(meta, nodeId, msgControls, currentlyEditing, transformMessageCard = _ (
            boxShadow := s"0px 1px 0px 1px ${ tagColor(nodeId).toHex }",
          )),
          div(
            cls := "chat-thread",
            borderLeft := s"3px solid ${ tagColor(nodeId).toHex }",

            groupNodes(graph, children, state, currentUserId)
              .map(kind => renderGroupedMessages(
                kind.nodeIds,
                meta.copy(
                  alreadyVisualizedParentIds = alreadyVisualizedParentIds + nodeId,
                  path = nodeId :: path,
                  directParentIds = Set(nodeId),
                ),
                Rx(avatarSizeThread),
              )),

            replyField(state, nodeId, directParentIds, path, activeReplyFields),

            draggableAs(state, DragItem.DisableDrag),
            dragTarget(DragItem.Chat.Thread(nodeId)),
            cursor.default, // draggable sets cursor.move, but drag is disabled on thread background
            keyed(nodeId)
          )
        )
      }
      else if(inCycle)
             chatMessageLine(meta, nodeId, msgControls, currentlyEditing, transformMessageCard = _ (
               Styles.flex,
               alignItems.center,
               freeSolid.faSyncAlt,
               paddingRight := "3px",
               backgroundColor := "#CCC",
               color := "#666",
               boxShadow := "0px 1px 0px 1px rgb(102, 102, 102, 0.45)",
             ))
      else
        chatMessageLine(meta, nodeId, msgControls, currentlyEditing)
    }
  }

  def replyField(state: GlobalState, nodeId: NodeId, directParentIds: Set[NodeId], path: List[NodeId], activeReplyFields: Var[Set[List[NodeId]]])(implicit ctx: Ctx.Owner): VNode = {
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
              focusOnInsert = true, blurAction = { value => if(value.isEmpty) activeReplyFields.update(_ - fullPath) }
            )(ctx)(
              keyed(nodeId),
              padding := "3px",
              width := "100%"
            ),
            closeButton(
              keyed,
              onClick --> sideEffect { activeReplyFields.update(_ - fullPath) },
            ),
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
  def chatMessageHeader(
    isMine: Boolean,
    nodeId: NodeId,
    graph: Graph,
    avatarSize: Rx[AvatarSize],
    showDate: Boolean = true,
  )(implicit ctx: Ctx.Owner): VNode = {
    val authorIdOpt = graph.authors(nodeId).headOption.map(_.id)
    div(
      cls := "chatmsg-header",
      keyed(nodeId),
      Rx { (avatarSize() == AvatarSize.Small).ifTrue[VDomModifier](avatarDiv(isMine, authorIdOpt, avatarSize())(marginRight := "3px")) },
      optAuthorDiv(isMine, nodeId, graph),
      showDate.ifTrue[VDomModifier](optDateDiv(isMine, nodeId, graph)),
    )
  }

  /// @return the actual body of a chat message
  /** Should be styled in such a way as to be repeatable so we can use this in groups */
  def chatMessageLine(meta: MessageMeta, nodeId: NodeId, msgControls: MsgControls, currentlyEditable: Var[Option[NodeId]], showTags: Boolean = true, transformMessageCard: VNode => VDomModifier = identity)(
    implicit ctx: Ctx.Owner
  ): VNode = {
    import meta._

    val isDeleted = graph.isDeletedNow(nodeId, directParentIds)
    val isSelected = state.selectedNodeIds.map(_ contains nodeId)
    val node = graph.nodesById(nodeId)

    val editable:Var[Boolean] = localEditableVar(currentlyEditable, nodeId)


    val messageCard = nodeCardEditable(state, node, editable = editable, state.eventProcessor.changes, newTagParentIds = directParentIds)(ctx)(
      isDeleted.ifTrueOption(cls := "node-deleted"), // TODO: outwatch: switch classes on and off via Boolean or Rx[Boolean]
      cls := "drag-feedback",
      onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
    )

    val controls = div(
      cls := "chatmsg-controls",
      msgControls(nodeId, meta, isDeleted, editable)
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

        transformMessageCard(messageCard),
        showTags.ifTrue[VDomModifier](messageTags(state, graph, nodeId, alreadyVisualizedParentIds)),
        Rx { (state.screenSize() != ScreenSize.Small).ifTrue[VDomModifier](controls(Styles.flexStatic)) }
      )
    )
  }

  def replyButton(action: () => Unit)(implicit ctx: Ctx.Owner): VNode = {
    div(
      div(cls := "fa-fw", freeSolid.faReply),
      onClick.stopPropagation --> sideEffect { action() },
      cursor.pointer,
    )
  }

  def editButton(state: GlobalState, editable: Var[Boolean])(implicit ctx: Ctx.Owner): VNode =
    div(
      div(cls := "fa-fw", freeRegular.faEdit),
      onClick.stopPropagation(!editable.now) --> editable,
      cursor.pointer,
    )

  def deleteButton(state: GlobalState, nodeId: NodeId, directParentIds: Set[NodeId])(implicit ctx: Ctx.Owner): VNode =
    div(
      div(cls := "fa-fw", freeRegular.faTrashAlt),
      onClick.stopPropagation --> sideEffect {
        state.eventProcessor.changes.onNext(GraphChanges.delete(nodeId, directParentIds))
        state.selectedNodeIds.update(_ - nodeId)
      },
      cursor.pointer,
    )

  def undeleteButton(state: GlobalState, nodeId: NodeId, directParentIds: Set[NodeId])(implicit ctx: Ctx.Owner): VNode =
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

  def zoomButton(state: GlobalState, nodeIds: Seq[NodeId])(implicit ctx: Ctx.Owner): VNode =
    div(
      div(cls := "fa-fw", freeRegular.faArrowAltCircleRight),
      onClick.stopPropagation(state.viewConfig.now.copy(page = Page(nodeIds))) --> state.viewConfig,
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

  def inputField(state: GlobalState, directParentIds: Set[NodeId], submittedNewMessage: Handler[Unit] = Handler.create[Unit].unsafeRunSync(), focusOnInsert: Boolean = false, blurAction: String => Unit = _ => ())(implicit ctx: Ctx.Owner): VNode = {
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
          submittedNewMessage.onNext(Unit)
        },
        focusOnInsert.ifTrue[VDomModifier](onInsert.asHtml --> sideEffect { e => e.focus() }),
        onBlur.value --> sideEffect { value => blurAction(value) },
        disabled <-- disableUserInput,
        rows := 1, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
        style("resize") := "none", //TODO: add resize style to scala-dom-types
        placeholder := "Write a message and press Enter to submit.",
      )
    )
  }
}
