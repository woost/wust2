package wust.webApp.views

import cats.effect.IO
import fontAwesome._
import org.scalajs.dom.raw.HTMLElement
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.sdk.{BaseColors, NodeColor}
import wust.util._
import wust.util.collection._
import wust.webApp.Icons
import wust.webApp.dragdrop.DragItem
import wust.webApp.jsdom.dateFns
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.BrowserDetect

import scala.collection.{breakOut, immutable, mutable}
import scala.scalajs.js

object ThreadOld {
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
    def nodeIndices: Seq[Int]
  }
  object ChatKind {
    case class Single(nodeIdx: Int) extends ChatKind {def nodeIndices = nodeIdx :: Nil }
    case class Group(nodeIndices: Seq[Int]) extends ChatKind
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
    alreadyVisualizedParentIndices: immutable.BitSet,
    path: List[NodeId],
    directParentIndices: immutable.BitSet,
    currentUserId: UserId,
    renderMessage: (Int, MessageMeta) => VDomModifier,
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

    val nodeIds: Rx[Seq[Int]] = Rx {
      val page = state.page()
      val graph = state.graph()
      val nodeIndices = new mutable.ArrayBuilder.ofInt
      graph.lookup.nodes.foreachIndexAndElement{ (i,node) =>
        val isContent = node match {
          case _:Node.Content => true
          case _ => false
        }
        // Be careful not to put graph lookup indices into fullGraph lookup tables!
        if(isContent && (graph.isChildOfAny(node.id, page.parentIds)))
          nodeIndices += i
      }
      nodeIndices.result().sortBy(i => graph.lookup.nodeCreated(i) : Long)
    }

    val activeReplyFields = Var(Set.empty[List[NodeId]])
    val currentlyEditable = Var(Option.empty[List[NodeId]])

    val selectedNodeIds:Var[Set[NodeId]] = Var(Set.empty[NodeId])
    def msgControls(nodeId: NodeId, meta: MessageMeta, isDeleted: Boolean, editable: Var[Boolean]): Seq[VNode] = {
      import meta._
      val state = meta.state
      val directParentIds:Array[NodeId] = directParentIndices.map(graph.lookup.nodeIds)(breakOut)
      if(isDeleted) List(undeleteButton(state, nodeId, directParentIds))
      else List(
        replyButton.apply(onTap foreach {
          activeReplyFields.update(_ + (nodeId :: meta.path))
          // we also set an Expand-edge, so that after an reply and its update the thread does not close again
          state.eventProcessor.changes.onNext(GraphChanges.connect(Edge.Expanded)(currentUserId, nodeId))
          ()
        }),
        editButton.apply(onTap foreach {
          editable() = true
          selectedNodeIds() = Set.empty[NodeId]
        }),
        deleteButton(state, nodeId, directParentIds, selectedNodeIds),
        zoomButton(state, nodeId :: Nil)
      )
    }

    def shouldGroup(graph: Graph, nodes: Seq[Int]): Boolean = {
      grouping && // grouping enabled
        // && (nodes
        //   .map(getNodeTags(graph, _, state.page.now)) // getNodeTags returns a sequence
        //   .distinct
        //   .size == 1) // tags must match
        // (nodes.forall(node => graph.authorIds(node).contains(currentUserId)) || // all nodes either mine or not mine
        // nodes.forall(node => !graph.authorIds(node).contains(currentUserId)))
        graph.lookup.authorsIdx(nodes.head).headOption.fold(false) { authorId =>
          nodes.forall(node => graph.lookup.authorsIdx(node).head == authorId)
        }
      // TODO: within a specific timespan && nodes.last.
    }


    def renderMessage(nodeIdx: Int, meta: MessageMeta)(implicit ctx: Ctx.Owner): VDomModifier = renderThread(nodeIdx, meta, shouldGroup, msgControls, activeReplyFields, currentlyEditable, selectedNodeIds)

    val submittedNewMessage = Handler.unsafe[Unit]

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
      selectedNodeIds() = Set.empty[NodeId]
    }

    val selectedSingleNodeActions: NodeId => List[VNode] = nodeId => if(state.graph.now.lookup.contains(nodeId)) {
      val path = reversePath(nodeId, state.page.now.parentIdSet, state.graph.now)
      List(
        editButton.apply(
          onTap foreach {
            currentlyEditable() = Some(path)
            selectedNodeIds() = Set.empty[NodeId]
          }
        ),
        replyButton.apply(onTap foreach { activeReplyFields.update(_ + path); clearSelectedNodeIds() }) //TODO: scroll to focused field?
      )
    } else Nil
    val selectedNodeActions: List[NodeId] => List[VNode] = nodeIds => List(
      zoomButton(state, nodeIds).apply(onTap foreach { selectedNodeIds.update(_ -- nodeIds) }),
      // SelectedNodes.deleteAllButton(state, nodeIds, selectedNodeIds),
      ???
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
        SelectedNodes[NodeId](state, nodeActions = selectedNodeActions, singleNodeActions = selectedSingleNodeActions, getNodeId = identity, selected = selectedNodeIds).apply(Styles.flexStatic, position.absolute, width := "100%"),
        chatHistory(state, nodeIds, submittedNewMessage, renderMessage = c => (a, b) => renderMessage(a, b)(c), shouldGroup = shouldGroup _, selectedNodeIds).apply(
          height := "100%",
          width := "100%",
          backgroundColor <-- state.pageStyle.map(_.bgLightColor),
        ),
        //        TagsList(state).apply(Styles.flexStatic)
      ),
      Rx {
        inputField(state, state.page().parentIdSet, submittedNewMessage, focusOnInsert = !BrowserDetect.isMobile).apply(Styles.flexStatic, padding := "3px")
      },
      registerDraggableContainer(state),
    )
  }

  def chatHistory(
    state: GlobalState,
    nodeIds: Rx[Seq[Int]],
    submittedNewMessage: Handler[Unit],
    renderMessage: Ctx.Owner => (Int, MessageMeta) => VDomModifier,
    shouldGroup: (Graph, Seq[Int]) => Boolean,
    selectedNodeIds: Var[Set[NodeId]],
  )(implicit ctx: Ctx.Owner): VNode = {

    val isScrolledToBottom = Var(true)
    val scrollableHistoryElem = Var(None: Option[HTMLElement])

    val scrollToBottomInAnimationFrame = requestSingleAnimationFrame {
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
        keyed,
        Rx {
          val page = state.page()
          val graph = state.graph()
          val user = state.user()
          val avatarSizeToplevel: AvatarSize = if(state.screenSize() == ScreenSize.Small) AvatarSize.Small else AvatarSize.Large

          if(nodeIds().isEmpty) VDomModifier(emptyChatNotice)
          else
            VDomModifier(
              groupNodes(graph, nodeIds(), state, user.id, shouldGroup)
                .map(kind => renderGroupedMessages(
                  kind.nodeIndices,
                  MessageMeta(state, graph, graph.lookup.createBitSet(page.parentIdSet), Nil, graph.lookup.createBitSet(page.parentIdSet), user.id, renderMessage(implicitly)), avatarSizeToplevel)
                ),

              draggableAs(DragItem.DisableDrag),
              cursor.auto, // draggable sets cursor.move, but drag is disabled on page background
              dragTarget(DragItem.Chat.Page(page.parentIds)),
            )
        },
        onDomPreUpdate foreach {
          scrollableHistoryElem.now.foreach { prev =>
            val wasScrolledToBottom = prev.scrollHeight - prev.clientHeight <= prev.scrollTop + 11 // at bottom + 10 px tolerance
            isScrolledToBottom() = wasScrolledToBottom
          }
        },
        onDomUpdate foreach {
          if (isScrolledToBottom.now) scrollToBottomInAnimationFrame()
        },
        managed(
          IO {
            // on page change, always scroll down
            state.page.foreach { _ =>
              isScrolledToBottom() = true
              scrollToBottomInAnimationFrame()
            }
          },
          IO { submittedNewMessage.foreach(_ => scrollToBottomInAnimationFrame()) }
        ),
      ),
      onDomMount.asHtml foreach { elem =>
        scrollableHistoryElem() = Some(elem)
        scrollToBottomInAnimationFrame()
      },
      overflow.auto,

      // tapping on background deselects
      onTap foreach { selectedNodeIds() = Set.empty[NodeId] }
    )
  }

  private def emptyChatNotice: VNode =
    h3(textAlign.center, "Nothing here yet.", paddingTop := "40%", color := "rgba(0,0,0,0.5)")

  /** returns a Seq of ChatKind instances where similar successive nodes are grouped via ChatKind.Group */
  def groupNodes(
    graph: Graph,
    nodes: Seq[Int],
    state: GlobalState,
    currentUserId: UserId,
    shouldGroup: (Graph, Seq[Int]) => Boolean
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
            kinds.dropRight(1) :+ ChatKind.Group(nodeIndices = lastNodes :+ node)
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
    nodeIds: Seq[Int],
    meta: MessageMeta,
    avatarSize: AvatarSize,
  )(
    implicit ctx: Ctx.Owner
  ): VNode = {
    import meta._

    val currNode = nodeIds.last
    val headNode = nodeIds.head
    val isMine = graph.lookup.authorsIdx(currNode).contains(graph.lookup.idToIdx(currentUserId))
    val nodeId = graph.lookup.nodeIds(headNode)

    div(
      cls := "chat-group-outer-frame",
      keyed(nodeId), // if the head-node is moved/removed, all reply-fields in this Group close. We didn't find a better key yet.
      (avatarSize != AvatarSize.Small).ifTrue[VDomModifier](avatarDiv(isMine, graph.lookup.authorsIdx(headNode).headOption.map(i => graph.lookup.nodeIds(i).asInstanceOf[UserId]), avatarSize)(marginRight := "5px")),
      div(
        keyed,
          cls := "chat-group-inner-frame",
          chatMessageHeader(isMine, headNode, graph, avatarSize),
          nodeIds.map(nid => renderMessage(nid, meta)),
      ),
    )
  }

  private def renderThread(nodeIdx: Int, meta: MessageMeta, shouldGroup: (Graph, Seq[Int]) => Boolean, msgControls: MsgControls, activeReplyFields: Var[Set[List[NodeId]]], currentlyEditing: Var[Option[List[NodeId]]], selectedNodeIds: Var[Set[NodeId]])(implicit ctx: Ctx.Owner): VDomModifier = {
    import meta._
    @deprecated("","")
    val nodeId = graph.lookup.nodeIds(nodeIdx)
    val inCycle = alreadyVisualizedParentIndices.contains(nodeIdx)
    val isThread = !graph.lookup.isDeletedNowIdx(nodeIdx, directParentIndices) && (graph.lookup.hasChildrenIdx(nodeIdx)) && !inCycle

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
          chatMessageLine(meta, nodeIdx, msgControls, currentlyEditing, selectedNodeIds, ThreadVisibility.Collapsed)
        case ThreadVisibility.Expanded  =>
          val children = (graph.lookup.childrenIdx(nodeIdx)).sortBy(idx => graph.lookup.nodeCreated(idx): Long)
          div(
            backgroundColor := BaseColors.pageBgLight.copy(h = NodeColor.hue(nodeId)).toHex,
            keyed(nodeId),
            chatMessageLine(meta, nodeIdx, msgControls, currentlyEditing, selectedNodeIds, ThreadVisibility.Expanded, transformMessageCard = _ (
              boxShadow := s"0px 1px 0px 1px ${ tagColor(nodeId).toHex }",
            )),
            div(
              cls := "chat-thread-messages",
              borderLeft := s"3px solid ${ tagColor(nodeId).toHex }",

              groupNodes(graph, children, state, currentUserId, shouldGroup)
                .map(kind => renderGroupedMessages(
                  kind.nodeIndices,
                  meta.copy(
                    alreadyVisualizedParentIndices = alreadyVisualizedParentIndices + nodeIdx,
                    path = nodeId :: path,
                    directParentIndices = immutable.BitSet(nodeIdx),
                  ),
                  avatarSizeThread,
                )),

              replyField(state, nodeId, path, activeReplyFields),

              draggableAs(DragItem.DisableDrag),
              dragTarget(DragItem.Chat.Thread(nodeId)),
              cursor.auto, // draggable sets cursor.move, but drag is disabled on thread background
              keyed(nodeId)
            )
          )
        case ThreadVisibility.Plain     =>
          if(inCycle)
            chatMessageLine(meta, nodeIdx, msgControls, currentlyEditing, selectedNodeIds, ThreadVisibility.Plain, transformMessageCard = _ (
              Styles.flex,
              alignItems.center,
              freeSolid.faSyncAlt,
              paddingRight := "3px",
              backgroundColor := "#CCC",
              color := "#666",
              boxShadow := "0px 1px 0px 1px rgb(102, 102, 102, 0.45)",
            ))
          else chatMessageLine(meta, nodeIdx, msgControls, currentlyEditing, selectedNodeIds, ThreadVisibility.Plain)
      }
    }
  }

  def replyField(state: GlobalState, nodeId: NodeId, path: List[NodeId], activeReplyFields: Var[Set[List[NodeId]]])(implicit ctx: Ctx.Owner): VNode = {
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
            inputField(state, directParentIds = Set(nodeId), focusOnInsert = true, blurAction = { value => if(value.isEmpty) activeReplyFields.update(_ - fullPath) }
            )(ctx)(
              keyed(nodeId),
              padding := "3px",
              width := "100%"
            ),
            closeButton(
              keyed,
              onTap foreach { activeReplyFields.update(_ - fullPath) },
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
            onMouseDown.stopPropagation foreach { defer { activeReplyFields.update(_ + fullPath) } }
          )
      }
    )
  }

  /// @return a vnode containing a chat header with optional name, date and avatar
  def chatMessageHeader(
    isMine: Boolean,
    nodeIdx: Int,
    graph: Graph,
    avatarSize: AvatarSize,
    showDate: Boolean = true,
  )(implicit ctx: Ctx.Owner): VNode = {
    @deprecated("","")
    val nodeId = graph.lookup.nodeIds(nodeIdx)
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
  def chatMessageLine(meta: MessageMeta, nodeIdx: Int, msgControls: MsgControls, currentlyEditable: Var[Option[List[NodeId]]], selectedNodeIds:Var[Set[NodeId]], threadVisibility: ThreadVisibility, showTags: Boolean = true, transformMessageCard: VNode => VDomModifier = identity)(
    implicit ctx: Ctx.Owner
  ): VNode = {
    import meta._

    @deprecated("","")
    val nodeId = graph.lookup.nodeIds(nodeIdx)

    val isDeleted = graph.lookup.isDeletedNowIdx(nodeIdx, directParentIndices)
    val isSelected = selectedNodeIds.map(_ contains nodeId)
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
        onChange.checked foreach { checked =>
          if(checked) selectedNodeIds.update(_ + nodeId)
          else selectedNodeIds.update(_ - nodeId)
        }
      ),
      label()
    )

    val messageCard = nodeCardEditable(state, node, editMode = editable, state.eventProcessor.changes)(ctx)(
      Styles.flex,
      flexDirection.row,
      alignItems := "flex-end", //TODO SDT update
      isDeleted.ifTrueOption(cls := "node-deleted"), // TODO: outwatch: switch classes on and off via Boolean or Rx[Boolean]
      cls := "drag-feedback",

      Rx { editable().ifTrue[VDomModifier](VDomModifier(boxShadow := "0px 0px 0px 2px  rgba(65,184,255, 1)")) },

      Rx {
        val icon: VNode = if(isSynced()) freeSolid.faCheck else freeRegular.faClock
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
        cls := "chat-row",
        keyed(nodeId),
        Styles.flex,

        onPress foreach {
          selectedNodeIds.update(_ + nodeId)
        },
        onTap foreach {
          val selectionModeActive = selectedNodeIds.now.nonEmpty
          if(selectionModeActive) selectedNodeIds.update(_.toggle(nodeId))
        },

        // The whole line is draggable, so that it can also be a drag-target.
        // This is currently a limit in the draggable library
        cursor.auto, // else draggableAs sets class .draggable, which sets cursor.move
        editable.map { editable =>
          if(editable)
            draggableAs(DragItem.DisableDrag) // prevents dragging when selecting text
          else {
            val payload = () => {
              val selection = selectedNodeIds.now
              if(selection contains nodeId)
                DragItem.Chat.Messages(selection.toSeq)
              else
                DragItem.Chat.Message(nodeId)
            }
            // payload is call by name, so it's always the current selectedNodeIds
            draggableAs(payload())
          }
        },
        dragTarget(DragItem.Chat.Message(nodeId)),

        (state.screenSize.now != ScreenSize.Small).ifTrue[VDomModifier](checkbox(Styles.flexStatic)),
        transformMessageCard(messageCard.apply(keyed(nodeId))),
        expandCollapseButton(meta, nodeId, threadVisibility),
        showTags.ifTrue[VDomModifier](messageTags(state, graph, nodeIdx, alreadyVisualizedParentIndices)),
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

  def deleteButton(state: GlobalState, nodeId: NodeId, directParentIds: Iterable[NodeId], selectedNodeIds: Var[Set[NodeId]])(implicit ctx: Ctx.Owner): VNode =
    div(
      div(cls := "fa-fw", Icons.delete),
      onTap foreach {
        state.eventProcessor.changes.onNext(GraphChanges.delete(nodeId, directParentIds))
        selectedNodeIds.update(_ - nodeId)
      },
      cursor.pointer,
    )

  def undeleteButton(state: GlobalState, nodeId: NodeId, directParentIds: Iterable[NodeId])(implicit ctx: Ctx.Owner): VNode =
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

  private def messageTags(state: GlobalState, graph: Graph, nodeIdx: Int, alreadyVisualizedParentIds: immutable.BitSet)(implicit ctx: Ctx.Owner) = {

    @deprecated("","")
    val nodeId = graph.lookup.nodeIds(nodeIdx)
    val directNodeTags:Array[Node] = graph.lookup.directNodeTags(nodeIdx, alreadyVisualizedParentIds)
    val transitiveNodeTags:Array[Node] = graph.lookup.transitiveNodeTags(nodeIdx, alreadyVisualizedParentIds)

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
            removableNodeTag(state, tag, nodeId)(Styles.flexStatic)
          }(breakOut): Seq[VDomModifier],
          transitiveNodeTags.map { tag =>
            nodeTag(state, tag)(Styles.flexStatic, cls := "transitivetag", opacity := 0.4)
          }(breakOut): Seq[VDomModifier]
        )
    }
  }

  def inputField(state: GlobalState, directParentIds: Set[NodeId], submittedNewMessage: Handler[Unit] = Handler.unsafe[Unit], focusOnInsert: Boolean = false, blurAction: String => Unit = _ => ())(implicit ctx: Ctx.Owner): VNode = {
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
        valueWithEnterWithInitial(initialValue.toObservable.collect { case Some(s) => s }) foreach { str =>
          val changes = {
            GraphChanges.addNodeWithParent(Node.MarkdownMessage(str), directParentIds)
          }

          state.eventProcessor.changes.onNext(changes)
          submittedNewMessage.onNext(Unit)
        },
        // else, these events would bubble up to global event handlers and produce a lag
        onKeyPress.stopPropagation foreach {},
        onKeyUp.stopPropagation foreach {},
        focusOnInsert.ifTrue[VDomModifier](onDomMount.asHtml --> inNextAnimationFrame(_.focus())),
        onBlur.value foreach { value => blurAction(value) },
        rows := 1, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
        resize := "none",
        placeholder := "Write a message and press Enter to submit.",
      )
    )
  }
}
