package wust.webApp.views

import wust.facades.dateFns.DateFns
import flatland._
import fontAwesome.{ IconDefinition, freeRegular, freeSolid }
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.webUtil.BrowserDetect
import wust.webUtil.outwatchHelpers._
import wust.api.AuthUser
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.Colors
import wust.util.macros.InlineList
import wust.webApp.Icons
import wust.webApp.state.{ FocusState, GlobalState }
import wust.webApp.views.Components._

import scala.collection.{ breakOut, mutable }
import scala.scalajs.js.Date

// Unread view, this view is for showing all new unread items in the current page.
// It shows the node roles: Message, Task, Notes, Project
object NotificationView {

  sealed trait Revision {
    def timestamp: EpochMilli
  }
  object Revision {
    final case class Delete(timestamp: EpochMilli) extends Revision
    final case class Edit(author: Node.User, timestamp: EpochMilli, seen: Boolean) extends Revision
    final case class Create(author: Node.User, timestamp: EpochMilli, seen: Boolean) extends Revision
  }
  final case class UnreadNode(nodeIdx: Int, newRevisions: List[Revision])

  object LatestChangeInGroupOrdering extends Ordering[(Int, Array[UnreadNode])] {
    def compare(a: (Int, Array[UnreadNode]), b: (Int, Array[UnreadNode])) = {
      val aValue = a._2(0).newRevisions.head.timestamp
      val bValue = b._2(0).newRevisions.head.timestamp
      -aValue compare -bValue
    }
  }
  object LatestChangeInUnreadNodeOrdering extends Ordering[UnreadNode] {
    def compare(a: UnreadNode, b: UnreadNode) = -a.newRevisions(0).timestamp compare -b.newRevisions(0).timestamp
  }

  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val renderTime = EpochMilli.now

