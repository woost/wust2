package wust.webApp.views

import wust.webApp.dragdrop.{ DragContainer, DragItem }
import fontAwesome.{ freeSolid, freeRegular }
import SharedViewElements._
import dateFns.DateFns
import wust.webApp.{ BrowserDetect, Icons, ItemProperties }
import wust.webApp.Icons
import outwatch.dom._
import wust.sdk.{ BaseColors, NodeColor }
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import wust.webApp.views.Elements._
import monix.reactive.subjects.{ BehaviorSubject, PublishSubject }
import rx._
import wust.api.AuthUser
import wust.css.{ Styles, ZIndex }
import wust.graph._
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{ FocusState, GlobalState }
import wust.webApp.views.Components._
import wust.util._
import wust.util.macros.InlineList
import flatland._

import scala.collection.{ breakOut, mutable }
import scala.scalajs.js.Date

// Unread view, this view is for showing all new unread items in the current page.
// It shows the node roles: Message, Task, Notes, Project
object NotificationView {

  sealed trait Revision {
    def timestamp: EpochMilli
  }
  object Revision {
    case class Delete(timestamp: EpochMilli) extends Revision
    case class Edit(author: Node.User, timestamp: EpochMilli, seen: Boolean) extends Revision
    case class Create(author: Node.User, timestamp: EpochMilli, seen: Boolean) extends Revision
  }
  case class UnreadNode(nodeIdx: Int, newRevisions: Array[Revision])

  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    val renderTime = EpochMilli.now

    div(
      keyed,
      Styles.growFull,
      overflow.auto,
      padding := "20px",
      Rx {
        val graph = state.rawGraph()
        val user = state.user()

        val unreadNodes = calculateNewNodes(graph, user, renderTime = renderTime)

        val currentTime = EpochMilli.now
        val sortedUnreadNodes = unreadNodes.sortBy(n => -graph.nodeModified(n.nodeIdx)).take(200)

        val unreadNodesByParent = Array.fill[Array[UnreadNode]](graph.nodes.length)(Array.empty)
        sortedUnreadNodes.foreach { n =>
          graph.parentsIdx.foreachElement(n.nodeIdx) { parentIdx =>
            unreadNodesByParent(parentIdx) = unreadNodesByParent(parentIdx) :+ n
          }
        }
        //TODO: sort unreadNodesByParent by latest from bucket

        VDomModifier(
          if (unreadNodes.isEmpty)
            h3(
            textAlign.center,
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
                cls := "ui tiny compact button",
                "Mark everything as read",

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
          unreadNodesByParent.mapWithIndex { (idx, nodes) =>
            if (nodes.isEmpty) VDomModifier.empty
            else renderUnreadNode(state, graph, nodes, parentId = graph.nodeIds(idx), focusedId = focusState.focusedId, renderTime = renderTime, currentTime = currentTime)
          }
        )
      },
    )
  }

