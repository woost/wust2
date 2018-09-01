package wust.webApp.views

import fontAwesome._
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp.dragdrop.{DragItem, DragStatus}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

object SelectedNodes {
  def apply(state: GlobalState, nodeActions:List[NodeId] => List[VNode] = _ => Nil, singleNodeActions:NodeId => List[VNode] = _ => Nil)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Rx {
        val graph = state.graph()
        val sortedNodeIds = state.selectedNodeIds().toList.sortBy(nodeId => graph.nodeModified(nodeId): Long)
        VDomModifier(
          sortedNodeIds match {
            case Nil => state.draggableEvents.status.map {
              case DragStatus.None =>
                VDomModifier.empty
              case DragStatus.Dragging => VDomModifier(
                "drag here to select",
                cls := "selectednodes",
                position := "absolute",
                bottom := "0px",
                height := "37px",
                width := "100%",
                paddingTop := "5px",
                textAlign.center,
                draggableAs(state, DragItem.DisableDrag),
                dragTarget(DragItem.SelectedNodesBar),
              )
            }:VDomModifier
            case nonEmptyNodeIds => VDomModifier(
              cls := "selectednodes",
              Styles.flex,
              alignItems.center,
//              (nonEmptyNodeIds.size >= 2).ifTrue[VDomModifier](nodeList(state, nonEmptyNodeIds, state.graph())), // grow, so it can be grabbed
              div(marginLeft.auto),
              (nonEmptyNodeIds.size == 1).ifTrueSeq(singleNodeActions(nonEmptyNodeIds.head).map(_(cls := "actionbutton"))),
              nodeActions(nonEmptyNodeIds).map(_(cls := "actionbutton")),
              clearSelectionButton(state)
            )
          }
        )
      },
      registerDraggableContainer(state),
      keyed,
      onGlobalEscape(Set.empty[NodeId]) --> state.selectedNodeIds
    )
  }

  private def nodeList(state:GlobalState, selectedNodeIds:List[NodeId], graph:Graph)(implicit ctx: Ctx.Owner) = {
    div(
      cls := "nodelist drag-feedback",
      draggableAs(state, DragItem.SelectedNodes(selectedNodeIds)),
      dragTarget(DragItem.SelectedNodesBar),

      Styles.flex,
//      alignItems.center,
      flexWrap.wrap,
      selectedNodeIds.map { nodeId =>
          val node = graph.nodesById(nodeId)
          selectedNodeCard(state, node)
        }
    )
  }

  def deleteAllButton(state:GlobalState, selectedNodeIds:List[NodeId])(implicit ctx: Ctx.Owner): VNode = {
    div(
      div(cls := "fa-fw", freeRegular.faTrashAlt),
      cls := "actionbutton",

      onClick --> sideEffect{_ =>
        val changes = GraphChanges.delete(selectedNodeIds, state.graph.now)
        state.eventProcessor.changes.onNext(changes)
        state.selectedNodeIds() = Set.empty[NodeId]
      }
    )
  }

  private def clearSelectionButton(state:GlobalState) = {
    closeButton(
      cls := "actionbutton",
      onClick --> sideEffect {
        state.selectedNodeIds() = Set.empty[NodeId]
      }
    )
  }

  private def selectedNodeCard(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner) = {
    nodeCard(state,node,injected = Seq[VDomModifier](
      Styles.flex,
      alignItems.center,
      span(
        "×",
        cls := "actionbutton",
        onClick.stopPropagation --> sideEffect {
          state.selectedNodeIds.update(_ - node.id)
        }
      ),
    ),
      maxLength = Some(20)
    )(ctx)(
      draggableAs(state, DragItem.SelectedNode(node.id)),
      dragTarget(DragItem.SelectedNode(node.id)),
      cls := "draggable drag-feedback"
    )
  }
}
