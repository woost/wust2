package wust.webApp.views

import fontAwesome._
import monix.reactive.Observable
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
      List(
      )
    }

    def renderMessage(nodeId: NodeId, meta: MessageMeta): VDomModifier = renderThread(nodeId, meta, msgControls, activeReplyFields, currentlyEditable)

    val submittedNewMessage = Handler.create[Unit].unsafeRunSync()

    var lastSelectedPath:List[NodeId] = Nil // TODO: set by clicking on a message
    def reversePath(nodeId:NodeId, pageParents:Set[NodeId], graph:Graph):List[NodeId] = {
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
            graph.parents(nodeId).maxBy(nid => graph.nodeCreated(nid):Long)
          }

        }
        nodeId :: reversePath(nextParent, pageParents, graph)
      }
    }

    def clearSelectedNodeIds() = state.selectedNodeIds() = Set.empty[NodeId]

    val selectedSingleNodeActions:NodeId => List[VNode] = _ => Nil
    val selectedNodeActions:List[NodeId] => List[VNode] =  nodeIds => List(
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
        // SelectedNodes(state, nodeActions = selectedNodeActions, singleNodeActions = selectedSingleNodeActions).apply(Styles.flexStatic, position.absolute, width := "100%"),
        chatHistory(state, nodeIds, submittedNewMessage, renderMessage = renderMessage).apply(),
      ),
      Rx { inputField(state, state.page().parentIdSet, submittedNewMessage, focusOnInsert = state.screenSize.now != ScreenSize.Small).apply(Styles.flexStatic, padding := "3px") },
    )
  }

  def chatHistory(
    state: GlobalState,
    nodeIds: Rx[Seq[NodeId]],
    submittedNewMessage: Handler[Unit],
    renderMessage: (NodeId, MessageMeta) => VDomModifier,
  )(implicit ctx: Ctx.Owner): VNode = {
    val avatarSizeToplevel: Rx[AvatarSize] = Rx { AvatarSize.Small }

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
              cursor.auto, // draggable sets cursor.move, but drag is disabled on page background
              dragTarget(DragItem.Chat.Page(page.parentIds)),
              keyed
            )
        },
      ),
      overflow.auto,
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

    nodes.map { (node) =>
      ChatKind.Single(node)
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
        keyed,
        cls := "chatmsg-group-inner-frame",
        // chatMessageHeader(isMine, headNode, graph, avatarSize),
        nodeIds.map(nid => renderMessage(nid, meta)),
      ),
  }

  private def renderThread(nodeId: NodeId, meta: MessageMeta, msgControls: MsgControls, activeReplyFields: Var[Set[List[NodeId]]], currentlyEditing: Var[Option[NodeId]])(implicit ctx: Ctx.Owner): VDomModifier = {
    import meta._

    chatMessageLine(meta, nodeId, msgControls, currentlyEditing, ThreadVisibility.Plain, transformMessageCard = identity ) }


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
  def chatMessageLine(meta: MessageMeta, nodeId: NodeId, msgControls: MsgControls, currentlyEditable: Var[Option[NodeId]], threadVisibility: ThreadVisibility, showTags: Boolean = true, transformMessageCard: VNode => VDomModifier = identity)(
    implicit ctx: Ctx.Owner
  ): VNode = {
    import meta._

    val isDeleted = graph.isDeletedNow(nodeId, directParentIds)
    val isSelected = state.selectedNodeIds.map(_ contains nodeId)
    val node = graph.nodesById(nodeId)

    val editable:Var[Boolean] = localEditableVar(currentlyEditable, nodeId)

    //TODO: how to kill rx after it became true? we do not need to be subscribed anymore thanVar
    val isSynced: Rx[Boolean] = state.addNodesInTransit
      .map(nodeIds => !nodeIds(nodeId))

    val messageCard = nodeCardEditable(state, node, editable = editable, state.eventProcessor.changes, newTagParentIds = directParentIds)(ctx)(
      Styles.flex,
      flexDirection.row,
      alignItems := "flex-end", //TODO SDT update
      isDeleted.ifTrueOption(cls := "node-deleted"), // TODO: outwatch: switch classes on and off via Boolean or Rx[Boolean]
      cls := "drag-feedback",

      // onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,


      div(
        cls := "draghandle",
        paddingLeft := "8px",
        paddingRight := "8px",
        freeSolid.faEllipsisV,
        color := "#666",
        alignSelf.center,
      )
    )


    div(
      keyed(nodeId),
      isSelected.map(_.ifTrueOption(backgroundColor := "rgba(65,184,255, 0.5)")),
      div( // this nesting is needed to get a :hover effect on the selected background
        cls := "chatmsg-line",
        Styles.flex,
        messageCard,
      )
    )
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
        focusOnInsert.ifTrue[VDomModifier](onDomMount.asHtml --> sideEffect { e => e.focus() }),
        onBlur.value --> sideEffect { value => blurAction(value) },
        disabled <-- disableUserInput,
        rows := 1, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
        style("resize") := "none", //TODO: add resize style to scala-dom-types
        placeholder := "Write a message and press Enter to submit.",
      )
    )
  }
}
