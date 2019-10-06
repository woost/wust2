package wust.webApp.views

import outwatch.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp.dragdrop.DragItem
import wust.webApp.state.{FocusState, GlobalState}
import wust.webApp.views.DragComponents.registerDragContainer
import wust.webUtil.outwatchHelpers._

import scala.concurrent.duration._

// Topologically sorted items by property relations
object TopologicalView {
  def apply(focusState: FocusState)(implicit ctx: Ctx.Owner): VNode = {

    final case class NodeInfo(node: Node, depth: Int)

    val propertyName = Var("depends on")
    val nodeDepth: Rx[Array[Int]] = Rx {
      val graph = GlobalState.graph()
      val lookup = graph.propertyLookup(propertyName())
      algorithm.longestPathsIdx(lookup)
    }

    val nodeInfos: Rx[Array[NodeInfo]] = Rx {
      val graph = GlobalState.graph()
      val depth = nodeDepth()
      val nodeIdx = graph.idToIdxOrThrow(focusState.focusedId)

      graph.childrenIdx.flatMap(nodeIdx) { nodeIdx =>
        //TODO: TaskOrdering.constructOrderingOf[Node](graph, firstWorkspaceId, inboxTasks.map(graph.nodes), _.id)
        val node = graph.nodes(nodeIdx)
        if (node.role == NodeRole.Task && !graph.isDone(nodeIdx)) { //TODO: only done in this workspace
          Array(NodeInfo(node, depth(nodeIdx)))
        } else Array.empty[NodeInfo]
      }.sortBy(_.depth)
    }

    div(
      Styles.growFull,
      overflow.auto,
      padding := "20px",

      div(
        Styles.flex,
        justifyContent.flexEnd,
        alignItems.center,
        marginBottom := "10px",

        div("Drag items onto each other to connect.", opacity := 0.5, marginRight.auto),
        div("Field:", marginRight := "10px"),
        div(
          cls := "ui input",
          input(
            tpe := "text",
            value <-- propertyName,
            onInput.value.debounce(300 milliseconds) --> propertyName,
            marginLeft.auto,
          )
        )
      ),
      Rx {
        var lastLevel = 0
        nodeInfos().map { nodeInfo =>
          val isNewGroup = lastLevel != nodeInfo.depth
          lastLevel = nodeInfo.depth
          VDomModifier("not implemented")
//          TaskNodeCard.render(
//            nodeInfo.node,
//            parentId = focusState.focusedId,
//            focusState = focusState,
//            inOneLine = true,
//            dragPayload = nodeId => DragItem.TaskConnect(nodeInfo.node.id, propertyName()),
//            dragTarget = nodeId => DragItem.TaskConnect(nodeInfo.node.id, propertyName()),
//          ).apply(
//              marginBottom := "3px",
//              VDomModifier.ifTrue(isNewGroup)(marginTop := "40px"),
//            )
        }
      },
      registerDragContainer,
    )
  }
}
