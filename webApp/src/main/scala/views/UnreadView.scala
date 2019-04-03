package wust.webApp.views

import wust.webApp.dragdrop.{DragContainer, DragItem}
import fontAwesome.freeSolid
import SharedViewElements._
import dateFns.DateFns
import wust.webApp.{BrowserDetect, Icons, ItemProperties}
import wust.webApp.Icons
import outwatch.dom._
import wust.sdk.{BaseColors, NodeColor}
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import wust.webApp.views.Elements._
import monix.reactive.subjects.{BehaviorSubject, PublishSubject}
import rx._
import wust.api.AuthUser
import wust.css.{Styles, ZIndex}
import wust.graph._
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState}
import wust.webApp.views.Components._
import wust.util._
import wust.util.macros.InlineList

import scala.collection.mutable
import scala.scalajs.js.Date

// Unread view, this view is for showing all new unread items in the current page.
// It shows the node roles: Message, Task, Notes, Project
object UnreadView {

  sealed trait Revision {
    def timestamp: EpochMilli
  }
  object Revision {
    case class Delete(timestamp: EpochMilli) extends Revision
    case class Edit(author: Node.User, timestamp: EpochMilli) extends Revision
    case class Create(author: Node.User, timestamp: EpochMilli) extends Revision
  }
  case class UnreadNode(nodeIdx: Int, newRevisions: Array[Revision])

  def apply(state: GlobalState, focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      keyed,
      Styles.growFull,
      overflow.auto,
      padding := "20px",
      h3(
        "What is new?",
        padding := "5px"
      ),

      Rx {
        val graph = state.rawGraph()
        val user = state.user()

        val unreadNodes = calculateNewNodes(graph, user)

        val currentTime = EpochMilli.now
        unreadNodes.sortBy(n => -graph.nodeModified(n.nodeIdx)).take(50).map { n =>
          renderUnreadNode(state, graph, n, parentId = focusState.focusedId, currentTime = currentTime)
        }
      },
    )
  }

  private def calculateNewNodes(graph: Graph, user: AuthUser): Array[UnreadNode] = {
    val unreadNodes = Array.newBuilder[UnreadNode]

    graph.nodes.foreach { node =>
      if(InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task, NodeRole.Note, NodeRole.Project)(node.role)) graph.idToIdxGet(node.id).foreach { nodeIdx =>
        graph.nodeModifier(nodeIdx)
        val readTimes = graph.readEdgeIdx(nodeIdx).flatMap { edgeIdx =>
          val edge = graph.edges(edgeIdx).asInstanceOf[Edge.Read]
          if(edge.userId == user.id) Some(edge.data.timestamp)
          else None
        }
        val lastReadTime = if(readTimes.isEmpty) None else Some(readTimes.max)
        val newRevisionsBuilder = Array.newBuilder[Revision]
        var isFirst = true
        graph.sortedAuthorshipEdgeIdx.foreachElement(nodeIdx) { edgeIdx =>
          val edge = graph.edges(edgeIdx).asInstanceOf[Edge.Author]
          if(lastReadTime.forall(_ < edge.data.timestamp)) {
            val author = graph.nodesById(edge.userId).asInstanceOf[Node.User]
            val revision = if(isFirst) Revision.Create(author, edge.data.timestamp) else Revision.Edit(author, edge.data.timestamp)
            newRevisionsBuilder += revision
          }
          isFirst = false
        }

        val newRevisions = newRevisionsBuilder.result
        if(newRevisions.nonEmpty) {
          unreadNodes += UnreadNode(nodeIdx, newRevisions)
        }
      }
    }

    unreadNodes.result
  }
  private def renderUnreadNode(state: GlobalState, graph: Graph, unreadNode: UnreadNode, parentId: NodeId, currentTime: EpochMilli)(implicit ctx: Ctx.Owner): VDomModifier = {
    import unreadNode._
    graph.nodes(nodeIdx) match {
      case node: Node.Content =>
        val breadCrumbs = {
          BreadCrumbs(state, Some(parentId), Var(Some(parentId)), _ => ()) //TODO: static breadcrumbs without rx? just a simple method in breadcrumbs that does the rendering...
        }

        val deletedTime = graph.parentEdgeIdx(nodeIdx).find { idx =>
          val edge = graph.edges(idx).asInstanceOf[Edge.Child]
          edge.parentId == parentId
        }.flatMap { idx =>
          val edge = graph.edges(idx).asInstanceOf[Edge.Child]
          edge.data.deletedAt
        }

        val newRevisionsWithDelete = deletedTime match {
          case Some(time) => newRevisions :+ Revision.Delete(time)
          case _ => newRevisions
        }

        val changes = newRevisionsWithDelete.map { revision =>
          def authorNode(node: Node.User) = nodeCard(node).prepend(
            cursor.pointer,
            Styles.flex,
            alignItems.center,
            Components.nodeAvatar(node, size = 12).apply(Styles.flexStatic, marginRight := "4px")
          )
          val (doIcon, doDescription, doAuthor) = revision match {
            case revision: Revision.Edit => (freeSolid.faEdit, s"Edit ${node.role}", Some(revision.author))
            case revision: Revision.Create => (freeSolid.faPlus, s"New ${node.role}", Some(revision.author))
            case revision: Revision.Delete => (freeSolid.faTrash, s"Archive ${node.role}", None)
          }

          div(
            Styles.flex,
            alignItems.center,

            doIcon,

            div(
              marginLeft := "5px",
              s"${DateFns.formatDistance(new Date(revision.timestamp), new Date(currentTime))} ago"
            ),

            div(
              marginLeft := "5px",
              color.gray,
              doDescription
            ),


            doAuthor.map { author =>
              authorNode(author)(
                marginLeft := "5px",
              )
            },
          )
        }

        UI.segment(
          breadCrumbs,
          div(
            width := "100%",
            Styles.flex,
            alignItems.flexStart,
            justifyContent.spaceBetween,

            nodeCard(node, maxLength = Some(150)).apply(
              VDomModifier.ifTrue(deletedTime.isDefined)(cls := "node-deleted"),
              Components.sidebarNodeFocusMod(state.rightSidebarNode, node.id),
            ),

            div(
              Styles.flex,
              flexDirection.column,
              alignItems.flexStart,

              changes
            ),

            div(
              freeSolid.faCheck,

              cursor.pointer,

              UI.popup := "Mark as read",

              onClick.stopPropagation.foreach {
                val changes = GraphChanges(
                  addEdges = Set(
                    Edge.Read(node.id, EdgeData.Read(EpochMilli.now), state.user.now.id)
                  )
                )

                state.eventProcessor.changes.onNext(changes)
                ()
              }
            )
          ),
        ).apply(
          width := "100%"
        )

      case _ => VDomModifier.empty
    }
  }
}
