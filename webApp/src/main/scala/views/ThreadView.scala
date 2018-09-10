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
        div(
          div(
            cls := "chat-history",
            padding := "20px 0 20px 20px",
            Rx {
              val graph = state.graph()
              counter += 1
              graph.nodeIds.map(kind => 
                  div( state.user.map{u => u.toString})
                ),
            }
            ),
          overflow.auto,
          )
      ),
      Rx { inputField(state, state.page().parentIdSet, Handler.create[Unit].unsafeRunSync, focusOnInsert = state.screenSize.now != ScreenSize.Small).apply(Styles.flexStatic, padding := "3px") },
    )
  }

  var counter = 0

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
          for( _ <- 0 until 200) {
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
          }
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