  def existingNewNodes(graph: Graph, user: AuthUser): Boolean = {
    graph.nodes.foreach { node =>
      if (InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task, NodeRole.Note, NodeRole.Project)(node.role)) graph.idToIdxGet(node.id).foreach { nodeIdx =>
        val readTimes = graph.readEdgeIdx(nodeIdx).flatMap { edgeIdx =>
          val edge = graph.edges(edgeIdx).asInstanceOf[Edge.Read]
          if (edge.userId == user.id) Some(edge.data.timestamp)
          else None
        }
        val lastReadTime = if (readTimes.isEmpty) None else Some(readTimes.max)
        graph.sortedAuthorshipEdgeIdx.foreachElement(nodeIdx) { edgeIdx =>
          val edge = graph.edges(edgeIdx).asInstanceOf[Edge.Author]
          if (lastReadTime.forall(_ < edge.data.timestamp)) {
            return true
          }
        }
      }
    }
    false
  }

  private def calculateNewNodes(graph: Graph, user: AuthUser, renderTime: EpochMilli): Array[UnreadNode] = {
    val unreadNodes = Array.newBuilder[UnreadNode]

    graph.nodes.foreach { node =>
      if (InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task, NodeRole.Note, NodeRole.Project)(node.role)) graph.idToIdxGet(node.id).foreach { nodeIdx =>
        val readTimes = graph.readEdgeIdx(nodeIdx).flatMap { edgeIdx =>
          val edge = graph.edges(edgeIdx).asInstanceOf[Edge.Read]
          if (edge.userId == user.id) Some(edge.data.timestamp)
          else None
        }
        val lastReadTime = if (readTimes.isEmpty) None else Some(readTimes.max)
        val newRevisionsBuilder = Array.newBuilder[Revision]
        var isFirst = true
        graph.sortedAuthorshipEdgeIdx.foreachElement(nodeIdx) { edgeIdx =>
          val edge = graph.edges(edgeIdx).asInstanceOf[Edge.Author]
          val readDuringRender = lastReadTime.exists(_ > renderTime)
          if (readDuringRender || lastReadTime.forall(_ < edge.data.timestamp)) {
            val author = graph.nodesById(edge.userId).asInstanceOf[Node.User]
            val revision = if (isFirst) Revision.Create(author, edge.data.timestamp, seen = readDuringRender) else Revision.Edit(author, edge.data.timestamp, seen = readDuringRender)
            newRevisionsBuilder += revision
          }
          isFirst = false
        }

        val newRevisions = newRevisionsBuilder.result
        if (newRevisions.nonEmpty) {
          unreadNodes += UnreadNode(nodeIdx, newRevisions)
        }
      }
    }

    unreadNodes.result
  }
  private def renderUnreadNode(state: GlobalState, graph: Graph, unreadNodes: Array[UnreadNode], parentId: NodeId, focusedId: NodeId, renderTime: EpochMilli, currentTime: EpochMilli)(implicit ctx: Ctx.Owner): VDomModifier = {
    val breadCrumbs = Rx {
      BreadCrumbs(state, graph, state.user(), Some(focusedId), parentId = Some(parentId), parentIdAction = nodeId => state.urlConfig.update(_.focus(Page(nodeId))))
    }

    UI.segment(
      div(
        Styles.flex,
        justifyContent.spaceBetween,

        breadCrumbs,
        button(
          cls := "ui tiny compact button",
          "Mark all as read",

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
      VDomModifier(
        padding := "0px",
        table(
          cls := "ui single line table",
          border := "none",
          unreadNodes.map { unreadNode =>
            graph.nodes(unreadNode.nodeIdx) match {
              case node: Node.Content =>
                val deletedTime = graph.parentEdgeIdx(unreadNode.nodeIdx).find { idx =>
                  val edge = graph.edges(idx).asInstanceOf[Edge.Child]
                  edge.parentId == focusedId
                }.flatMap { idx =>
                  val edge = graph.edges(idx).asInstanceOf[Edge.Child]
                  edge.data.deletedAt
                }

                val newRevisionsWithDelete = deletedTime match {
                  case Some(time) => unreadNode.newRevisions :+ Revision.Delete(time)
                  case _          => unreadNode.newRevisions
                }

                var allSeen = true
                val changes = newRevisionsWithDelete.map { revision =>
                  def authorNode(node: Node.User) = div(
                    fontSize := "0.8em",
                    fontWeight.bold,
                    Styles.flex,
                    alignItems.center,
                    Components.nodeAvatar(node, size = 12).apply(Styles.flexStatic, marginRight := "3px"),
                    Components.displayUserName(node.data),
                  )
                  val (doIcon, doDescription, doAuthor, isSeen) = revision match {
                    case revision: Revision.Edit =>
                      allSeen = allSeen && revision.seen
                      (freeSolid.faEdit, s"Edited ${node.role}", Some(revision.author), revision.seen)
                    case revision: Revision.Create =>
                      allSeen = allSeen && revision.seen
                      (freeSolid.faPlus, s"Created ${node.role}", Some(revision.author), revision.seen)
                    case revision: Revision.Delete => (freeSolid.faTrash, s"Archived ${node.role}", None, true)
                  }

                  div(
                    Styles.flex,
                    alignItems.center,

                    VDomModifier.ifTrue(isSeen)(opacity := 0.5),

                    div(cls := "fa-fw", doIcon),

                    div(
                      marginLeft := "5px",
                      color.gray,
                      doDescription
                    ),

                    doAuthor.map { author =>
                      authorNode(author)(
                        marginLeft.auto,
                      )
                    },

                    div(
                      marginLeft := "5px",
                      s"${DateFns.formatDistance(new Date(revision.timestamp), new Date(currentTime))} ago"
                    ),

                  )
                }

                tr(
                  width := "100%",
                  td(
                    VDomModifier.ifTrue(allSeen)(opacity := 0.5),
                    nodeCard(node, maxLength = Some(150)).apply(
                      VDomModifier.ifTrue(deletedTime.isDefined)(cls := "node-deleted"),
                      Components.sidebarNodeFocusMod(state.rightSidebarNode, node.id),
                    ),
                  ),

                  td(
                    Styles.flex,
                    flexDirection.column,

                    changes
                  ),

                  td(
                    textAlign.right,
                    if (allSeen) VDomModifier(
                      freeRegular.faCircle,
                      color.gray,
                    )
                    else VDomModifier(
                      color := Styles.unreadColor.value,
                      freeSolid.faCircle,
                    ),

                    cursor.pointer,

                    onClick.stopPropagation.foreach {
                      val changes = if (allSeen) GraphChanges.from(delEdges = state.graph.now.readEdgeIdx.flatMap[Edge.Read](state.graph.now.idToIdxOrThrow(node.id)) { idx =>
                        val edge = state.graph.now.edges(idx).asInstanceOf[Edge.Read]
                        if (edge.userId == state.user.now.id && edge.data.timestamp >= renderTime) Array(edge) else Array.empty
                      })
                      else GraphChanges(
                        addEdges = Set(Edge.Read(node.id, EdgeData.Read(EpochMilli.now), state.user.now.id))
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
    ).apply(
        width := "100%"
      )
  }
}
