package wust.webApp.views

import cats.effect.IO
import fontAwesome._
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
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
import wust.webApp.Icons

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

  sealed trait ThreadVisibility
  object ThreadVisibility {
    case object Expanded extends ThreadVisibility
    case object Collapsed extends ThreadVisibility
    case object Plain extends ThreadVisibility
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

  def localEditableVar(currentlyEditable: Var[Option[List[NodeId]]], path: List[NodeId])(implicit ctx: Ctx.Owner): Var[Boolean] = {
    currentlyEditable.zoom(_.fold(false)(_ == path)) {
      case (_, true)               => Some(path)
      case (Some(`path`), false) => None
      case (current, false)        => current // should never happen
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
    val currentlyEditable = Var(Option.empty[List[NodeId]])

    def msgControls(nodeId: NodeId, meta: MessageMeta, isDeleted: Boolean, editable: Var[Boolean]): Seq[VNode] = {
      import meta._
      val state = meta.state
      if(isDeleted) List(undeleteButton(state, nodeId, directParentIds))
      else List(
        replyButton.apply(onTap handleWith {
          activeReplyFields.update(_ + (nodeId :: meta.path))
          // we also set an Expand-edge, so that after an reply and its update the thread does not close again
          state.eventProcessor.changes.onNext(GraphChanges.connect(Edge.Expanded)(currentUserId, nodeId))
          ()
        }),
        editButton.apply(onTap handleWith {
          editable() = true
          state.selectedNodeIds() = Set.empty[NodeId]
        }),
        deleteButton(state, nodeId, directParentIds),
        zoomButton(state, nodeId :: Nil)
      )
    }

    def shouldGroup(graph: Graph, nodes: Seq[NodeId]): Boolean = {
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


    def renderMessage(nodeId: NodeId, meta: MessageMeta)(implicit ctx: Ctx.Owner): VDomModifier = renderThread(nodeId, meta, shouldGroup, msgControls, activeReplyFields, currentlyEditable)

    val submittedNewMessage = Handler.created[Unit]

    var lastSelectedPath: List[NodeId] = Nil // TODO: set by clicking on a message
    def reversePath(nodeId: NodeId, pageParents: Set[NodeId], graph: Graph): List[NodeId] = {
      // this only works, because all nodes in the graph are descendants
      // of the pageParents.
      val isTopLevelNode = pageParents.exists(pageParentId => graph.children(pageParentId) contains nodeId)
      if(isTopLevelNode) nodeId :: Nil
      else {
        val nextParent = {
          val parents = graph.parents(nodeId)
          assert(parents.nonEmpty)
          val lastSelectedParents = parents intersect lastSelectedPath.toSet
          if(lastSelectedParents.nonEmpty)
            lastSelectedParents.head
          else {
            // // when only taking youngest parents first, the pageParents would always be last.
            // // So we check if the node is a toplevel node
            // val topLevelParentpageParents.find(pageParentId => graph.children(pageParentId).contains(nodeId)))
            graph.parents(nodeId).maxBy(nid => graph.nodeCreated(nid): Long)
          }

        }
        nodeId :: reversePath(nextParent, pageParents, graph)
      }
    }

    def clearSelectedNodeIds(): Unit = {
      state.selectedNodeIds() = Set.empty[NodeId]
    }

    val selectedSingleNodeActions: NodeId => List[VNode] = nodeId => if(state.graphContent.now.nodesById.isDefinedAt(nodeId)) {
      val path = reversePath(nodeId, state.page.now.parentIdSet, state.graphContent.now)
      List(
        editButton.apply(
          onTap handleWith {
            currentlyEditable() = Some(path)
            state.selectedNodeIds() = Set.empty[NodeId]
          }
        ),
        replyButton.apply(onTap handleWith { activeReplyFields.update(_ + path); clearSelectedNodeIds() }) //TODO: scroll to focused field?
      )
    } else Nil
    val selectedNodeActions: List[NodeId] => List[VNode] = nodeIds => List(
      zoomButton(state, nodeIds).apply(onTap handleWith { state.selectedNodeIds.update(_ -- nodeIds) }),
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
        chatHistory(state, nodeIds, submittedNewMessage, renderMessage = c => (a, b) => renderMessage(a, b)(c), shouldGroup = shouldGroup _).apply(
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
    renderMessage: Ctx.Owner => (NodeId, MessageMeta) => VDomModifier,
    shouldGroup: (Graph, Seq[NodeId]) => Boolean,
  )(implicit ctx: Ctx.Owner): VNode = {

    val isScrolledToBottom = Var(true)
    val scrollableHistoryElem = Var(None: Option[HTMLElement])


    div(
      managed(IO {
        submittedNewMessage.foreach { _ =>
          scrollableHistoryElem.now.foreach { elem =>
            scrollToBottom(elem)
          }
        }
      }),
      // this wrapping of chat history is currently needed,
      // to allow dragging the scrollbar without triggering a drag event.
      // see https://github.com/Shopify/draggable/issues/262
      div(
        cls := "chat-history",
        padding := "50px 0 20px 20px", // large padding-top to have space for selectedNodes bar
        Rx {
          val page = state.page()
          val graph = state.graphContent()
          val user = state.user()
          val avatarSizeToplevel: AvatarSize = if(state.screenSize() == ScreenSize.Small) AvatarSize.Small else AvatarSize.Large

          if(nodeIds().isEmpty) VDomModifier(emptyChatNotice)
          else
            VDomModifier(
              groupNodes(graph, nodeIds(), state, user.id, shouldGroup)
                .map(kind => renderGroupedMessages(
                  kind.nodeIds,
                  MessageMeta(state, graph, page.parentIdSet, Nil, page.parentIdSet, user.id, renderMessage(implicitly)), avatarSizeToplevel)
                ),


              draggableAs(state, DragItem.DisableDrag),
              cursor.auto, // draggable sets cursor.move, but drag is disabled on page background
              dragTarget(DragItem.Chat.Page(page.parentIds)),
              keyed
            )
        },
        onSnabbdomPrePatch handleWith {
          scrollableHistoryElem.now.foreach { prev =>
            val wasScrolledToBottom = prev.scrollHeight - prev.clientHeight <= prev.scrollTop + 11 // at bottom + 10 px tolerance
            isScrolledToBottom() = wasScrolledToBottom
          }
        },
        onDomUpdate handleWith {
          scrollableHistoryElem.now.foreach { elem =>
            if(isScrolledToBottom.now)
              defer { scrollToBottom(elem) }
          }
        },
        managed(IO {
          // on page change, always scroll down
          state.page.foreach { _ =>
              isScrolledToBottom() = true
              scrollableHistoryElem.now.foreach { elem => defer{ scrollToBottom(elem) } }
          }
        }),

      ),
      overflow.auto,
      onDomMount.asHtml handleWith { elem =>
        if(isScrolledToBottom.now)
          defer { scrollToBottom(elem) }
        scrollableHistoryElem() = Some(elem)
      },

      // tapping on background deselects
      onTap handleWith { state.selectedNodeIds() = Set.empty[NodeId] }
    )
  }

  private def emptyChatNotice: VNode =
    h3(textAlign.center, "Nothing here yet.", paddingTop := "40%", color := "rgba(0,0,0,0.5)")

  /** returns a Seq of ChatKind instances where similar successive nodes are grouped via ChatKind.Group */
  def groupNodes(
    graph: Graph,
    nodes: Seq[NodeId],
    state: GlobalState,
    currentUserId: UserId,
    shouldGroup: (Graph, Seq[NodeId]) => Boolean
  ): Seq[ChatKind] = {
    nodes.foldLeft(Seq[ChatKind]()) { (kinds, node) =>
      kinds.lastOption match {
        case Some(ChatKind.Single(lastNode)) =>
          if(shouldGroup(graph, lastNode :: node :: Nil))
            kinds.dropRight(1) :+ ChatKind.Group(Seq(lastNode, node))
          else
            kinds :+ ChatKind.Single(node)

        case Some(ChatKind.Group(lastNodes)) =>
          if(shouldGroup(graph, lastNodes.last :: node :: Nil))
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
        .fold(div())(author => div(if(author.data.isImplicit) Rendered.implicitUserName else author.name, cls := "chatmsg-author"))
    else div()
  }

  private def renderGroupedMessages(
    nodeIds: Seq[NodeId],
    meta: MessageMeta,
    avatarSize: AvatarSize,
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
      (avatarSize != AvatarSize.Small).ifTrue[VDomModifier](avatarDiv(isMine, graph.authorIds(headNode).headOption, avatarSize)(marginRight := "5px")),
      div(
        keyed,
        cls := "chatmsg-group-inner-frame",
        chatMessageHeader(isMine, headNode, graph, avatarSize),
        nodeIds.map(nid => renderMessage(nid, meta)),
      ),
    )
  }

  private def renderThread(nodeId: NodeId, meta: MessageMeta, shouldGroup: (Graph, Seq[NodeId]) => Boolean, msgControls: MsgControls, activeReplyFields: Var[Set[List[NodeId]]], currentlyEditing: Var[Option[List[NodeId]]])(implicit ctx: Ctx.Owner): VDomModifier = {
    import meta._
    val inCycle = alreadyVisualizedParentIds.contains(nodeId)
    val isThread = !graph.isDeletedNow(nodeId, directParentIds) && (graph.hasChildren(nodeId) || graph.hasDeletedChildren(nodeId)) && !inCycle

    val replyFieldActive = Rx { activeReplyFields() contains (nodeId :: path) }
    val threadVisibility = Rx {
      // this is a separate Rx, to prevent rerendering of the whole thread, when only replyFieldActive changes.
      val userExpandedNodes = graph.expandedNodes(state.user().id)
      if(replyFieldActive()) ThreadVisibility.Expanded
      else if(isThread && !userExpandedNodes(nodeId)) ThreadVisibility.Collapsed
      else if(isThread) ThreadVisibility.Expanded
      else ThreadVisibility.Plain
    }

    Rx {
      threadVisibility() match {
        case ThreadVisibility.Collapsed =>
          chatMessageLine(meta, nodeId, msgControls, currentlyEditing, ThreadVisibility.Collapsed)
        case ThreadVisibility.Expanded  =>
          val children = (graph.children(nodeId) ++ graph.deletedChildren(nodeId)).toSeq.sortBy(nid => graph.nodeCreated(nid): Long)
          div(
            backgroundColor := BaseColors.pageBgLight.copy(h = NodeColor.hue(nodeId)).toHex,
            keyed(nodeId),
            chatMessageLine(meta, nodeId, msgControls, currentlyEditing, ThreadVisibility.Expanded, transformMessageCard = _ (
              boxShadow := s"0px 1px 0px 1px ${ tagColor(nodeId).toHex }",
            )),
            div(
              cls := "chat-thread",
              borderLeft := s"3px solid ${ tagColor(nodeId).toHex }",

              groupNodes(graph, children, state, currentUserId, shouldGroup)
                .map(kind => renderGroupedMessages(
                  kind.nodeIds,
                  meta.copy(
                    alreadyVisualizedParentIds = alreadyVisualizedParentIds + nodeId,
                    path = nodeId :: path,
                    directParentIds = Set(nodeId),
                  ),
                  avatarSizeThread,
                )),

              replyField(state, nodeId, directParentIds, path, activeReplyFields),

              draggableAs(state, DragItem.DisableDrag),
              dragTarget(DragItem.Chat.Thread(nodeId)),
              cursor.auto, // draggable sets cursor.move, but drag is disabled on thread background
              keyed(nodeId)
            )
          )
        case ThreadVisibility.Plain     =>
          if(inCycle)
            chatMessageLine(meta, nodeId, msgControls, currentlyEditing, ThreadVisibility.Plain, transformMessageCard = _ (
              Styles.flex,
              alignItems.center,
              freeSolid.faSyncAlt,
              paddingRight := "3px",
              backgroundColor := "#CCC",
              color := "#666",
              boxShadow := "0px 1px 0px 1px rgb(102, 102, 102, 0.45)",
            ))
          else chatMessageLine(meta, nodeId, msgControls, currentlyEditing, ThreadVisibility.Plain)
      }
    }
  }

  def replyField(state: GlobalState, nodeId: NodeId, directParentIds: Set[NodeId], path: List[NodeId], activeReplyFields: Var[Set[List[NodeId]]])(implicit ctx: Ctx.Owner): VNode = {
    val fullPath = nodeId :: path
    val active = Rx { activeReplyFields() contains fullPath }

    div(
      keyed(nodeId),
      Rx {
        if(active())
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
              onTap handleWith { activeReplyFields.update(_ - fullPath) },
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
            // before the click was finished. This does not happen with onMouseDown combined with deferred opening of the new reply field.
            onMouseDown.stopPropagation handleWith { defer { activeReplyFields.update(_ + fullPath) } }
          )
      }
    )
  }

  /// @return a vnode containing a chat header with optional name, date and avatar
  def chatMessageHeader(
    isMine: Boolean,
    nodeId: NodeId,
    graph: Graph,
    avatarSize: AvatarSize,
    showDate: Boolean = true,
  )(implicit ctx: Ctx.Owner): VNode = {
    val authorIdOpt = graph.authors(nodeId).headOption.map(_.id)
    div(
      cls := "chatmsg-header",
      keyed(nodeId),
      (avatarSize == AvatarSize.Small).ifTrue[VDomModifier](avatarDiv(isMine, authorIdOpt, avatarSize)(marginRight := "3px")),
      optAuthorDiv(isMine, nodeId, graph),
      showDate.ifTrue[VDomModifier](optDateDiv(isMine, nodeId, graph)),
    )
  }

  /// @return the actual body of a chat message
  /** Should be styled in such a way as to be repeatable so we can use this in groups */
  def chatMessageLine(meta: MessageMeta, nodeId: NodeId, msgControls: MsgControls, currentlyEditable: Var[Option[List[NodeId]]], threadVisibility: ThreadVisibility, showTags: Boolean = true, transformMessageCard: VNode => VDomModifier = identity)(
    implicit ctx: Ctx.Owner
  ): VNode = {
    import meta._

    val isDeleted = graph.isDeletedNow(nodeId, directParentIds)
    val isSelected = state.selectedNodeIds.map(_ contains nodeId)
    val node = graph.nodesById(nodeId)

    // needs to be a var, so that it can be set from the selectedNodes bar
    val editable: Var[Boolean] = localEditableVar(currentlyEditable, nodeId :: path)

    val isSynced: Rx[Boolean] = {
      // when a node is not in transit, avoid rx subscription
      val nodeInTransit = state.addNodesInTransit.now contains nodeId
      if(nodeInTransit) state.addNodesInTransit.map(nodeIds => !nodeIds(nodeId))
      else Rx(true)
    }

    val checkbox = div(
      cls := "ui checkbox fitted",
      marginLeft := "5px",
      marginRight := "3px",
      isSelected.map(_.ifTrueOption(visibility.visible)),
      input(
        tpe := "checkbox",
        checked <-- isSelected,
        onChange.checked handleWith { checked =>
          if(checked) state.selectedNodeIds.update(_ + nodeId)
          else state.selectedNodeIds.update(_ - nodeId)
        }
      ),
      label()
    )

    val messageCard = nodeCardEditable(state, node, editable = editable, state.eventProcessor.changes, newTagParentIds = directParentIds)(ctx)(
      Styles.flex,
      flexDirection.row,
      alignItems := "flex-end", //TODO SDT update
      isDeleted.ifTrueOption(cls := "node-deleted"), // TODO: outwatch: switch classes on and off via Boolean or Rx[Boolean]
      cls := "drag-feedback",

      Rx { editable().ifTrue[VDomModifier](VDomModifier(boxShadow := "0px 0px 0px 2px  rgba(65,184,255, 1)")) },

      Rx {
        val icon: VNode = if(isSynced()) freeSolid.faCheck
                          else freeRegular.faClock

        icon(width := "10px", marginBottom := "5px", marginRight := "5px", color := "#9c9c9c")
      },

      div(
        cls := "draghandle",
        paddingLeft := "8px",
        paddingRight := "8px",
        freeSolid.faGripVertical,
        color := "#b3bfca",
        alignSelf.center,
        marginLeft.auto,
      )
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
        keyed(nodeId),
        Styles.flex,

        onPress handleWith {
          state.selectedNodeIds.update(_ + nodeId)
        },
        onTap handleWith {
          val selectionModeActive = state.selectedNodeIds.now.nonEmpty
          if(selectionModeActive) state.selectedNodeIds.update(_.toggle(nodeId))
        },

        // The whole line is draggable, so that it can also be a drag-target.
        // This is currently a limit in the draggable library
        cursor.auto, // else draggableAs sets class .draggable, which sets cursor.move
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

        (state.screenSize.now != ScreenSize.Small).ifTrue[VDomModifier](checkbox(Styles.flexStatic)),
        transformMessageCard(messageCard.apply(keyed(nodeId))),
        expandCollapseButton(meta, nodeId, threadVisibility),
        showTags.ifTrue[VDomModifier](messageTags(state, graph, nodeId, alreadyVisualizedParentIds)),
        (state.screenSize.now != ScreenSize.Small).ifTrue[VDomModifier](controls(Styles.flexStatic))
      )
    )
  }

  def replyButton(implicit ctx: Ctx.Owner): VNode = {
    div(
      div(cls := "fa-fw", freeSolid.faReply),
      cursor.pointer,
    )
  }

  def editButton(implicit ctx: Ctx.Owner): VNode =
    div(
      div(cls := "fa-fw", Icons.edit),
      cursor.pointer,
    )

  def deleteButton(state: GlobalState, nodeId: NodeId, directParentIds: Set[NodeId])(implicit ctx: Ctx.Owner): VNode =
    div(
      div(cls := "fa-fw", Icons.delete),
      onTap handleWith {
        state.eventProcessor.changes.onNext(GraphChanges.delete(nodeId, directParentIds))
        state.selectedNodeIds.update(_ - nodeId)
      },
      cursor.pointer,
    )

  def undeleteButton(state: GlobalState, nodeId: NodeId, directParentIds: Set[NodeId])(implicit ctx: Ctx.Owner): VNode =
    div(
      div(cls := "fa-fw", Icons.undelete),
      onTap(GraphChanges.undelete(nodeId, directParentIds)) --> state.eventProcessor.changes,
      cursor.pointer,
    )

  def expandCollapseButton(meta: MessageMeta, nodeId: NodeId, threadVisibility: ThreadVisibility)(implicit ctx: Ctx.Owner): VNode = threadVisibility match {
    case ThreadVisibility.Expanded  =>
      div(
        cls := "collapsebutton",
        cls := "ui mini blue label", "-", marginLeft := "10px",
        onTap(GraphChanges.disconnect(Edge.Expanded)(meta.currentUserId, nodeId)) --> meta.state.eventProcessor.changes,
        cursor.pointer)
    case ThreadVisibility.Collapsed =>
      div(
        cls := "collapsebutton",
        cls := "ui mini blue label", "+" + meta.graph.children(nodeId).size, marginLeft := "10px",
        onTap(GraphChanges.connect(Edge.Expanded)(meta.currentUserId, nodeId)) --> meta.state.eventProcessor.changes,
        cursor.pointer)
    case ThreadVisibility.Plain     => div()
  }

  def zoomButton(state: GlobalState, nodeIds: Seq[NodeId])(implicit ctx: Ctx.Owner): VNode =
    div(
      div(cls := "fa-fw", Icons.zoom),
      onTap(state.viewConfig.now.copy(page = Page(nodeIds))) --> state.viewConfig,
      cursor.pointer,
    )

  private def messageTags(state: GlobalState, graph: Graph, nodeId: NodeId, alreadyVisualizedParentIds: Set[NodeId])(implicit ctx: Ctx.Owner) = {
    val directNodeTags = graph.directNodeTags((nodeId, alreadyVisualizedParentIds))
    val transitiveNodeTags = graph.transitiveNodeTags((nodeId, alreadyVisualizedParentIds))

    state.screenSize.now match {
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

  def inputField(state: GlobalState, directParentIds: Set[NodeId], submittedNewMessage: Handler[Unit] = Handler.created[Unit], focusOnInsert: Boolean = false, blurAction: String => Unit = _ => ())(implicit ctx: Ctx.Owner): VNode = {
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
        valueWithEnterWithInitial(initialValue.toObservable.collect { case Some(s) => s }) handleWith { str =>
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
        // else, these events would bubble up to global event handlers and produce a lag
        // TODO: sideEffect still has the overhead of triggering the monix scheduler, since it is implemented as an observer
        onKeyPress.stopPropagation handleWith {},
        onKeyUp.stopPropagation handleWith {},
        focusOnInsert.ifTrue[VDomModifier](onDomMount.asHtml handleWith { e => e.focus() }),
        onBlur.value handleWith { value => blurAction(value) },
        disabled <-- disableUserInput,
        rows := 1, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
        style("resize") := "none", //TODO: add resize style to scala-dom-types
        placeholder := "Write a message and press Enter to submit.",
      )
    )
  }
}
