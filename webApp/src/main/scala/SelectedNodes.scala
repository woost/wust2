package wust.webApp

import fontAwesome._
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

object SelectedNodes {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
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
              nodeList(state, nonEmptyNodeIds, state.graph()), // grow, so it can be grabbed
              deleteAllButton(state, nonEmptyNodeIds)(ctx)(marginLeft.auto),
              clearSelectionButton(state)
            )
          }
        )
      },
      registerDraggableContainer(state),
      key := "selectednodes",
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
