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

  object LatestChangeInGroupOrdering extends Ordering[(Int, Array[UnreadNode])] {
    def compare(a: (Int, Array[UnreadNode]), b: (Int, Array[UnreadNode])) = {
      val aValue = a._2(0).newRevisions(0).timestamp
      val bValue = b._2(0).newRevisions(0).timestamp
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
      padding := "20px",
      Rx {
        val graph = state.rawGraph()
        val user = state.user()
        val page = state.page()

        val unreadNodes: Array[UnreadNode] = page.parentId.fold(Array.empty[UnreadNode])(pageParentId => calculateNewNodes(graph, pageParentId, user, renderTime = renderTime)).take(200)

        val currentTime = EpochMilli.now

        val unreadNodesByParentBuilder = new Array[mutable.ArrayBuilder[UnreadNode]](graph.nodes.length) // default = null
        unreadNodes.foreach { n =>
          var hasParent = false
          graph.parentsIdx.foreachElement(n.nodeIdx) { parentIdx =>
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

  def existingNewNodes(graph: Graph, parentNodeId: NodeId, user: AuthUser): Boolean = {
    graph.idToIdxGet(parentNodeId).foreach { parentNodeIdx =>
      graph.descendantsIdx(parentNodeIdx).foreachElement { nodeIdx =>
        val node = graph.nodes(nodeIdx)
        node match {
          case node: Node.Content if InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task, NodeRole.Note, NodeRole.Project)(node.role) =>
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
          case _ =>
        }
      }
    }

    false
  }

  private def calculateNewNodes(graph: Graph, parentNodeId: NodeId, user: AuthUser, renderTime: EpochMilli): Array[UnreadNode] = {
    val unreadNodes = Array.newBuilder[UnreadNode]

    graph.idToIdxGet(parentNodeId).foreach { parentNodeIdx =>
      graph.descendantsIdx(parentNodeIdx).foreachElement { nodeIdx =>
        val node = graph.nodes(nodeIdx)
        node match {
          case node: Node.Content if InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task, NodeRole.Note, NodeRole.Project)(node.role) =>
            val readTimes = graph.readEdgeIdx(nodeIdx).flatMap { edgeIdx =>
              val edge = graph.edges(edgeIdx).asInstanceOf[Edge.Read]
              if (edge.userId == user.id) Some(edge.data.timestamp)
              else None
            }
            val lastReadTime = if (readTimes.isEmpty) None else Some(readTimes.max)

            val newSortedRevisionsBuilder = Array.newBuilder[Revision]
            var isFirst = true
            graph.sortedAuthorshipEdgeIdx.foreachElement(nodeIdx) { edgeIdx =>
              val edge = graph.edges(edgeIdx).asInstanceOf[Edge.Author]
              val readDuringRender = lastReadTime.exists(_ > renderTime)
              if (readDuringRender || lastReadTime.forall(_ < edge.data.timestamp)) {
                val author = graph.nodesById(edge.userId).asInstanceOf[Node.User]
                val revision = if (isFirst) Revision.Create(author, edge.data.timestamp, seen = readDuringRender) else Revision.Edit(author, edge.data.timestamp, seen = readDuringRender)
                newSortedRevisionsBuilder += revision
              }
              isFirst = false
            }

            val newSortedRevisions = newSortedRevisionsBuilder.result.reverse //TODO: Performance: instead of reversed, build up newRevisionsBuilder reversed
            if (newSortedRevisions.nonEmpty) {
              unreadNodes += UnreadNode(nodeIdx, newSortedRevisions)
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
          cls := "ui fixed table",
          border := "none",
          unreadNodes.map { unreadNode =>
            graph.nodes(unreadNode.nodeIdx) match {
              case node: Node.Content => // node is always Content. See calculateNewNodes.
                val (revisionTable, allSeen, deletedTime) = renderRevisions(graph, unreadNode, node, focusedId, currentTime)

                tr(
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
                    width := "40px",
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

  private def renderRevisions(
    graph: Graph,
    unreadNode: UnreadNode,
    node: Node.Content,
    focusedId: NodeId,
    currentTime: EpochMilli
  ): (VNode, Boolean, Option[EpochMilli]) = {
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

    val tableNode = table(
      cls := "ui compact fixed table",
      cls := "no-inner-table-borders",
      border := "none",
      newRevisionsWithDelete.map { revision =>
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

        tr(
          VDomModifier.ifTrue(isSeen)(opacity := 0.5),

          td(
            color.gray,
            span(
              display.inlineBlock,
              cls := "fa-fw",
              doIcon,
              marginRight := "5px",
            ),
            doDescription
          ),

          td(
            doAuthor.map { author =>
              authorNode(author)(
                marginLeft.auto,
              )
            }
          ),

          td(
            Styles.flexStatic,
            marginLeft := "5px",
            s"${DateFns.formatDistance(new Date(revision.timestamp), new Date(currentTime))} ago"
          ),
        )
      }
    )

    (tableNode, allSeen, deletedTime)
  }
}
