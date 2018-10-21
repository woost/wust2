package wust.webApp.views

import fontAwesome._
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp.Icons
import wust.webApp.dragdrop.{DragItem, DragStatus}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.state.ScreenSize

object SelectedNodes {
  //TODO T <: SelectedNodeBase, remove getNodeId when ChatOld/ThreadOld
  def apply[T](state: GlobalState, getNodeId: T => NodeId, nodeActions:List[T] => List[VNode] = (_:List[T]) => Nil, singleNodeActions:T => List[VNode] = (_:List[T]) => Nil, selected:Var[Set[T]])(implicit ctx: Ctx.Owner): VNode = {

    val selectedNodes: Var[Set[T]] = selected.mapRead { selectedNodes =>
      selectedNodes().filter(data => state.graph().lookup.contains(getNodeId(data)))
    }

    div(
      style("user-select") := "none",
      Rx {
        val graph = state.graph()
        val sortedNodeIds = selectedNodes().toList.sortBy(data => graph.nodeModified(getNodeId(data)): Long)
        VDomModifier(
          sortedNodeIds match {
            case Nil => VDomModifier.empty
//              state.draggableEvents.status.map {
//              case DragStatus.None =>
//                VDomModifier.empty
//              case DragStatus.Dragging => VDomModifier(
//                cls := "selectednodes",
//                Styles.flex,
//                alignItems.center,
//                justifyContent.spaceAround,
//                div("drag here to select", margin.auto),
//                clearSelectionButton(selectedNodes).apply(visibility.hidden), // to provide the correct height for the bar
//                // height := "37px",
//                width := "100%",
//                textAlign.center,
//                draggableAs(state, DragItem.DisableDrag),
//                dragTarget(DragItem.SelectedNodesBar),
//              )
//            }
            case nonEmptyNodeIds => VDomModifier(
              cls := "selectednodes",
              Styles.flex,
              alignItems.center,
              clearSelectionButton(selectedNodes),
              div(nonEmptyNodeIds.size,cls := "ui large blue label", marginLeft := "10px"),

              Rx {
                (state.screenSize() != ScreenSize.Small).ifTrue[VDomModifier](nodeList(state, nonEmptyNodeIds.map(getNodeId), selectedNodes, getNodeId, state.graph()))
              }, // grow, so it can be grabbed

              div(marginLeft.auto),
              (nonEmptyNodeIds.size == 1).ifTrueSeq(singleNodeActions(nonEmptyNodeIds.head).map(_(cls := "actionbutton"))),
              nodeActions(nonEmptyNodeIds).map(_(cls := "actionbutton")),
            )
          }
        )
      },
      registerDraggableContainer(state),
      keyed,
      onGlobalEscape(Set.empty[T]) --> selectedNodes
    )
  }

  private def nodeList[T](state:GlobalState, selectedNodeIds:List[NodeId], selectedNodes: Var[Set[T]], getNodeId: T => NodeId, graph:Graph)(implicit ctx: Ctx.Owner) = {
    div(
      cls := "nodelist drag-feedback",
      draggableAs(state, DragItem.SelectedNodes(selectedNodeIds)),
      dragTarget(DragItem.SelectedNodesBar),

      Styles.flex,
//      alignItems.center,
      flexWrap.wrap,
      selectedNodeIds.map { nodeId =>
          val node = graph.nodesById(nodeId)
          selectedNodeCard(state, selectedNodes, getNodeId, node)
        }
    )
  }

  def deleteAllButton[T](state:GlobalState, selectedNodesList:List[T], selectedNodes: Var[Set[T]], getNodeId: T => NodeId, getDirectParentIds: T => Iterable[NodeId])(implicit ctx: Ctx.Owner): VNode = {
    val allSelectedNodesAreDeleted = Rx {
      val graph = state.graph()
      selectedNodesList.forall(t => graph.lookup.isDeletedNow(getNodeId(t), getDirectParentIds(t)))
    }
    div(
      div(
        cls := "fa-fw",
        Rx {
          if (allSelectedNodesAreDeleted()) Icons.undelete : VNode
          else Icons.delete : VNode
        }
      ),
      cls := "actionbutton",

      onClick handleWith{_ =>
        val changes =
          if (allSelectedNodesAreDeleted.now)
            selectedNodesList.foldLeft(GraphChanges.empty)((c, t) => c merge GraphChanges.undelete(getNodeId(t), getDirectParentIds(t)))
          else
            selectedNodesList.foldLeft(GraphChanges.empty)((c, t) => c merge GraphChanges.delete(getNodeId(t), getDirectParentIds(t)))

        state.eventProcessor.changes.onNext(changes)
        selectedNodes() = Set.empty[T]
      }
    )
  }

  private def clearSelectionButton[T](selectedNodes: Var[Set[T]]) = {
    closeButton(
      cls := "actionbutton",
      onClick handleWith {
        selectedNodes() = Set.empty[T]
      }
    )
  }

  private def selectedNodeCard[T](state:GlobalState, selectedNodes: Var[Set[T]], getNodeId: T => NodeId, node: Node)(implicit ctx: Ctx.Owner) = {
    nodeCard(node,injected = Seq[VDomModifier](
      Styles.flex,
      alignItems.center,
      span(
        "×",
        cls := "actionbutton",
        margin := "0",
        onClick.stopPropagation handleWith {
          selectedNodes.update(_.filterNot(data => getNodeId(data) == node.id))
        }
      ),
    ),
      maxLength = Some(20)
    )(
      draggableAs(state, DragItem.SelectedNode(node.id)),
      dragTarget(DragItem.SelectedNode(node.id)),
      cls := "draggable drag-feedback"
    )
  }
}