    div(
      keyed,
      Styles.growFull,
      overflow.auto,
      if (BrowserDetect.isMobile) padding := "8px" else padding := "20px",

      Rx {
        val graph = state.rawGraph()
        val user = state.user()
        val page = state.page()

        val allUnreadNodes: Array[UnreadNode] = page.parentId.fold(Array.empty[UnreadNode])(pageParentId => calculateUnreadNodes(graph, pageParentId, user, renderTime = renderTime))
        val unreadNodes: Array[UnreadNode] = allUnreadNodes.take(200)

        val currentTime = EpochMilli.now

        val unreadNodesByParentBuilder = new Array[mutable.ArrayBuilder[UnreadNode]](graph.nodes.length) // default = null
        unreadNodes.foreach { n =>
          var hasParent = false
          graph.workspacesForNode(n.nodeIdx).foreach { parentIdx =>
            hasParent = true
            if (unreadNodesByParentBuilder(parentIdx) == null) unreadNodesByParentBuilder(parentIdx) = new mutable.ArrayBuilder.ofRef[UnreadNode]
            unreadNodesByParentBuilder(parentIdx) += n
          }

          if (!hasParent) {
            if (unreadNodesByParentBuilder(n.nodeIdx) == null) unreadNodesByParentBuilder(n.nodeIdx) = new mutable.ArrayBuilder.ofRef[UnreadNode]
            unreadNodesByParentBuilder(n.nodeIdx) += n
          }
        }
        val unreadNodesByParentSorted: Array[(Int, Array[UnreadNode])] = {
          val result = new mutable.ArrayBuilder.ofRef[(Int, Array[UnreadNode])]
          unreadNodesByParentBuilder.foreachIndexAndElement{ (parentIdx, unreadNodesBuilder) =>
            if (unreadNodesBuilder != null) {
              val unreadNodes = unreadNodesBuilder.result()
              scala.util.Sorting.quickSort(unreadNodes)(LatestChangeInUnreadNodeOrdering)
              result += (parentIdx -> unreadNodes)
            }
          }
          val sortedResult = result.result
          scala.util.Sorting.quickSort(sortedResult)(LatestChangeInGroupOrdering)
          sortedResult
        }

        VDomModifier(
          if (unreadNodes.isEmpty)
            h3(
            textAlign.center,
            color.gray,
            "Nothing New.",
            padding := "10px"
          )
          else
            div(
              Styles.flex,
              h3("What's new?"),
              button(
                alignSelf.center,
                marginLeft.auto,
                marginRight := "0px",
                cls := "ui tiny compact button",
                "Mark everything as read",

                cursor.pointer,

                onClick.stopPropagation.foreach {
                  val changes = GraphChanges(
                    addEdges = allUnreadNodes.map(n => Edge.Read(state.graph.now.nodeIds(n.nodeIdx), EdgeData.Read(EpochMilli.now), state.user.now.id))(breakOut)
                  )

                  state.eventProcessor.changes.onNext(changes)
                  ()
                }
              )
            ),
          // render outer groups
          unreadNodesByParentSorted.map {
            case (idx, nodes) =>
              VDomModifier.ifTrue(nodes != null)(
                renderUnreadGroup(state, graph, nodes, parentId = graph.nodeIds(idx), focusedId = focusState.focusedId, renderTime = renderTime, currentTime = currentTime)
              )
          }
        )
      },
      div(height := "20px") // padding bottom workaround in flexbox
    )
  }

  def notificationsButton(state: GlobalState, nodeId: NodeId, modifiers: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): EmitterBuilder[View.Visible, VDomModifier] = EmitterBuilder.ofModifier { sink =>

    val haveUnreadNotifications = Rx {
      val graph = state.graph()
      val user = state.user()
      existingNewNodes(graph, nodeId, user)
    }

    val channelNotification = Rx {
      VDomModifier.ifTrue(haveUnreadNotifications())(
        button(
          cls := "ui compact inverted button",
          Icons.notifications,
          onClick.stopPropagation(View.Notifications) --> sink,
          modifiers,
        )
      )
    }
    channelNotification
  }

  private def findLastReadTime(graph: Graph, nodeIdx: Int, userId: UserId): EpochMilli = {
    var lastReadTime = EpochMilli.min
    graph.readEdgeIdx.foreachElement(nodeIdx) { edgeIdx =>
      val edge = graph.edges(edgeIdx).as[Edge.Read]
      if (edge.userId == userId && lastReadTime < edge.data.timestamp) {
        lastReadTime = edge.data.timestamp
      }
    }
    lastReadTime
  }

  private def existingNewNodes(graph: Graph, parentNodeId: NodeId, user: AuthUser): Boolean = {
    graph.idToIdx(parentNodeId).foreach { parentNodeIdx =>
      graph.descendantsIdxForeach(parentNodeIdx) { nodeIdx =>
        val node = graph.nodes(nodeIdx)
        node match {
          case node: Node.Content if InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task, NodeRole.Note, NodeRole.Project)(node.role) =>
            val lastReadTime = findLastReadTime(graph, nodeIdx, user.id)
            graph.sortedAuthorshipEdgeIdx.foreachElement(nodeIdx) { edgeIdx =>
              val edge = graph.edges(edgeIdx).as[Edge.Author]
              if (lastReadTime < edge.data.timestamp) {
                return true
              }
            }
          case _ =>
        }
      }
    }

    false
  }

  private def calculateUnreadNodes(graph: Graph, parentNodeId: NodeId, user: AuthUser, renderTime: EpochMilli): Array[UnreadNode] = {
    val unreadNodes = Array.newBuilder[UnreadNode]

    graph.idToIdx(parentNodeId).foreach { parentNodeIdx =>
      graph.descendantsIdxForeach(parentNodeIdx) { nodeIdx =>
        val node = graph.nodes(nodeIdx)
        node match {
          case node: Node.Content if UnreadComponents.nodeIsUnread(graph, user.id, nodeIdx) =>

            val lastReadTime = findLastReadTime(graph, nodeIdx, user.id)
            val isReadDuringRender = lastReadTime > renderTime
            if (isReadDuringRender) { // show only last revision if read during this rendering
              val sliceLength = graph.sortedAuthorshipEdgeIdx.sliceLength(nodeIdx)
              if (sliceLength > 0) {
                val edgeIdx = graph.sortedAuthorshipEdgeIdx(nodeIdx, sliceLength - 1)
                val edge = graph.edges(edgeIdx).as[Edge.Author]
                val author = graph.nodes(graph.edgesIdx.b(edgeIdx)).as[Node.User]
                if (author.id != user.id) {
                  val isFirst = sliceLength == 1
                  val revision = if (isFirst) Revision.Create(author, edge.data.timestamp, seen = true) else Revision.Edit(author, edge.data.timestamp, seen = true)
                  unreadNodes += UnreadNode(nodeIdx, revision :: Nil)
                }
              }
            } else if (BrowserDetect.isMobile) { // just take last revision on mobile
              val sliceLength = graph.sortedAuthorshipEdgeIdx.sliceLength(nodeIdx)
              if (sliceLength > 0) {
                val edgeIdx = graph.sortedAuthorshipEdgeIdx(nodeIdx, sliceLength - 1)
                val edge = graph.edges(edgeIdx).as[Edge.Author]
                if (lastReadTime < edge.data.timestamp) {
                  val author = graph.nodes(graph.edgesIdx.b(edgeIdx)).as[Node.User]
                  val isFirst = sliceLength == 1
                  val revision = if (isFirst) Revision.Create(author, edge.data.timestamp, seen = false) else Revision.Edit(author, edge.data.timestamp, seen = false)
                  unreadNodes += UnreadNode(nodeIdx, revision :: Nil)
                }
              }
            } else {
              var newSortedRevisions = List.empty[Revision]
              var isFirst = true
              graph.sortedAuthorshipEdgeIdx.foreachElement(nodeIdx) { edgeIdx =>
                val edge = graph.edges(edgeIdx).as[Edge.Author]
                def isUnread = lastReadTime < edge.data.timestamp
                if (isUnread) {
                  val author = graph.nodes(graph.edgesIdx.b(edgeIdx)).as[Node.User]
                  val revision = if (isFirst) Revision.Create(author, edge.data.timestamp, seen = false) else Revision.Edit(author, edge.data.timestamp, seen = false)
                  newSortedRevisions ::= revision
                }
                isFirst = false
              }

              if (newSortedRevisions.nonEmpty) {
                unreadNodes += UnreadNode(nodeIdx, newSortedRevisions)
              }
            }
          case _ =>
        }
      }
    }

    unreadNodes.result
  }

  private def renderUnreadGroup(
    state: GlobalState,
    graph: Graph,
    unreadNodes: Array[UnreadNode],
    parentId: NodeId,
    focusedId: NodeId,
    renderTime: EpochMilli,
    currentTime: EpochMilli
  )(implicit ctx: Ctx.Owner): VDomModifier = {
    val breadCrumbs = Rx {
      BreadCrumbs(state, graph, state.user(), Some(focusedId), parentId = Some(parentId), parentIdAction = nodeId => state.urlConfig.update(_.focus(Page(nodeId))))
    }

    VDomModifier(
      div(
        cls := "notifications-header",

        breadCrumbs,
        button(
          cls := "ui tiny compact button",
          "Mark all as read",
          marginLeft := "auto",
          marginRight := "0px", // remove semantic ui button margin

          cursor.pointer,

          onClick.stopPropagation.foreach {
            val changes = GraphChanges(
              addEdges = unreadNodes.map(n => Edge.Read(state.graph.now.nodeIds(n.nodeIdx), EdgeData.Read(EpochMilli.now), state.user.now.id))(breakOut)
            )

            state.eventProcessor.changes.onNext(changes)
            ()
          }
        )
      ),
      div(
        cls := "ui segment",
        marginTop := "0px", // remove semantic ui marginTop
        table(
          cls := "ui fixed table",
          border := "none",
          unreadNodes.map { unreadNode =>
            graph.nodes(unreadNode.nodeIdx) match {
              case node: Node.Content => // node is always Content. See calculateUnreadNodes.
                val (revisionTable, allSeen, deletedTime) = renderRevisions(graph, unreadNode, node, focusedId, currentTime)

                tr(
                  padding := "0px",
                  td(
                    cls := "top aligned",
                    width := "400px",
                    VDomModifier.ifTrue(allSeen)(opacity := 0.5),
                    nodeCard(node, maxLength = Some(150)).apply(
                      VDomModifier.ifTrue(deletedTime.isDefined)(cls := "node-deleted"),
                      Components.sidebarNodeFocusMod(state.rightSidebarNode, node.id),
                    ),
                  ),

                  td(
                    cls := "top aligned",
                    revisionTable,
                  ),

                  td(
                    cls := "top aligned",
                    width := "20px",

                    //TODO: hack for having a better layout on mobile with this table
                    if (BrowserDetect.isMobile)
                      marginTop := "-6px"
                    else
                      padding := "5px",

                    textAlign.right,
                    if (allSeen) VDomModifier(
                      freeRegular.faCircle,
                      color.gray,
                    )
                    else VDomModifier(
                      color := Colors.unread,
                      freeSolid.faCircle,
                    ),

                    cursor.pointer,

                    onClick.stopPropagation.foreach {
                      val changes = if (allSeen) GraphChanges.from(delEdges = state.graph.now.readEdgeIdx.flatMap[Edge.Read](state.graph.now.idToIdxOrThrow(node.id)) { idx =>
                        val edge = state.graph.now.edges(idx).as[Edge.Read]
                        if (edge.userId == state.user.now.id && edge.data.timestamp >= renderTime) Array(edge) else Array.empty
                      })
                      else GraphChanges(
                        addEdges = Array(Edge.Read(node.id, EdgeData.Read(EpochMilli.now), state.user.now.id))
                      )

                      state.eventProcessor.changes.onNext(changes)
                      ()
                    }
                  )
                )

              case _ => VDomModifier.empty
            }
          }
        )
      )
    )
  }

  private def renderRevisions(
    graph: Graph,
    unreadNode: UnreadNode,
    node: Node.Content,
    focusedId: NodeId,
    currentTime: EpochMilli
  ): (VNode, Boolean, Option[EpochMilli]) = {
    val deletedTime = graph.parentEdgeIdx(unreadNode.nodeIdx).find { idx =>
      val edge = graph.edges(idx).as[Edge.Child]
      edge.parentId == focusedId
    }.flatMap { idx =>
      val edge = graph.edges(idx).as[Edge.Child]
      edge.data.deletedAt
    }

    val newRevisionsWithDelete = deletedTime match {
      case Some(time) => unreadNode.newRevisions :+ Revision.Delete(time)
      case _          => unreadNode.newRevisions
    }

    var allSeen = true

    def revisionVisuals(revision: Revision): (IconDefinition, String, Option[Node.User], Boolean) = {
      revision match {
        case revision: Revision.Edit =>
          allSeen = allSeen && revision.seen
          (freeSolid.faEdit, s"Edited ${node.role}", Some(revision.author), revision.seen)
        case revision: Revision.Create =>
          allSeen = allSeen && revision.seen
          (freeSolid.faPlus, s"Created ${node.role}", Some(revision.author), revision.seen)
        case revision: Revision.Delete => (freeSolid.faTrash, s"Archived ${node.role}", None, true)
      }
    }

    def descriptionModifiers(doIcon: IconDefinition, doDescription: String) = VDomModifier(
      color.gray,
      span(
        display.inlineBlock,
        cls := "fa-fw",
        doIcon,
        marginRight := "5px",
      ),
      doDescription
    )

    def authorModifiers(doAuthor: Option[Node.User]) = VDomModifier(
      doAuthor.map { author =>
        div(
          fontSize := "0.8em",
          fontWeight.bold,
          Styles.flex,
          alignItems.center,
          Components.nodeAvatar(author, size = 12).apply(Styles.flexStatic, marginRight := "3px"),
          Components.displayUserName(author.data),
          marginLeft.auto,
        )
      }
    )

    def timestampModifiers(timestamp: EpochMilli) = VDomModifier(
      fontSize.smaller,
      s"${DateFns.formatDistance(new Date(timestamp), new Date(currentTime))} ago"
    )

    val tableNode = if (BrowserDetect.isMobile) div(
      newRevisionsWithDelete.map { revision =>
        val (doIcon, doDescription, doAuthor, isSeen) = revisionVisuals(revision)

        div(
          Styles.flex,
          justifyContent.spaceBetween,
          flexWrap.wrap,

          VDomModifier.ifTrue(isSeen)(opacity := 0.5),

          div(authorModifiers(doAuthor)),
          div(descriptionModifiers(doIcon, doDescription)),
          div(timestampModifiers(revision.timestamp))
        )
      }
    )
    else table(
      cls := "ui compact fixed table",
      cls := "no-inner-table-borders",
      border := "none",
      newRevisionsWithDelete.map { revision =>
        val (doIcon, doDescription, doAuthor, isSeen) = revisionVisuals(revision)

        tr(
          VDomModifier.ifTrue(isSeen)(opacity := 0.5),

          td(authorModifiers(doAuthor)),
          td(descriptionModifiers(doIcon, doDescription)),
          td(timestampModifiers(revision.timestamp))
        )
      }
    )

    (tableNode, allSeen, deletedTime)
  }
}
