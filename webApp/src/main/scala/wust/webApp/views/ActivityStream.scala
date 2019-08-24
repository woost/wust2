package wust.webApp.views

import wust.webApp.state._
import wust.util.collection._
import scala.scalajs.js
import wust.facades.dateFns.DateFns
import wust.util.algorithm.dfs
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
        if (BrowserDetect.isMobile) padding := "5px" else padding := "20px",

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

    val buffer = mutable.ArrayBuffer[UnreadNode]()

    dfs.foreach(_(nodeIdx), dfs.withStart, graph.childrenIdx, { nodeIdx =>
      if (UnreadComponents.nodeIsActivity(graph, nodeIdx)) {
        val node = graph.nodes(nodeIdx)
        val lastReadTime = UnreadComponents.activitiesOfNode(graph, userId, nodeIdx) { activity =>
          val revision =
            if (activity.authorship.isCreation) Revision.Create(activity.authorship.author, activity.authorship.timestamp, seen = activity.isSeen)
            else Revision.Edit(activity.authorship.author, activity.authorship.timestamp, seen = activity.isSeen)
          buffer += UnreadNode(node, revision)
        }

        graph.parentEdgeIdx.whileElement(nodeIdx) { idx =>
          val edge = graph.edges(idx).as[Edge.Child]
          if (edge.parentId == nodeId) {
            edge.data.deletedAt.foreach { ts =>
              // val isSeen = lastReadTime.exists(_ isAfterOrEqual ts)
              val revision = Revision.Delete(ts, seen = true) // looks better?
              buffer += UnreadNode(node, revision)
            }
            false
          } else true
        }
      }
    })

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
      case revision: Revision.Edit => (freeSolid.faEdit, s"Edited ${unreadNode.node.role}", Some(revision.author), revision.seen)
      case revision: Revision.Create => (freeSolid.faPlus, s"Created ${unreadNode.node.role}", Some(revision.author), revision.seen)
      case revision: Revision.Delete => (freeSolid.faTrash, s"Archived ${unreadNode.node.role}", None, revision.seen)
    }

    val nodeIcon: VDomModifier = unreadNode.node.role match {
      case NodeRole.Message => Icons.message
      case NodeRole.Task    => Icons.task
      case NodeRole.Note    => Icons.note
      case NodeRole.Project => Icons.project
      case _                => VDomModifier.empty
    }

    val authorWidth = if (BrowserDetect.isMobile) "40px" else "90px"
    val authorDecoration = div(
      fontSize.xSmall,
      textAlign.center,
      marginRight := "5px",
      flexShrink := 0,
      fontWeight.bold,
      width := "100px",
      maxWidth := "100px",
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

    def timestampString(timestamp: EpochMilli) = s"${DateFns.formatDistance(new Date(timestamp), new Date(currentTime))} ago"

    div(
      borderBottom := "1px solid rgba(0,0,0,0.1)",
      paddingTop := "10px",
      paddingBottom := "10px",
      width := "100%",

      div(
        marginTop := "15px",
        Styles.flex,
        justifyContent.spaceBetween,
        alignItems.flexStart,
        width := "100%",

        VDomModifier.ifTrue(isSeen)(opacity := 0.5),

        authorDecoration,

        div(
          maxWidth := "100vw",
          flexGrow := 1,

          div(
            width := "100%",
            Styles.flex,
            alignItems.center,
            justifyContent.spaceBetween,
            marginBottom := "10px",

            div(
              color.gray,
              Styles.flex,
              alignItems.center,
              flexWrap.wrap,

              div(
                marginRight := "10px",
                Styles.flex,
                alignItems.center,
                renderFontAwesomeIcon(doIcon).apply(marginRight := "10px"),
                doDescription
              ),

              div(
                marginRight := "10px",
                timestampString(unreadNode.revision.timestamp)
              ),

              breadCrumbs.map(_.apply(flexWrap.wrap, marginLeft := "-2px")), //correct some padding to align...
            ),

            // currently cannot toggle delete revision...
            VDomModifier.ifNot(unreadNode.revision.isInstanceOf[Revision.Delete])(markSingleAsReadButton(unreadNode))
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
