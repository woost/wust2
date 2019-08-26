package wust.webApp.views

import wust.webUtil.Ownable
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
  final case class ActivityNode(node: Node, revision: Revision)

  private val readColor = "gray"

  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val graph = GlobalState.rawGraph
    val userId = GlobalState.userId

    val activityNodes = Rx {
      calculateActivityList(graph(), focusState.focusedId, userId()).take(100) // max 100 items
    }

    div(
      keyed,
      Styles.growFull,
      overflow.auto,

      Styles.flex,
      justifyContent.center,

      div(
        cls := "activity-stream-view",
        if (BrowserDetect.isMobile) padding := "8px" else padding := "20px",

        div(
          Styles.flex,
          h3("Activity Stream"),
          markAllAsReadButton("Mark all as read", activityNodes, userId)
        ),

        div(
          cls := "activity-stream-container",
          if (BrowserDetect.isMobile) padding := "5px" else padding := "20px",

          Rx {
            if (activityNodes().isEmpty) {
              emptyNotifications
            } else {
              val currentTime = EpochMilli.now
              VDomModifier(
                activityNodes().map(renderActivityNode(graph, _, focusState.focusedId, userId(), currentTime = currentTime))
              )
            }
          },

        ),
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

  private def calculateActivityList(graph: Graph, nodeId: NodeId, userId: UserId): scala.collection.Seq[ActivityNode] = graph.idToIdxFold(nodeId)(Seq.empty[ActivityNode]) { nodeIdx =>

    val buffer = mutable.ArrayBuffer[ActivityNode]()

    dfs.foreach(_(nodeIdx), dfs.withStart, graph.childrenIdx, { nodeIdx =>
      if (UnreadComponents.nodeIsActivity(graph, nodeIdx)) {
        val node = graph.nodes(nodeIdx)
        val lastReadTime = UnreadComponents.activitiesOfNode(graph, userId, nodeIdx) { activity =>
          val revision =
            if (activity.authorship.isCreation) Revision.Create(activity.authorship.author, activity.authorship.timestamp, seen = activity.isSeen)
            else Revision.Edit(activity.authorship.author, activity.authorship.timestamp, seen = activity.isSeen)
          buffer += ActivityNode(node, revision)
        }

        graph.parentEdgeIdx.whileElement(nodeIdx) { idx =>
          val edge = graph.edges(idx).as[Edge.Child]
          if (edge.parentId == nodeId) {
            edge.data.deletedAt.foreach { ts =>
              // val isSeen = lastReadTime.exists(_ isAfterOrEqual ts)
              val revision = Revision.Delete(ts, seen = true) // looks better?
              buffer += ActivityNode(node, revision)
            }
            false
          } else true
        }
      }
    })

    buffer.sortBy(x => -x.revision.timestamp)
  }

  private def renderActivityNode(
    graph: Rx[Graph],
    activityNode: ActivityNode,
    focusedId: NodeId,
    userId: UserId,
    currentTime: EpochMilli
  ): VDomModifier = div.thunk(activityNode.node.id.toStringFast + activityNode.revision.timestamp)(activityNode)(Ownable { implicit ctx => // multiple nodes with same id...

    val nodeIdx = Rx {
      graph().idToIdxOrThrow(activityNode.node.id)
    }

    val breadCrumbs = Rx {
      BreadCrumbs(
        graph(),
        filterUpTo = Some(focusedId),
        parentId = Some(activityNode.node.id),
        parentIdAction = (nodeId: NodeId) => GlobalState.rightSidebarNode.update({
          case Some(pref) if pref.nodeId == nodeId => None
          case _                                   => Some(FocusPreference(nodeId))
        }: Option[FocusPreference] => Option[FocusPreference]),
        showOwn = false
      ).apply(color := "black")
    }

    val (doIcon, doDescription, doAuthor, isSeen): (IconDefinition, String, Option[Node.User], Boolean) = activityNode.revision match {
      case revision: Revision.Edit => (freeSolid.faEdit, s"Edited ${activityNode.node.role}", Some(revision.author), revision.seen)
      case revision: Revision.Create => (freeSolid.faPlus, s"Created ${activityNode.node.role}", Some(revision.author), revision.seen)
      case revision: Revision.Delete => (freeSolid.faTrash, s"Archived ${activityNode.node.role}", None, revision.seen)
    }

    val nodeIcon: VDomModifier = activityNode.node.role match {
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
          Components.displayUserName(author.data)
        )
        case None => VDomModifier.empty
      },

      div(
        opacity := 0.3,
        padding := "5px",
        fontSize := "2em",
        nodeIcon
      )
    )

    def timestampString(timestamp: EpochMilli) = s"${DateFns.formatDistance(new Date(timestamp), new Date(currentTime))} ago"

    VDomModifier(
      borderBottom := "1px solid rgba(0,0,0,0.1)",
      paddingTop := "15px",
      paddingBottom := "15px",

      div(
        marginTop := "15px",
        Styles.flex,
        justifyContent.flexStart,
        alignItems.flexStart,

        VDomModifier.ifTrue(isSeen)(opacity := 0.5),

        authorDecoration.append(Styles.flexStatic),

        div(
          flexGrow := 1,

          div(
            Styles.flex,
            alignItems.center,
            justifyContent.flexStart,
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
                timestampString(activityNode.revision.timestamp)
              ),

              breadCrumbs.map(_.apply(marginLeft := "-2px")), //correct some padding to align...
            ),

            // currently cannot toggle delete revision...
            VDomModifier.ifNot(activityNode.revision.isInstanceOf[Revision.Delete])(markSingleAsReadButton(activityNode, userId)(marginLeft.auto))
          ),

          div(
            nodeCard(activityNode.node, maxLength = Some(250), projectWithIcon = true).apply(
              VDomModifier.ifTrue(activityNode.revision.isInstanceOf[Revision.Delete])(cls := "node-deleted"),

              Components.sidebarNodeFocusMod(GlobalState.rightSidebarNode, activityNode.node.id)
            )
          )
        )
      )
    )
  })

  def markAllAsReadButton(text: String, activityNodes: Rx[Seq[ActivityNode]], userId: Rx[UserId]) = {
    button(
      cls := "ui tiny compact button",
      text,
      marginLeft := "auto",
      marginRight := "0px", // remove semantic ui button margin
      marginTop := "3px",
      marginBottom := "3px",
      Styles.flexStatic,

      onClickDefault.foreach {
        val now = EpochMilli.now
        val changes = GraphChanges(
          addEdges = unreadNodeIds(activityNodes.now).map(nodeId => Edge.Read(nodeId, EdgeData.Read(now), userId.now))
        )

        GlobalState.submitChanges(changes)
        ()
      }
    )
  }

  def markSingleAsReadButton(activityNode: ActivityNode, userId: UserId)  = {
    div(
      if (activityNode.revision.seen) VDomModifier(
        freeRegular.faCircle,
        color := readColor
      )
      else VDomModifier(
        freeSolid.faCircle,
        color := Colors.unread
      ),

      onClickDefault.foreach {
        val changes = if (activityNode.revision.seen) {
          GraphChanges.from(delEdges = GlobalState.graph.now.readEdgeIdx.flatMap[Edge.Read](GlobalState.graph.now.idToIdxOrThrow(activityNode.node.id)) { idx =>
            val edge = GlobalState.graph.now.edges(idx).as[Edge.Read]
            if (edge.userId == userId) Array(edge) else Array.empty
          })
        } else {
          GraphChanges(
            addEdges = Array(Edge.Read(activityNode.node.id, EdgeData.Read(EpochMilli.now), userId))
          )
        }

        GlobalState.submitChanges(changes)
        ()
      }
    ),
  }

  private def unreadNodeIds(activityNodes: Seq[ActivityNode]): Array[NodeId] = {
    val unreadNodeIds = distinctBuilder[NodeId, Array]

    activityNodes.foreach { activityNode =>
      if (!activityNode.revision.seen) unreadNodeIds += activityNode.node.id
    }

    unreadNodeIds.result
  }
}
