package wust.webApp.views

import wust.webApp.state._
import wust.util.collection._
import scala.scalajs.js
import wust.facades.dateFns.DateFns
import flatland._
import fontAwesome.{ IconDefinition, freeRegular, freeSolid }
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.webUtil.Elements.onClickDefault
import wust.webUtil.BrowserDetect
import wust.webUtil.outwatchHelpers._
import wust.api.AuthUser
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.Colors
import wust.util.macros.InlineList
import wust.util.collection._
import wust.webApp.Icons
import wust.webApp.state.{ FocusState, GlobalState }
import wust.webApp.views.Components._
import SharedViewElements._
import wust.webUtil.UI

import scala.collection.{ breakOut, mutable }
import scala.scalajs.js.Date

// Unread view, this view is for showing all new unread items in the current page.
// It shows the node roles: Message, Task, Notes, Project
object ActivityStream {

  sealed trait Revision {
    def timestamp: EpochMilli
    def seen: Boolean
  }
  object Revision {
    final case class Delete(timestamp: EpochMilli, seen: Boolean) extends Revision
    final case class Edit(author: Node.User, timestamp: EpochMilli, seen: Boolean) extends Revision
    final case class Create(author: Node.User, timestamp: EpochMilli, seen: Boolean) extends Revision
  }
  final case class UnreadNode(node: Node, revision: Revision)

  private val lookBackInTime: DurationMilli = DurationMilli(2L * 60 * 60 * 24) // 2 days
  private val readColor = "gray"

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val renderTime = EpochMilli.now
    val resolvedTime = EpochMilli(renderTime - lookBackInTime)

    val graph = GlobalState.rawGraph
    val userId = GlobalState.userId

    val unreadNodes = Rx {
      calculateUnreadList(graph(), focusState.focusedId, userId(), resolvedTime = resolvedTime)
    }

