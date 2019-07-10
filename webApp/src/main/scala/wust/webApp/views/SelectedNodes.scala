package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{Styles, ZIndex}
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp.dragdrop.DragItem
import wust.webApp.state.{GlobalState, NodePermission, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{drag, registerDragContainer}
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._

import scala.collection.breakOut

object SelectedNodes {
  import SharedViewElements.SelectedNodeBase

  def apply[T <: SelectedNodeBase](selected:Var[Set[T]], nodeActions:(List[T], Boolean) => List[VNode] = (_:List[T], _: Boolean) => Nil, singleNodeActions:(T, Boolean) => List[VNode] = (_: T, _: Boolean) => Nil)(implicit ctx: Ctx.Owner): VNode = {

    val selectedNodes: Var[Set[T]] = selected.mapRead { selectedNodes =>
      selectedNodes().filter(data => GlobalState.graph().lookup.contains(data.nodeId))
    }

    div(
      emitterRx(selected).map(_.map(_.nodeId)(breakOut): List[NodeId]) --> GlobalState.selectedNodes,

      Rx {
        val graph = GlobalState.graph()
        val sortedNodeIds = selectedNodes().toList //.sortBy(data => graph.nodeCreated(graph.idToIdx(data.nodeId)): Long)
        val canWriteAll = NodePermission.canWriteAll(GlobalState.user(), graph, sortedNodeIds.map(_.nodeId))
        VDomModifier(
          sortedNodeIds match {
            case Nil => VDomModifier.empty
            case nonEmptyNodeIds => VDomModifier(
              cls := "selectednodes",
              Styles.flex,
              alignItems.center,

              clearSelectionButton(selectedNodes),
              div(nonEmptyNodeIds.size, marginRight := "10px", fontWeight.bold),

              Rx {
                (GlobalState.screenSize() != ScreenSize.Small).ifTrue[VDomModifier](nodeList( nonEmptyNodeIds.map(_.nodeId), selectedNodes, GlobalState.graph()))
              }, // grow, so it can be grabbed

              div(marginLeft.auto),
              (nonEmptyNodeIds.size == 1).ifTrueSeq(singleNodeActions(nonEmptyNodeIds.head, canWriteAll).map(_(cls := "actionbutton"))),
              nodeActions(nonEmptyNodeIds, canWriteAll).map(_(cls := "actionbutton")),
            )
          }
        )
      },
      registerDragContainer,
      keyed,
      zIndex := ZIndex.selected,
      onGlobalEscape(Set.empty[T]) --> selectedNodes,
    )
  }

  private def nodeList[T <: SelectedNodeBase]( selectedNodeIds:List[NodeId], selectedNodes: Var[Set[T]], graph:Graph)(implicit ctx: Ctx.Owner) = {
    div(
      cls := "nodelist",
      drag(payload = DragItem.SelectedNodes(selectedNodeIds)),
      DragComponents.onAfterPayloadWasDragged.foreach{ selectedNodes() = Set.empty[T] },

      Styles.flex,
//      alignItems.center,
      flexWrap.wrap,
      selectedNodeIds.map { nodeId =>
          val node = graph.nodesByIdOrThrow(nodeId)
          selectedNodeCard( selectedNodes, node)
        }
    )
  }

  def deleteAllButton[T <: SelectedNodeBase]( selectedNodesList:List[T], selectedNodes: Var[Set[T]], allSelectedNodesAreDeleted: Rx[Boolean])(implicit ctx: Ctx.Owner): VNode = {
    div(
      div(
        cls := "fa-fw",
        Rx {
          if (allSelectedNodesAreDeleted()) SharedViewElements.undeleteButton
          else SharedViewElements.deleteButton
        }
      ),

      onClick foreach{_ =>
        val changes =
          if (allSelectedNodesAreDeleted.now)
            selectedNodesList.foldLeft(GraphChanges.empty)((c, t) => c merge GraphChanges.undelete(ChildId(t.nodeId), t.directParentIds.map(ParentId(_))))
          else
            selectedNodesList.foldLeft(GraphChanges.empty)((c, t) => c merge GraphChanges.delete(ChildId(t.nodeId), t.directParentIds.map(ParentId(_))))

        GlobalState.submitChanges(changes)
        selectedNodes() = Set.empty[T]
      }
    )
  }

  private def clearSelectionButton[T](selectedNodes: Var[Set[T]]) = {
    closeButton(
      cls := "actionbutton",
      onClick foreach {
        selectedNodes() = Set.empty[T]
      }
    )
  }

  private def selectedNodeCard[T <: SelectedNodeBase]( selectedNodes: Var[Set[T]], node: Node)(implicit ctx: Ctx.Owner) = {
    nodeCard( node,contentInject = Seq[VDomModifier](
      Styles.flex,
      alignItems.center,
      span(
        "×",
        cls := "actionbutton",
        margin := "0",
        onClick.stopPropagation foreach {
          selectedNodes.update(_.filterNot(data => data.nodeId == node.id))
        }
      ),
    ),
      maxLength = Some(20)
    ).apply(
      drag(DragItem.SelectedNode(node.id)),
      cls := "draggable"
    )
  }
}
