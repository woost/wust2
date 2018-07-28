package wust.webApp

import cats.data.{NonEmptyList, NonEmptySet}
import fastparse.core.Parsed
import cats.syntax._
import fontAwesome._
import monix.reactive.subjects.PublishSubject
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.util._
import wust.util.collection._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.parsers.NodeDataParser
import wust.webApp.views.Elements._
import wust.webApp.views.Rendered._

import scala.collection.breakOut

object SelectedNodes {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Rx {
        val graph = state.graph()
        val sortedNodeIds = state.selectedNodeIds().toList.sortBy(nodeId => graph.nodeModified(nodeId): Long)
        VDomModifier(
          draggableAs(state, DragItem.SelectedNodes(sortedNodeIds)),
          dragTarget(DragItem.SelectedNodesBar),

          sortedNodeIds match {
            case Nil => state.dragEvents.status.map {
              case DragStatus.None =>
                VDomModifier.empty
              case DragStatus.Dragging => VDomModifier(
                cls := "selectednodes",
                position := "absolute",
                bottom := "0px",
                height := "37px",
                width := "100%",
//                textAlign.center,
                "drag here to select"
              )
            }
            case nonEmptyNodeIds => VDomModifier(
              cls := "selectednodes",
              Styles.flex,
//              alignItems.center,
              nodeList(state, nonEmptyNodeIds, state.graph())(ctx)(marginRight.auto, cls := "nodelist"),
              deleteAllButton(state, nonEmptyNodeIds),
              clearSelectionButton(state)
            )
          }
        )
      },
      registerDraggableContainer(state)
    )
  }

  private def nodeList(state:GlobalState, selectedNodeIds:List[NodeId], graph:Graph)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
//      alignItems.center,
      flexWrap.wrap,
      selectedNodeIds.map { nodeId =>
          val node = graph.nodesById(nodeId)
          nodeCard(state, node)
        }
    )
  }

  private def deleteAllButton(state:GlobalState, selectedNodeIds:List[NodeId])(implicit ctx: Ctx.Owner) = {
    div(
      freeRegular.faTrashAlt,
      cls := "actionbutton",
      margin := "5px",

      onClick --> sideEffect{_ =>
        val changes = GraphChanges.delete(selectedNodeIds, state.graph.now, state.page.now)
        state.eventProcessor.changes.onNext(changes)
        state.selectedNodeIds() = Set.empty[NodeId]
      }
    )
  }

  private def clearSelectionButton(state:GlobalState) = {
    div(
      "×",
      cls := "actionbutton",
      margin := "5px",
      fontWeight.bold,
      onClick --> sideEffect {
        state.selectedNodeIds() = Set.empty[NodeId]
      }
    )
  }

  private def nodeCard(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner) = {
    nodeCardCompact(state,node,injected = Seq[VDomModifier](
      Styles.flex,
      alignItems.center,
      span(
        "×",
        cls := "actionbutton",
        onClick.stopPropagation --> sideEffect {
          state.selectedNodeIds.update(_ - node.id)
        }
      )
    ), maxLength = Some(20))(ctx)(marginBottom := "3px")
  }
}