    div(
      keyed,
      Styles.growFull,
      overflow.auto,

      Styles.flex,
      justifyContent.center,

      div(
        cls := "activity-stream-view",
        if (BrowserDetect.isMobile) padding := "10px" else padding := "50px",

        div(
          Styles.flex,
          h3("Activity Stream"),
          markAllAsReadButton("Mark all as read", focusState.focusedId, graph, userId)
        ),

        Rx {
          if (unreadNodes().isEmpty) {
            emptyNotifications
          } else {
            val currentTime = EpochMilli.now
            VDomModifier(
              unreadNodes().map(renderUnreadNode(graph, _, focusState.focusedId, currentTime = currentTime))
            )
          }
        },

        div(height := "20px") // padding bottom workaround in flexbox
      )
    )
  }

  private def emptyNotifications: VDomModifier = {
    h3(
      textAlign.center,
      color.gray,
      "Nothing New.",
      padding := "10px"
    )
  }

  private def calculateUnreadList(graph: Graph, nodeId: NodeId, userId: UserId, resolvedTime: EpochMilli): scala.collection.Seq[UnreadNode] = graph.idToIdxFold(nodeId)(Seq.empty[UnreadNode]) { nodeIdx =>

    def authorshipRevision(nodeIdx: Int, lastAuthorship: UnreadComponents.Authorship, seen: Boolean): Revision = {
      import lastAuthorship._
      if (isCreation) Revision.Create(author, timestamp, seen = seen)
      else Revision.Edit(author, timestamp, seen = seen)
    }

    val buffer = mutable.ArrayBuffer[UnreadNode]()

    graph.descendantsIdxForeach(nodeIdx) { nodeIdx =>
      if (UnreadComponents.nodeRoleIsAccepted(graph.nodes(nodeIdx).role)) {
        val readRevision = UnreadComponents.readStatusOfNode(graph, userId, nodeIdx) match {
          case UnreadComponents.ReadStatus.SeenAt(timestamp, lastAuthorship) if timestamp > resolvedTime =>
            Some(authorshipRevision(nodeIdx, lastAuthorship, seen = true))
          case UnreadComponents.ReadStatus.Unseen(lastAuthorship) =>
            Some(authorshipRevision(nodeIdx, lastAuthorship, seen = false))
          case _ =>
            None
        }

        val deleteRevision = graph.parentEdgeIdx(nodeIdx).findMap { idx =>
          val edge = graph.edges(idx).as[Edge.Child]
          if (edge.parentId == nodeId) {
            readRevision match {
              case Some(readRevision) => edge.data.deletedAt.collect { case ts if ts > readRevision.timestamp => Revision.Delete(ts, readRevision.seen) }
              case None => edge.data.deletedAt.collect { case ts if ts > resolvedTime => Revision.Delete(ts, seen = true) }
            }
          }
          else None
        }

        val result = deleteRevision orElse readRevision
        result.foreach(rev => buffer += UnreadNode(graph.nodes(nodeIdx), rev))
      }
    }

    buffer.sortBy(x => -x.revision.timestamp)
  }

  private def renderUnreadNode(
    graph: Rx[Graph],
    unreadNode: UnreadNode,
    focusedId: NodeId,
    currentTime: EpochMilli
  )(implicit ctx: Ctx.Owner): VDomModifier = {

    val nodeIdx = Rx {
      graph().idToIdxOrThrow(unreadNode.node.id)
    }

    val breadCrumbs = Rx {
      BreadCrumbs(
        graph(),
        filterUpTo = Some(focusedId),
        parentId = Some(unreadNode.node.id),
        parentIdAction = (nodeId: NodeId) => GlobalState.rightSidebarNode.update({
          case Some(pref) if pref.nodeId == nodeId => None
          case _                                   => Some(FocusPreference(nodeId))
        }: Option[FocusPreference] => Option[FocusPreference]),
        showOwn = false
      ).apply(color := "black")
    }

    val (doIcon, doDescription, doAuthor, isSeen): (IconDefinition, String, Option[Node.User], Boolean) = unreadNode.revision match {
      case revision: Revision.Edit => (freeSolid.faEdit, "", Some(revision.author), revision.seen)
      case revision: Revision.Create => (freeSolid.faPlus, "", Some(revision.author), revision.seen)
      case revision: Revision.Delete => (freeSolid.faTrash, "", None, revision.seen)
    }

    val nodeIcon: VDomModifier = unreadNode.node.role match {
      case NodeRole.Message => Icons.message
      case NodeRole.Task    => Icons.task
      case NodeRole.Note    => Icons.note
      case NodeRole.Project => Icons.project
      case _                => VDomModifier.empty
    }

    val authorDecoration = div(
      fontWeight.bold,
      width := "150px",
      maxWidth := "150px",
      Styles.flex,
      alignItems.center,
      flexDirection.column,

      doAuthor match {
        case Some(author) => VDomModifier(
          Components.nodeAvatar(author, size = 16).apply(Styles.flexStatic),
          Components.displayUserName(author.data),
        )
        case None => VDomModifier.empty
      },

      div(
        padding := "5px",
        nodeIcon
      )
    )

    def timestampModifiers(timestamp: EpochMilli) = s"${DateFns.formatDistance(new Date(timestamp), new Date(currentTime))} ago"

    div(
      borderBottom := "1px solid rgba(0,0,0,0.1)",
      padding := "40px",
      width := "100%",

      div(
        Styles.flex,
        justifyContent.spaceBetween,
        alignItems.flexStart,
        flexWrap.wrap,
        width := "100%",

        VDomModifier.ifTrue(isSeen)(opacity := 0.5),

        authorDecoration,

        div(
          flexGrow := 1,
          marginLeft := "20px",

          div(
            Styles.flex,
            alignItems.center,
            justifyContent.spaceBetween,
            marginTop := "15px",
            marginBottom := "15px",

            div(
              color.gray,
              Styles.flex,
              alignItems.center,

              doIcon,

              div(s" $doDescription ${timestampModifiers(unreadNode.revision.timestamp)}", marginLeft := "20px"),

              breadCrumbs.map(_.apply(marginLeft := "20px")),
            ),

            markSingleAsReadButton(unreadNode)
          ),

          div(
            nodeCard(unreadNode.node, maxLength = Some(250), projectWithIcon = true).apply(
              VDomModifier.ifTrue(unreadNode.revision.isInstanceOf[Revision.Delete])(cls := "node-deleted"),

              Components.sidebarNodeFocusMod(GlobalState.rightSidebarNode, unreadNode.node.id)
            )
          )
        )
      )
    )
  }

  def markAllAsReadButton(text: String, parentId: NodeId, graph: Rx[Graph], userId: Rx[UserId]) = {
    button(
      cls := "ui tiny compact basic button",
      text,
      marginLeft := "auto",
      marginRight := "0px", // remove semantic ui button margin
      marginTop := "3px",
      marginBottom := "3px",
      Styles.flexStatic,

      onClickDefault.foreach {
        val now = EpochMilli.now
        val changes = GraphChanges(
          addEdges = calculateDeepUnreadChildren(graph.now, parentId, userId.now, renderTime = now)
            .map(nodeIdx => Edge.Read(graph.now.nodeIds(nodeIdx), EdgeData.Read(now), userId.now))(breakOut)
        )

        GlobalState.submitChanges(changes)
        ()
      }
    )
  }

  def markSingleAsReadButton(unreadNode: UnreadNode)  = {
    div(
      if (unreadNode.revision.seen) VDomModifier(
        freeRegular.faCircle,
        color := readColor,
      )
      else VDomModifier(
        freeSolid.faCircle,
        color := Colors.unread,
      ),

      onClickDefault.foreach {
        val changes = if (unreadNode.revision.seen) {
          GraphChanges.from(delEdges = GlobalState.graph.now.readEdgeIdx.flatMap[Edge.Read](GlobalState.graph.now.idToIdxOrThrow(unreadNode.node.id)) { idx =>
            val edge = GlobalState.graph.now.edges(idx).as[Edge.Read]
            if (edge.userId == GlobalState.user.now.id) Array(edge) else Array.empty
          })
        } else {
          GraphChanges(
            addEdges = Array(Edge.Read(unreadNode.node.id, EdgeData.Read(EpochMilli.now), GlobalState.user.now.id))
          )
        }

        GlobalState.submitChanges(changes)
        ()
      }
    ),
  }

  private def calculateDeepUnreadChildren(graph: Graph, parentNodeId: NodeId, userId: UserId, renderTime: EpochMilli): js.Array[Int] = {
    val unreadNodes = js.Array[Int]()

    graph.idToIdx(parentNodeId).foreach { parentNodeIdx =>
      graph.descendantsIdxForeach(parentNodeIdx) { nodeIdx =>
        UnreadComponents.readStatusOfNode(graph, userId, nodeIdx) match {
          case UnreadComponents.ReadStatus.SeenAt(timestamp, lastAuthorship) if timestamp > renderTime =>
            unreadNodes += nodeIdx
          case UnreadComponents.ReadStatus.Unseen(lastAuthorship) =>
            unreadNodes += nodeIdx
          case _ => ()
        }
      }
    }

    unreadNodes
  }
}
