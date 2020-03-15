package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{Styles, ZIndex}
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp.dragdrop.DragItem
import wust.webApp.state.GlobalState.SelectedNode
import wust.webApp.state.{GlobalState, NodePermission, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{drag, registerDragContainer}
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._

object SelectedNodes {
  def apply(nodeActions: (Vector[SelectedNode], Boolean) => List[VNode] = (_: Vector[SelectedNode], _: Boolean) => Nil, singleNodeActions: (SelectedNode, Boolean) => List[VNode] = (_: SelectedNode, _: Boolean) => Nil)(implicit ctx: Ctx.Owner): VNode = {

    div(
      Rx {
        val graph = GlobalState.graph()
        val sortedNodes = GlobalState.selectedNodes() //.sortBy(data => graph.nodeCreated(graph.idToIdx(data.nodeId)): Long)
        val sortedNodeIds = sortedNodes.map(_.nodeId)
        val canWriteAll = NodePermission.canWriteAll(GlobalState.userId(), graph, sortedNodeIds)
        VDomModifier.ifTrue(sortedNodes.nonEmpty)(VDomModifier(
          cls := "selectednodes",
          Styles.flex,
          alignItems.center,

          clearSelectionButton(),
          div(sortedNodes.size, marginRight := "10px", fontWeight.bold),

          Rx {
            (GlobalState.screenSize() != ScreenSize.Small).ifTrue[VDomModifier](nodeList(sortedNodeIds, GlobalState.graph()))
          }, // grow, so it can be grabbed

          div(marginLeft.auto),
          (sortedNodes.size == 1).ifTrueSeq(singleNodeActions(sortedNodes.head, canWriteAll).map(_(cls := "actionbutton"))),
          nodeActions(sortedNodes, canWriteAll).map(_(cls := "actionbutton")),
        ))
      },
      registerDragContainer,
      keyed,
      zIndex := ZIndex.selected,
      onGlobalEscape.foreach { GlobalState.clearSelectedNodes() },
    )
  }

  private def nodeList(selectedNodeIds: Seq[NodeId], graph: Graph)(implicit ctx: Ctx.Owner) = {
    div(
      cls := "nodelist",
      drag(payload = DragItem.SelectedNodes(selectedNodeIds)),
      DragComponents.onAfterPayloadWasDragged.foreach{ GlobalState.clearSelectedNodes() },

      Styles.flex,
      //      alignItems.center,
      flexWrap.wrap,
      selectedNodeIds.map { nodeId =>
        val node = graph.nodesByIdOrThrow(nodeId)
        selectedNodeCard(node)
      }
    )
  }

  def deleteAllButton(selectedNodesList: Seq[SelectedNode], allSelectedNodesAreDeleted: Rx[Boolean])(implicit ctx: Ctx.Owner): VNode = {
    div(
      div(
        cls := "fa-fw",
        Rx {
          if (allSelectedNodesAreDeleted()) SharedViewElements.undeleteButton
          else SharedViewElements.deleteButton
        }
      ),

      onClickDefault foreach { _ =>
        val changes =
          if (allSelectedNodesAreDeleted.now)
            selectedNodesList.foldLeft(GraphChanges.empty)((c, t) => c merge GraphChanges.undelete(ChildId(t.nodeId), t.directParentIds))
          else
            selectedNodesList.foldLeft(GraphChanges.empty)((c, t) => c merge GraphChanges.delete(ChildId(t.nodeId), t.directParentIds))

        GlobalState.submitChanges(changes)
        GlobalState.clearSelectedNodes()
      }
    )
  }

  private def clearSelectionButton() = {
    closeButton(
      cls := "actionbutton",
      onClickDefault foreach {
        GlobalState.clearSelectedNodes()
      }
    )
  }

  private def selectedNodeCard(node: Node)(implicit ctx: Ctx.Owner) = {
    nodeCard(node, contentInject = Seq[VDomModifier](
      Styles.flex,
      alignItems.center,
      span(
        "×",
        cls := "actionbutton",
        margin := "0",
        onClickDefault.stopPropagation foreach {
          GlobalState.removeSelectedNode(node.id)
        }
      ),
    ),
      ).apply(
        drag(DragItem.SelectedNode(node.id)),
        cls := "draggable"
      )
  }
}
